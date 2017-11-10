/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import org.mule.extensions.vm.internal.listener.VMListener;
import org.mule.runtime.api.metadata.TypedValue;

import java.io.Serializable;

/**
 * Command used to tell a {@link VMListener} that the {@link #value} should be pushed into the owning Flow,
 * and the obtained response should be published into a queue of name {@link #replyToQueueName}
 *
 * @since 1.0
 */
public class ReplyToCommand implements Serializable {

  private static final long serialVersionUID = -6702564673951123007L;

  private final TypedValue<Serializable> value;
  private final String replyToQueueName;

  /**
   * Creates a new instance
   *
   * @param value            the published content
   * @param replyToQueueName the name of the reply-To queue
   */
  public ReplyToCommand(TypedValue<Serializable> value, String replyToQueueName) {
    this.value = value;
    this.replyToQueueName = replyToQueueName;
  }

  public TypedValue<Serializable> getValue() {
    return value;
  }

  public String getReplyToQueueName() {
    return replyToQueueName;
  }
}
