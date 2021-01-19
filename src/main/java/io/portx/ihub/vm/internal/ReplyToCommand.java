/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import io.portx.ihub.vm.internal.listener.VMListener;
import org.mule.runtime.api.metadata.TypedValue;

import java.io.Serializable;
import java.util.Map;

/**
 * Command used to tell a {@link VMListener} that the {@link #value} should be pushed into the owning Flow,
 * and the obtained response should be published into a queue of name {@link #replyToQueueName}
 *
 * @since 1.0
 */
public class ReplyToCommand extends VMMessage {

  private static final long serialVersionUID = -6702564673951123007L;

  private final String replyToQueueName;

  /**
   * Creates a new instance
   *
   * @param value            the published content
   * @param replyToQueueName the name of the reply-To queue
   */
  public ReplyToCommand(TypedValue<Serializable> value, String replyToQueueName, String correlationInfo) {
    super(value, correlationInfo);
    this.replyToQueueName = replyToQueueName;
  }

  /**
   * Creates a new instance
   *
   * @param value            the published content
   * @param properties       optional map of user properties
   * @param replyToQueueName the name of the reply-To queue
   */
  public ReplyToCommand(TypedValue<Serializable> value, Map<String, TypedValue<Serializable>> properties, String replyToQueueName,
                        String correlationInfo) {
    super(value, properties, correlationInfo);
    this.replyToQueueName = replyToQueueName;
  }

  public String getReplyToQueueName() {
    return replyToQueueName;
  }
}
