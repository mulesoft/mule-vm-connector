/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.api;

import static io.portx.ihub.vm.api.QueueType.TRANSIENT;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

/**
 * Defines a queue and its properties
 *
 * @since 1.0
 */
@Alias("queue")
public class QueueDefinition {

  /**
   * The name of the queue
   */
  @Parameter
  @Expression(NOT_SUPPORTED)
  private String queueName;

  /**
   * Whether the queue is transient or persistent
   */
  @Parameter
  @Optional(defaultValue = "TRANSIENT")
  @Expression(NOT_SUPPORTED)
  private QueueType queueType = TRANSIENT;

  /**
   * Defines the maximum number of messages that can be queued.
   */
  @Parameter
  @Optional(defaultValue = "0")
  private int maxOutstandingMessages = 0;

  public String getQueueName() {
    return queueName;
  }

  public QueueType getQueueType() {
    return queueType;
  }

  public int getMaxOutstandingMessages() {
    return maxOutstandingMessages;
  }

}
