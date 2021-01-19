/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import io.portx.ihub.vm.internal.VMConnector;
import io.portx.ihub.vm.internal.VMConnectorQueueManager;
import io.portx.ihub.vm.internal.VMMessage;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.construct.Flow;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.extension.ExtensionManager;
import org.mule.runtime.core.api.util.queue.Queue;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.runner.RunnerDelegateTo;

import java.util.Collection;
import java.util.function.Function;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunnerDelegateTo(Parameterized.class)
public class VMTxTestCase extends VMTestCase {

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {"vm-tx-config.xml", (Function<VMTxTestCase, Queue>) tc -> tc.getTransientQueue()},
        {"vm-tx-persistent-config.xml", (Function<VMTxTestCase, Queue>) tc -> tc.getPersistentQueue()}
    });
  }

  @Inject
  private VMConnectorQueueManager vmQueueManager;

  @Inject
  private ExtensionManager extensionManager;

  private final String config;

  private final Function<VMTxTestCase, Queue> queueGetter;

  public VMTxTestCase(String config, Function<VMTxTestCase, Queue> queueGetter) {
    this.config = config;
    this.queueGetter = queueGetter;
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {config, "vm-configs.xml"};
  }

  @Test
  public void publishCommit() throws Exception {
    simulateListener();

    publish(false);
    TypedValue typedValue = ((VMMessage) queueGetter.apply(this).poll(1000)).getValue();
    assertThat(typedValue.getValue(), equalTo(STRING_PAYLOAD));
  }

  @Test
  public void publishRollback() throws Exception {
    simulateListener();

    assertThat(publish(true), is(nullValue()));
    assertThat(queueGetter.apply(this).poll(1000), is(nullValue()));
  }

  @Test
  public void listenerCommit() throws Exception {
    startListenerFlow("listener");

    queueGetter.apply(this).put(STRING_PAYLOAD);
    new PollingProber(1000, 100).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        return CAPTURED.size() == 1;
      }
    });

    assertThat(queueGetter.apply(this).poll(100), is(nullValue()));
  }

  @Test
  public void listenerRollback() throws Exception {
    final String flowName = "failingListener";
    startListenerFlow(flowName);

    queueGetter.apply(this).put(STRING_PAYLOAD);
    new PollingProber(1000, 100).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        return !CAPTURED.isEmpty();
      }
    });

    stopListenerFlow(flowName);
    String value = (String) queueGetter.apply(this).poll(1000);
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
