/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal.operations;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.extensions.vm.api.VMError.EMPTY_QUEUE;
import static org.mule.extensions.vm.api.VMError.QUEUE_TIMEOUT;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.extensions.vm.api.VMMessageAttributes;
import org.mule.extensions.vm.internal.QueueDescriptor;
import org.mule.extensions.vm.internal.ReplyToCommand;
import org.mule.extensions.vm.internal.VMConnectorQueueManager;
import org.mule.extensions.vm.internal.connection.VMConnection;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.core.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.Serializable;
import java.util.Optional;

import javax.inject.Inject;

public class VMOperations implements Startable, Stoppable {

  @Inject
  private VMConnectorQueueManager queueManager;

  @Inject
  private SchedulerService schedulerService;

  private Scheduler scheduler;

  @Override
  public void start() throws MuleException {
    scheduler = schedulerService.ioScheduler();
  }

  @Override
  public void stop() throws MuleException {
    if (scheduler != null) {
      scheduler.stop();
      scheduler = null;
    }
  }

  /**
   * Publishes the given {@code content} into the queue of the given {@code queueName}.
   * <p>
   * The queue on which the content is published has to be one for which a {@code vm:listener} exists. Otherwise, a
   * {@code VM:QUEUE_NOT_FOUND} error is thrown.
   *
   * @param content         the content to be published
   * @param queueDescriptor the queue configuration
   * @param connection      the acting connection
   * @param location        this processor's location
   */
  @Throws(PublishErrorTypeProvider.class)
  public void publish(@Content TypedValue<Serializable> content,
                      @ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                      @Connection VMConnection connection,
                      ComponentLocation location) {

    Queue queue = getQueue(queueDescriptor, connection, location, "publish");
    doPublish(content, queueDescriptor, queue);
  }

  /**
   * Pull one message from a queue. If a message is not immediately available, it will wait up to the configured
   * {@code queueTimeout}, after which a {@code VM:QUEUE_TIMEOUT} error will be thrown.
   * <p>
   * The queue on which the content is published has to be one for which a {@code vm:listener} exists. Otherwise, a
   * {@code VM:QUEUE_NOT_FOUND} error is thrown.
   *
   * @param queueDescriptor the queue configuration
   * @param connection      the acting connection
   * @param location        this processor's location
   * @return The next element on the consumed queue
   */
  @Throws(ConsumeErrorTypeProvider.class)
  public Result<Serializable, VMMessageAttributes> consume(@ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                                                           @Connection VMConnection connection,
                                                           ComponentLocation location) {

    Queue queue = getQueue(queueDescriptor, connection, location, "consume");
    return doConsume(queue, queueDescriptor)
        .map(value -> asConsumeResponse(value, queueDescriptor))
        .orElseThrow(() -> new ModuleException(format(
                                                      "Tried to consume messages from VM queue '%s' but it was empty after timeout of %d %s",
                                                      queueDescriptor.getQueueName(), queueDescriptor.getTimeout(),
                                                      queueDescriptor.getTimeoutUnit()),
                                               EMPTY_QUEUE));
  }

  /**
   * Publishes the given {@code content} into a queue, and then awaits up to the {@code queueTimeout} for a response
   * to be supplied on a temporal reply-To queue that this operation automatically creates.
   * <p>
   * The temporal reply queue is automatically disposed after a response is received or the timeout expires.
   * <p>
   * The queue on which the content is published has to be one for which a {@code vm:listener} exists. Otherwise, a
   * {@code VM:QUEUE_NOT_FOUND} error is thrown.
   *
   * @param content         the content to be published
   * @param queueDescriptor the queue configuration
   * @param connection      the acting connection
   * @param location        this processor's location
   * @return The response generated from the invoked listener's flow
   */
  @Throws(PublishConsumeErrorTypeProvider.class)
  public Result<Serializable, VMMessageAttributes> publishConsume(@Content TypedValue<Serializable> content,
                                                                  @ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                                                                  @Connection VMConnection connection,
                                                                  ComponentLocation location) {

    final Queue queue = getQueue(queueDescriptor, connection, location, "publishConsume");
    final Queue replyToQueue = queueManager.createReplyToQueue(queue, connection);

    try {
      doPublish(new ReplyToCommand(content, replyToQueue.getName()), queueDescriptor, queue);
      return doConsume(replyToQueue, queueDescriptor)
          .map(value -> asConsumeResponse(value, queueDescriptor))
          .orElseThrow(() -> new ModuleException(format(
                                                        "Published messages to queue '%s' but got no response after timeout of %d %s",
                                                        queueDescriptor.getQueueName(), queueDescriptor.getTimeout(),
                                                        queueDescriptor.getTimeoutUnit()),
                                                 QUEUE_TIMEOUT));

    } catch (ModuleException e) {
      throw e;
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage(format(
                                                                "Found error trying to perform publish-consume to VM queue '%s'",
                                                                queueDescriptor.getQueueName())),
                                     e);
    } finally {
      queueManager.disposeReplyToQueue(replyToQueue);
    }
  }

  private Result<Serializable, VMMessageAttributes> asConsumeResponse(Serializable value, QueueDescriptor queueDescriptor) {

    Result.Builder<Serializable, VMMessageAttributes> resultBuilder = Result.builder();
    resultBuilder.attributes(new VMMessageAttributes(queueDescriptor.getQueueName()));

    if (value instanceof TypedValue) {
      TypedValue<Serializable> typedValue = (TypedValue) value;
      resultBuilder.output(typedValue.getValue())
          .mediaType(typedValue.getDataType().getMediaType());
    } else {
      resultBuilder.output(value);
    }

    return resultBuilder.build();
  }

  private void doPublish(Serializable content, QueueDescriptor queueDescriptor, Queue queue) {
    try {
      if (!queue.offer(content, queueDescriptor.getQueueTimeoutInMillis())) {
        throw new ModuleException("Timeout publishing message to VM queue " + queueDescriptor.getQueueName(), QUEUE_TIMEOUT);
      }
    } catch (InterruptedException e) {
      throw new ModuleException(QUEUE_TIMEOUT, e);
    }
  }

  private Optional<Serializable> doConsume(Queue queue, QueueDescriptor queueDescriptor) {
    try {
      return ofNullable(queue.poll(queueDescriptor.getQueueTimeoutInMillis()));
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage(format(
                                                                "Found error trying to consume messages from VM queue '%s'",
                                                                queueDescriptor.getQueueName())),
                                     e);
    }
  }

  private Queue getQueue(QueueDescriptor queueDescriptor, VMConnection connection, ComponentLocation location,
                         String operationName) {
    queueManager.validateQueue(queueDescriptor.getQueueName(), operationName, location);
    return connection.getQueue(queueDescriptor.getQueueName());
  }

}
