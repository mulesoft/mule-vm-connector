/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import static java.lang.String.format;
import static org.mule.extensions.vm.api.VMError.QUEUE_NOT_FOUND;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.extensions.vm.internal.connection.VMConnection;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.core.api.config.QueueProfile;
import org.mule.runtime.core.api.util.UUID;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueConfiguration;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.extension.api.exception.ModuleException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;

/**
 * Keeps track of all the {@link Queue queues} used by the connector.
 * <p>
 * Guarantees that all queues are properly handled all across component instances and configs.
 * It's also the centralized hub which guarantees that overlapping queues definitions are not allowed
 * and that all operations reference queues for which a listener exists
 *
 * @since 1.0
 */
public class VMConnectorQueueManager implements Stoppable {

  private static final Logger LOGGER = getLogger(VMConnectorQueueManager.class);

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  private QueueManager queueManager;

  private Map<String, String> queues2Locations = new ConcurrentHashMap<>();
  private Map<String, Queue> replyToQueues = new ConcurrentHashMap<>();

  /**
   * Disposes all the temporal replyTo queues
   * {@inheritDoc}
   */
  @Override
  public void stop() throws MuleException {
    replyToQueues.values().forEach(queue -> {
      try {
        queue.dispose();
      } catch (Exception e) {
        LOGGER.warn(format("Could not dispose temporal reply queue '%s'", queue.getName()), e);
      }
    });

    replyToQueues.clear();
    queues2Locations.clear();
  }

  /**
   * Creates a queue according to the given {@code descriptor} and tracks the location from which it was
   * defined
   *
   * @param queueDescriptor the queue's configuration
   * @param location        the location of the defining component
   * @throws InitialisationException
   */
  public void createQueue(QueueListenerDescriptor queueDescriptor, String location) throws InitialisationException {
    String previous = queues2Locations.put(queueDescriptor.getQueueName(), location);
    if (previous != null) {
      throw new IllegalArgumentException(format("Flow '%s' has a vm:listener which declares VM queue '%s', but flow"
          + "'%s' is trying to declare another queue with the same name.",
                                                previous,
                                                queueDescriptor.getQueueName(),
                                                location));
    }

    QueueProfile profile =
        new QueueProfile(queueDescriptor.getMaxOutstandingMessages(), queueDescriptor.getQueueType().isPersistent());
    profile.configureQueue(queueDescriptor.getQueueName(), queueManager);
  }

  /**
   * Returns the {@link QueueConfiguration} for the queue of the given {@code queueName}. If a configuration is not
   * found, a {@code VM:QUEUE_NOT_FOUND} error is thrown. However, that should only happen if a matching call to
   * {@link #createQueue(QueueListenerDescriptor, String)} hasn't yet happened.
   *
   * @param queueName the name of the queue
   * @return a {@link QueueConfiguration}
   * @throws ModuleException if no configuration found for that queue.
   */
  public QueueConfiguration getQueueConfiguration(String queueName) {
    return queueManager.getQueueConfiguration(queueName)
        .orElseThrow(() -> new ModuleException(format("There's no vm:listener associated to queue '%s'", queueName),
                                               QUEUE_NOT_FOUND));
  }

  /**
   * Creates a temporal replyToQueue for the givne {@code originQueue}. The temporal's queue name will
   * be a combination of the {@code originQueue} name and an UUID.
   * <p>
   * When the response is obtained or timesout, the given queue should be disposed of by calling
   * {@link #disposeReplyToQueue(Queue)}. All replyTo queues will be disposed of upon {@link #stop()}
   *
   * @param originQueue the queue which response is awaited
   * @param connection  the current connection
   * @return the tmeporal {@link Queue}
   */
  public Queue createReplyToQueue(Queue originQueue, VMConnection connection) {
    QueueConfiguration conf = getQueueConfiguration(originQueue.getName());

    String tempQueueName = originQueue.getName() + "-temp-replyTo-" + UUID.getUUID();
    QueueProfile tempProfile = new QueueProfile(1, conf.isPersistent());

    try {
      tempProfile.configureQueue(tempQueueName, queueManager);
    } catch (InitialisationException e) {
      throw new MuleRuntimeException(createStaticMessage(format(
                                                                "Could not create temporal reply-to queue for the '%s' queue"),
                                                         originQueue.getName()),
                                     e);
    }

    Queue queue = connection.getQueue(tempQueueName);
    replyToQueues.put(tempQueueName, queue);

    return queue;
  }

  /**
   * Disposes the given queue
   *
   * @param replyToQueue a {@link Queue} previously obtained through {@link #createReplyToQueue(Queue, VMConnection)}
   */
  public void disposeReplyToQueue(Queue replyToQueue) {
    try {
      replyToQueue.dispose();
    } catch (Exception e) {
      LOGGER.warn("Failed to dispose temporal replyTo queue " + replyToQueue.getName(), e);
    } finally {
      replyToQueues.remove(replyToQueue.getName());
    }
  }

  /**
   * Validates that {@code queueName} refers to a queue previously created through
   * {@link #createQueue(QueueListenerDescriptor, String)}. If not, a {@code VM:QUEUE_NOT_FOUND} exception is thrown.
   *
   * @param queueName     the name of the queue to validate
   * @param operationName the name of the component which is asking for the queue
   * @param location      the location of the component asking for the queue
   */
  public void validateQueue(String queueName, String operationName, ComponentLocation location) {
    if (!queues2Locations.containsKey(queueName)) {
      throw new ModuleException(format("Operation 'vm:%s' in Flow '%s' is trying to publish to a queue of name '%s', but "
          + "no vm:listener has declared that queue. Operations can only reference queues for which"
          + "a listener exists", operationName, location.getRootContainerName(), queueName),
                                QUEUE_NOT_FOUND);
    }
  }
}
