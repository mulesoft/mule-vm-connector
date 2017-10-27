/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import org.mule.extensions.vm.internal.VMConnector;
import org.mule.extensions.vm.internal.VMConnectorQueueManager;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.extension.ExtensionManager;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;

import javax.inject.Inject;

import org.junit.Test;

public class VMTxTestCase extends VMTestCase {

  @Inject
  private VMConnectorQueueManager vmQueueManager;

  @Inject
  private ExtensionManager extensionManager;

  @Override
  protected String[] getConfigFiles() {
    return new String[]{"vm-tx-config.xml", "vm-configs.xml"};
  }

  @Test
  public void publishCommit() throws Exception {
    simulateListener();

    publish(false);
    TypedValue<String> typedValue = (TypedValue<String>) getTransientQueue().poll(1000);
    assertThat(typedValue.getValue(), equalTo(STRING_PAYLOAD));
  }

  @Test
  public void publishRollback() throws Exception {
    simulateListener();

    assertThat(publish(true), is(nullValue()));
    assertThat(getTransientQueue().poll(1000), is(nullValue()));
  }

  @Test
  public void listenerCommit() throws Exception {
    startListenerFlow("listener");

    getTransientQueue().put(STRING_PAYLOAD);
    new PollingProber(1000, 100).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        return CAPTURED.size() == 1;
      }
    });

    assertThat(getTransientQueue().poll(100), is(nullValue()));
  }

  @Test
  public void listenerRollback() throws Exception {
    final String flowName = "failingListener";
    startListenerFlow(flowName);

    getTransientQueue().put(STRING_PAYLOAD);
    new PollingProber(1000, 100).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        return !CAPTURED.isEmpty();
      }
    });

    stopListenerFlow(flowName);
    String value = (String) getTransientQueue().poll(1000);
    assertThat(value, equalTo(STRING_PAYLOAD));
  }

  private CoreEvent publish(boolean fail) throws Exception {
    try {
      return flowRunner("publishInTx")
          .withPayload(STRING_PAYLOAD)
          .withVariable("fail", fail)
          .run();
    } catch (Exception e) {
      return null;
    }
  }

  private void startListenerFlow(String flowName) throws Exception {
    Flow flow = (Flow) getFlowConstruct(flowName);
    flow.start();
  }


  private void stopListenerFlow(String flowName) throws Exception {
    Flow flow = (Flow) getFlowConstruct(flowName);
    flow.stop();
  }

  private void simulateListener() throws Exception {
    VMConnector config = (VMConnector) extensionManager.getConfiguration("vm", testEvent()).getValue();
    vmQueueManager.registerListenerQueue(config, TRANSIENT_QUEUE_NAME, mock(ComponentLocation.class));
  }
}
