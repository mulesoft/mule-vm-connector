/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import static org.mule.extensions.vm.api.QueueType.TRANSIENT;
import org.mule.extensions.vm.api.QueueType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

public class QueueListenerDescriptor extends QueueDescriptor {

  /**
   * Whether the queue is transient or persistent
   */
  @Parameter
  @Optional(defaultValue = "TRANSIENT")
  private QueueType queueType = TRANSIENT;

  /**
   * Defines the maximum number of messages that can be queued.
   */
  @Parameter
  @Optional(defaultValue = "0")
  private int maxOutstandingMessages = 0;

  public QueueListenerDescriptor() {}

  public QueueListenerDescriptor(String queueName) {
    super(queueName);
  }

  public QueueType getQueueType() {
    return queueType;
  }

  public int getMaxOutstandingMessages() {
    return maxOutstandingMessages;
  }

}
