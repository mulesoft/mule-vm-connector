/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.util.concurrent.TimeUnit;

public class QueueDescriptor {

  /**
   * The name of the queue
   */
  @Parameter
  private String queueName;

  /**
   * How long to wait on the queue to complete and operation (either publishing or consuming) before failing
   * with a timeout error
   */
  @Parameter
  @Optional(defaultValue = "5")
  private int queueTimeout = 5;

  /**
   * A {@link TimeUnit} which qualifies the {@link #queueTimeoutUnit}
   */
  @Parameter
  @Optional(defaultValue = "SECONDS")
  private TimeUnit queueTimeoutUnit = SECONDS;

  public QueueDescriptor() {}

  public QueueDescriptor(String queueName) {
    this.queueName = queueName;
  }

  public String getQueueName() {
    return queueName;
  }

  public int getQueueTimeout() {
    return queueTimeout;
  }

  public TimeUnit getQueueTimeoutUnit() {
    return queueTimeoutUnit;
  }

  public long getQueueTimeoutInMillis() {
    return queueTimeoutUnit.toMillis(queueTimeout);
  }
}
