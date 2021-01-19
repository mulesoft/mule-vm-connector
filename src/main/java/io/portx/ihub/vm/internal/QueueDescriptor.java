/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.values.OfValues;

import java.util.concurrent.TimeUnit;

/**
 * Groups common parameters which describe a Queue
 *
 * @since 1.0
 */
public class QueueDescriptor {

  /**
   * The name of the queue
   */
  @Parameter
  @OfValues(value = QueueNamesValueProvider.class, open = false)
  private String queueName;

  /**
   * How long to wait on the queue to complete and operation (either publishing or consuming) before failing
   * with a timeout error
   */
  @Parameter
  @Optional(defaultValue = "5")
  private int timeout = 5;

  /**
   * A {@link TimeUnit} which qualifies the {@link #timeoutUnit}
   */
  @Parameter
  @Optional(defaultValue = "SECONDS")
  private TimeUnit timeoutUnit = SECONDS;

  public QueueDescriptor() {}

  public QueueDescriptor(String queueName) {
    this.queueName = queueName;
  }

  public String getQueueName() {
    return queueName;
  }

  public int getTimeout() {
    return timeout;
  }

  public TimeUnit getTimeoutUnit() {
    return timeoutUnit;
  }

  public long getQueueTimeoutInMillis() {
    return timeoutUnit.toMillis(timeout);
  }
}
