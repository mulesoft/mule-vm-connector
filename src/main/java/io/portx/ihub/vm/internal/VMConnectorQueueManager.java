/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;
import static org.slf4j.LoggerFactory.getLogger;

import io.portx.ihub.vm.api.QueueDefinition;
import io.portx.ihub.vm.internal.connection.VMConnection;
import io.portx.ihub.vm.internal.listener.VMListener;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.core.api.config.QueueProfile;
import org.mule.runtime.core.api.util.UUID;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.core.api.util.queue.QueueConfiguration;
import org.mule.runtime.core.api.util.queue.QueueManager;

import java.util.Collection;
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
public class VMConnectorQueueManager implements Initialisable, Stoppable {

  private static final Logger LOGGER = getLogger(VMConnectorQueueManager.class);

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  private QueueManager queueManager;

  private QueueDefinitionRepository definitionRepository;
  private Map<String, ComponentLocation> listenerQueues = new ConcurrentHashMap<>();
  private Map<String, Queue> replyToQueues = new ConcurrentHashMap<>();

  @Override
  public void initialise() throws InitialisationException {
    definitionRepository = new QueueDefinitionRepository(queueManager);
  }

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
    listenerQueues.clear();
  }

  /**
   * Creates and configures all the queue {@code definitions} and tracks the given {@code config} as their owner
   * @param config a {@link VMConnector}
   * @param definitions                  the queue definitions
   * @throws InitialisationException
   */
  public void createQueues(VMConnector config, Collection<QueueDefinition> definitions) throws InitialisationException {
    definitionRepository.createQueues(config, definitions);
  }

  /**
   * Releases the tracking handle for the queues defined in the given {@code config}. The queues themselves will not be destroyed
   * 
   * @param config the config that owns configs
   */
  public void unregisterQueues(VMConnector config) {
    definitionRepository.unregisterQueues(config);
  }

  /**
   * Validates that a queue of the given {@code queueName} was created through {@link #createQueues(VMConnector, Collection)}
   * and that it was actually defined on the give {@code config}
   *
   * @param queueName the queue name
   * @param config the owner
   */
  public void validateQueue(String queueName, VMConnector config) {
    VMConnector owner = definitionRepository.findByName(queueName)
        .map(Pair::getFirst)
        .orElseThrow(() -> new IllegalArgumentException(format("queue '%s' is not defined", queueName)));

    if (!owner.getName().equals(config.getName())) {
      throw new IllegalArgumentException(format("queue '%s' cannot be accessed from component with config-ref '%s' because "
          + "it was defined on config '%s",
                                                queueName, config.getName(), owner.getName()));
    }
  }

  /**
   * Creates a {@link Queue} for a {@link VMListener}, making sure that no other {@link VMListener} has already created that
   * queue
   *
  /**
   * Tracks that the queue of the given {@code queueName} has a listener on the given {@code location}, backed by the given
   * {@code config}.
   *
   * It validates that the queue was created through {@link #createQueues(VMConnector, Collection)} and that the given
   * {@code config} is actually the queue's owner
   *
   * @param config the owner config
   * @param queueName the name of the queue
   * @param location the listener's location
   */
  public void registerListenerQueue(VMConnector config, String queueName, ComponentLocation location) {
    Pair<VMConnector, QueueDefinition> definitionPair = definitionRepository.findByName(queueName)
        .orElseThrow(() -> new IllegalArgumentException(format("Flow '%s' declares a <vm:listener> listening to queue '%s', but "
            + "such queue is not defined",
                                                               location.getRootContainerName(), queueName)));

    if (!definitionPair.getFirst().getName().equals(config.getName())) {
      throw new IllegalArgumentException(format("Flow '%s' has a <vm:listener> with config-ref '%s', listening to queue '%s', "
          + "but that queue is defined on config '%s'. Listeners can only access queues "
          + "defined in their corresponding config",
                                                location.getRootContainerName(),
                                                config.getName(),
                                                queueName,
                                                definitionPair.getFirst().getName()));
    }

    ComponentLocation previous = listenerQueues.get(queueName);
    if (previous != null) {
      throw new IllegalArgumentException(format("Flow '%s' has a <vm:listener> listening to queue '%s', but flow"
          + "'%s' is trying to declare another listener to the same queue. Only one listener is allowed per queue.",
                                                previous.getRootContainerName(),
                                                queueName,
                                                location.getRootContainerName()));
    }

    listenerQueues.put(queueName, location);
  }

  public void unregisterListenerQueue(String queueName) {
    listenerQueues.remove(queueName);
  }

  /**
   * Returns the {@link QueueConfiguration} for the queue of the given {@code queueName}.
   *
   * @param queueName the name of the queue
   * @return a {@link QueueConfiguration}
   * @throws IllegalArgumentException if the queue doesn't exists
   */
  public QueueConfiguration getQueueConfiguration(String queueName) {
    return queueManager.getQueueConfiguration(queueName)
        .orElseThrow(() -> new IllegalArgumentException(format("There's no vm:listener associated to queue '%s'", queueName)));
  }

  /**
   * Creates a temporal replyToQueue for the given {@code originQueue}. The temporal's queue name will
   * be a combination of the {@code originQueue} name and an UUID.
   * <p>
   * When the response is obtained or times-out, the given queue should be disposed of by calling
   * {@link #disposeReplyToQueue(Queue)}. All replyTo queues will be disposed of upon {@link #stop()}
   *
   * @param originQueue the queue which response is awaited
   * @param connection  the current connection
   * @return the temporal {@link Queue}
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
   * Validates that no listener was registered for the {@code queueName} through the
   * {@link #registerListenerQueue(VMConnector, String, ComponentLocation)} method
   *
   * @param queueName     the name of the queue to validate
   * @param operationName the name of the component which is asking for the queue
   * @param location      the location of the component asking for the queue
   */
  public void validateNoListenerOnQueue(String queueName, String operationName, ComponentLocation location) {
    ComponentLocation listenerLocation = listenerQueues.get(queueName);
    if (listenerLocation != null) {
      throw new IllegalArgumentException(format("Operation '<vm:%s>' %sis trying to consume from queue '%s', but "
          + "Flow '%s' defines a <vm:listener> on that queue. It's not allowed to consume from a queue on which "
          + "a listener already exists", operationName, getConsumerLocation(location), queueName,
                                                listenerLocation.getRootContainerName()));
    }
  }

  public Queue getQueueWithoutTx(String queueName) {
    return queueManager.getQueueSession().getQueue(queueName);
  }

  private String getConsumerLocation(ComponentLocation location) {
    return location != null ? "in Flow '" + location.getRootContainerName() + "' " : "";
  }
}
