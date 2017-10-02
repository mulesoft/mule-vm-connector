/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.extensions.vm.api.VMError.EMPTY_QUEUE;
import static org.mule.extensions.vm.api.VMError.QUEUE_NOT_FOUND;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import static org.mule.runtime.api.metadata.DataType.STRING;

import org.mule.extensions.vm.internal.QueueListenerDescriptor;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;

import org.junit.Test;

import java.io.Serializable;

public class VMConsumeTestCase extends VMTestCase {

  @Override
  protected String getConfigFile() {
    return "vm-consume-config.xml";
  }

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    vmQueueManager.createQueue(new QueueListenerDescriptor(TRANSIENT_QUEUE_NAME), "consume");
  }

  @Test
  public void consumeTypedValue() throws Exception {
    TypedValue<String> value = new TypedValue<>(STRING_PAYLOAD, JSON_STRING);
    offer(value);

    TypedValue<String> payload = consume().getMessage().getPayload();
    assertThat(payload.getValue(), is(value.getValue()));
    assertThat(payload.getDataType(), is(JSON_STRING));
  }

  @Test
  public void consumeUnexistingQueue() throws Exception {
    expectedError.expectErrorType(VM_ERROR_NAMESPACE, QUEUE_NOT_FOUND.name());
    flowRunner("unexisting").run();
  }

  @Test
  public void consumeSingleValue() throws Exception {
    offer(STRING_PAYLOAD);

    TypedValue<String> payload = consume().getMessage().getPayload();
    assertThat(payload.getValue(), is(STRING_PAYLOAD));
    assertThat(payload.getDataType(), is(STRING));
  }

  @Test
  public void emptyQueue() throws Exception {
    expectedError.expectErrorType(VM_ERROR_NAMESPACE, EMPTY_QUEUE.name());
    flowRunner("consume").run();
  }

  private void offer(Serializable value) throws Exception {
    getTransientQueue().offer(value, 1000);
  }

  private CoreEvent consume() throws Exception {
    flowRunner("consume").run();
    return getCapturedEvent();
  }
}
