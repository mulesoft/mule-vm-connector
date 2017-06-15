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

public class VMConnectorQueueManager implements Stoppable {

  private static final Logger LOGGER = getLogger(VMConnectorQueueManager.class);

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  private QueueManager queueManager;

  private Map<String, String> queues2Locations = new ConcurrentHashMap<>();
  private Map<String, Queue> replyToQueues = new ConcurrentHashMap<>();

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

  public QueueConfiguration getQueueConfiguration(String queueName) {
    return queueManager.getQueueConfiguration(queueName)
        .orElseThrow(() -> new ModuleException(format("There's not vm:listener associated to queue '%s'", queueName),
                                               QUEUE_NOT_FOUND));
  }

  public Queue createReplyToQueue(Queue originQueue, VMConnection connection) {
    QueueConfiguration conf = getQueueConfiguration(originQueue.getName());

    String tempQueueName = originQueue.getName() + "-temp-replyTo-" + UUID.getUUID();
    QueueProfile tempProfile = new QueueProfile(1, conf.isPersistent());

    try {
      tempProfile.configureQueue(tempQueueName, queueManager);
    } catch (InitialisationException e) {
      throw new MuleRuntimeException(createStaticMessage("Could not create temporal reply-to queue"), e);
    }

    Queue queue = connection.getQueue(tempQueueName);
    replyToQueues.put(tempQueueName, queue);

    return queue;
  }

  public void disposeReplyToQueue(Queue replyToQueue) {
    try {
      replyToQueue.dispose();
    } catch (Exception e) {
      LOGGER.warn("Failed to dispose temporal replyTo queue " + replyToQueue.getName(), e);
    } finally {
      replyToQueues.remove(replyToQueue.getName());
    }
  }

  public void validateQueue(String queueName, String operationName, ComponentLocation location) {
    if (!queues2Locations.containsKey(queueName)) {
      throw new ModuleException(format("Operation 'vm:%s' in Flow '%s' is trying to publish to a queue of name '%s', but "
          + "no vm:listener has declared that queue. Operations can only reference queues for which"
          + "a listener exists", operationName, location.getRootContainerName(), queueName), QUEUE_NOT_FOUND);
    }
  }
}
