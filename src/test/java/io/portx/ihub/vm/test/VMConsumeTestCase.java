/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.api.metadata.DataType.JSON_STRING;
import static org.mule.runtime.api.metadata.DataType.STRING;
import static org.mule.tck.junit4.matcher.DataTypeCompatibilityMatcher.assignableTo;

import io.portx.ihub.vm.api.VMError;
import io.portx.ihub.vm.internal.VMConnector;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.extension.ExtensionManager;

import java.io.Serializable;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VMConsumeTestCase extends VMTestCase {

  @Rule
  public ExpectedException expectedException = none();

  @Inject
  private ExtensionManager extensionManager;

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-configs.xml", "vm-consume-config.xml"};
  }

  @Test
  public void consumeTypedValue() throws Exception {
    TypedValue<String> value = new TypedValue<>(STRING_PAYLOAD, JSON_STRING);
    offer(value);

    TypedValue<String> payload = consume().getMessage().getPayload();
    assertThat(payload.getValue(), is(value.getValue()));
    assertThat(payload.getDataType(), is(assignableTo(JSON_STRING)));
  }

  @Test
  public void consumeSingleValue() throws Exception {
    offer(STRING_PAYLOAD);

    TypedValue<String> payload = consume().getMessage().getPayload();
    assertThat(payload.getValue(), is(STRING_PAYLOAD));
    assertThat(payload.getDataType(), is(assignableTo(STRING)));
  }

  @Test
  public void emptyQueue() throws Exception {
    expectedError.expectErrorType(VM_ERROR_NAMESPACE, VMError.EMPTY_QUEUE.name());
    flowRunner("consume").run();
  }

  @Test
  public void consumeFromQueueWithListener() throws Exception {
    expectedException.expectCause(instanceOf(IllegalArgumentException.class));

    VMConnector config = (VMConnector) extensionManager.getConfiguration("vm", testEvent()).getValue();

    vmQueueManager.registerListenerQueue(config, TRANSIENT_QUEUE_NAME, mock(ComponentLocation.class));
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
