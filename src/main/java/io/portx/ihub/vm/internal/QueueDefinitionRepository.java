/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import static java.lang.String.format;
import static org.mule.runtime.core.api.util.concurrent.FunctionalReadWriteLock.readWriteLock;

import io.portx.ihub.vm.api.QueueDefinition;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.core.api.config.QueueProfile;
import org.mule.runtime.core.api.util.concurrent.FunctionalReadWriteLock;
import org.mule.runtime.core.api.util.queue.QueueManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of all the queues created by each config
 *
 * @since 1.0
 */
public class QueueDefinitionRepository {

  private final QueueManager queueManager;
  private final FunctionalReadWriteLock lock = readWriteLock();
  private Map<VMConnector, Map<String, QueueDefinition>> queues = new ConcurrentHashMap<>();

  public QueueDefinitionRepository(QueueManager queueManager) {
    this.queueManager = queueManager;
  }

  public void createQueues(VMConnector config, Collection<QueueDefinition> definitions) {
    Map<String, QueueDefinition> configQueues = queues.computeIfAbsent(config, key -> new ConcurrentHashMap());
    lock.withWriteLock(() -> {
      Map<String, QueueDefinition> createdQueues = new HashMap<>();
      for (QueueDefinition definition : definitions) {
        final String queueName = definition.getQueueName();

        findByName(queueName).ifPresent(previous -> {
          throw new IllegalArgumentException(format("<vm:config> '%s' is trying to define queue '%s' which is already defined "
              + "by config '%s'", config.getName(), definition.getQueueName(), previous.getFirst().getName()));
        });

        queueManager.getQueueConfiguration(definition.getQueueName()).ifPresent(queueConfig -> {
          if (queueConfig.isPersistent() != definition.getQueueType().isPersistent()) {
            throw new IllegalArgumentException(format(
                                                      "<vm:config> '%s' is trying to define %s queue '%s' which already exists is already configured in the Mule "
                                                          + "runtime as %s.",
                                                      config.getName(), definition.getQueueType().name(),
                                                      definition.getQueueName(),
                                                      queueConfig.isPersistent() ? "persistent" : "transient"));
          }
        });

        QueueProfile profile = new QueueProfile(definition.getMaxOutstandingMessages(), definition.getQueueType().isPersistent());
        profile.configureQueue(queueName, queueManager);
        createdQueues.put(queueName, definition);
      }

      configQueues.putAll(createdQueues);
    });
  }

  public void unregisterQueues(VMConnector config) {
    lock.withWriteLock(() -> queues.remove(config));
  }

  public Optional<Pair<VMConnector, QueueDefinition>> findByName(String name) {
    return lock.withReadLock(r -> queues.entrySet().stream()
        .filter(entry -> entry.getValue().containsKey(name))
        .map(entry -> new Pair<>(entry.getKey(), entry.getValue().get(name)))
        .findFirst());
  }

}
