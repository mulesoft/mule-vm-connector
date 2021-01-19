/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal.operations;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import io.portx.ihub.vm.api.VMError;
import io.portx.ihub.vm.api.VMMessageAttributes;
import io.portx.ihub.vm.internal.connection.VMConnection;
import io.portx.ihub.vm.internal.QueueDescriptor;
import io.portx.ihub.vm.internal.ReplyToCommand;
import io.portx.ihub.vm.internal.VMConnector;
import io.portx.ihub.vm.internal.VMConnectorQueueManager;
import io.portx.ihub.vm.internal.VMErrorResponse;
import io.portx.ihub.vm.internal.VMMessage;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.exception.TypedException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.ConfigOverride;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.parameter.CorrelationInfo;
import org.mule.runtime.extension.api.runtime.parameter.OutboundCorrelationStrategy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

/**
 * Basic connector operations
 *
 * @since 1.0
 */
// TODO: MULE-13111
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
   *
   * @param content           the content to be published
   * @param properties        optional map of properties
   * @param queueDescriptor   the queue configuration
   * @param sendCorrelationId options on whether to include an outbound correlation id or not
   * @param correlationId     allows to set a custom correlation id
   * @param config            the connector's config
   * @param connection        the acting connection
   * @param correlationInfo   the current message's correlation info
   */
  @Throws(PublishErrorTypeProvider.class)
  public void publish(@Content(primary = true) TypedValue<Serializable> content,
                      @Content @org.mule.runtime.extension.api.annotation.param.Optional Map<String, TypedValue<Serializable>> properties,
                      @ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                      @ConfigOverride OutboundCorrelationStrategy sendCorrelationId,
                      @org.mule.runtime.extension.api.annotation.param.Optional String correlationId,
                      @Config VMConnector config,
                      @Connection VMConnection connection,
                      CorrelationInfo correlationInfo) {

    Queue queue = getQueue(queueDescriptor, config, connection);
    VMMessage message =
        new VMMessage(content, properties,
                      sendCorrelationId.getOutboundCorrelationId(correlationInfo, correlationId).orElse(null));

    doPublish(message, queueDescriptor, queue);
  }

  /**
   * Pull one message from a queue. If a message is not immediately available, it will wait up to the configured
   * {@code queueTimeout}, after which a {@code VM:QUEUE_TIMEOUT} error will be thrown.
   * <p>
   * The queue on which the content is published has to be one for which a {@code <vm:listener>} <b>doesn't </b> exists.
   * Consuming from queues on which a {@code <vm:listener>} exists is not allowed.
   *
   * @param queueDescriptor the queue configuration
   * @param connection      the acting connection
   * @param location        this processor's location
   * @return The next element on the consumed queue
   */
  @Throws(ConsumeErrorTypeProvider.class)
  public Result<Serializable, VMMessageAttributes> consume(@ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                                                           @Config VMConnector config,
                                                           @Connection VMConnection connection,
                                                           ComponentLocation location) {

    queueManager.validateNoListenerOnQueue(queueDescriptor.getQueueName(), "consume", location);
    Queue queue = getQueue(queueDescriptor, config, connection);
    return doConsume(queue, queueDescriptor)
        .map(value -> asConsumeResponse(value, queueDescriptor))
        .orElseThrow(() -> new ModuleException(format(
                                                      "Tried to consume messages from VM queue '%s' but it was empty after timeout of %d %s",
                                                      queueDescriptor.getQueueName(), queueDescriptor.getTimeout(),
                                                      queueDescriptor.getTimeoutUnit()),
                                               VMError.EMPTY_QUEUE));
  }

  /**
   * Publishes the given {@code content} into a queue, and then awaits up to the {@code queueTimeout} for a response
   * to be supplied on a temporal reply-To queue that this operation automatically creates.
   * <p>
   * The temporal reply queue is automatically disposed after a response is received or the timeout expires.
   * <p>
   * The queue on which the content is published has to be one for which a {@code <vm:listener>} <b>doesn't </b> exists.
   * Consuming from queues on which a {@code <vm:listener>} exists is not allowed.
   * <p>
   * If the flow that receives and processed the message fails, then that error is propagated and re-thrown by
   * this operation. Notice that such error type will not be predictable by this operation and could be anything. You
   * need to be mindful of the listening flow when writing your error handlers.
   *
   * @param content           the content to be published
   * @param queueDescriptor   the queue configuration
   * @param sendCorrelationId options on whether to include an outbound correlation id or not
   * @param correlationId     allows to set a custom correlation id
   * @param config            the connector's config
   * @param connection        the acting connection
   * @param correlationInfo   the current message's correlation info
   * @return The response generated from the invoked listener's flow
   */
  @Throws(PublishConsumeErrorTypeProvider.class)
  public Result<Serializable, VMMessageAttributes> publishConsume(@Content(primary = true) TypedValue<Serializable> content,
                                                                  @Content @org.mule.runtime.extension.api.annotation.param.Optional Map<String, TypedValue<Serializable>> properties,
                                                                  @ParameterGroup(name = "queue") QueueDescriptor queueDescriptor,
                                                                  @ConfigOverride OutboundCorrelationStrategy sendCorrelationId,
                                                                  @org.mule.runtime.extension.api.annotation.param.Optional String correlationId,
                                                                  @Config VMConnector config,
                                                                  @Connection VMConnection connection,
                                                                  CorrelationInfo correlationInfo) {

    final Queue queue = getQueue(queueDescriptor, config, connection, connection.isInTransaction());
    final Queue replyToQueue = queueManager.createReplyToQueue(queue, connection);

    VMMessage message = new ReplyToCommand(content, properties, replyToQueue.getName(),
                                           sendCorrelationId.getOutboundCorrelationId(correlationInfo, correlationId)
                                               .orElse(null));
    try {
      doPublish(message, queueDescriptor, queue);
      return doConsume(replyToQueue, queueDescriptor)
          .map(value -> asConsumeResponse(value, queueDescriptor))
          .orElseThrow(() -> new ModuleException(format(
                                                        "Published messages to queue '%s' but got no response after timeout of %d %s",
                                                        queueDescriptor.getQueueName(), queueDescriptor.getTimeout(),
                                                        queueDescriptor.getTimeoutUnit()),
                                                 VMError.QUEUE_TIMEOUT));

    } catch (ModuleException | TypedException e) {
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
    String correlationId = null;
    Map<String, TypedValue<Serializable>> properties = new HashMap<>();

    if (value instanceof VMErrorResponse) {
      throw new ModuleException((String) ((VMErrorResponse) value).getValue().getValue(), VMError.PUBLISH_CONSUMER_FLOW_ERROR);
    }

    if (value instanceof VMMessage) {
      VMMessage message = (VMMessage) value;
      value = message.getValue();
      correlationId = message.getCorrelationId().orElse(null);
      properties = message.getProperties().orElse(new HashMap<>());
    }

    if (value instanceof TypedValue) {
      TypedValue<Serializable> typedValue = (TypedValue) value;
      resultBuilder.output(typedValue.getValue())
          .mediaType(typedValue.getDataType().getMediaType());
    } else {
      resultBuilder.output(value);
    }

    resultBuilder.attributes(new VMMessageAttributes(queueDescriptor.getQueueName(), correlationId, properties));
    return resultBuilder.build();
  }

  private void doPublish(Serializable content, QueueDescriptor queueDescriptor, Queue queue) {
    try {
      if (!queue.offer(content, queueDescriptor.getQueueTimeoutInMillis())) {
        throw new ModuleException("Timeout publishing message to VM queue " + queueDescriptor.getQueueName(),
                                  VMError.QUEUE_TIMEOUT);
      }
    } catch (InterruptedException e) {
      throw new ModuleException(VMError.QUEUE_TIMEOUT, e);
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

  private Queue getQueue(QueueDescriptor queueDescriptor, VMConnector config, VMConnection connection) {
    return getQueue(queueDescriptor, config, connection, false);
  }

  private Queue getQueue(QueueDescriptor queueDescriptor, VMConnector config, VMConnection connection, boolean skipTransaction) {
    final String queueName = queueDescriptor.getQueueName();

    queueManager.validateQueue(queueName, config);

    return skipTransaction ? queueManager.getQueueWithoutTx(queueName) : connection.getQueue(queueName);
  }
}
