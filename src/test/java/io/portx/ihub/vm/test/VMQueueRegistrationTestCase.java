/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static java.util.Arrays.asList;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.portx.ihub.vm.api.QueueDefinition;
import io.portx.ihub.vm.api.QueueType;
import io.portx.ihub.vm.internal.VMConnector;
import io.portx.ihub.vm.internal.VMConnectorQueueManager;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Stoppable;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VMQueueRegistrationTestCase extends VMTestCase {

  @Rule
  public ExpectedException expectedException = none();

  @Inject
  private VMConnectorQueueManager connectorQueueManager;

  @Override
  protected String getConfigFile() {
    return "vm-configs.xml";
  }

  @Test
  public void registerExistingQueue() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException
        .expectMessage("<vm:config> 'mockConfig' is trying to define queue 'transientQueue' which is already defined by config 'vm'");

    redefineTransientQueue();
  }

  @Test
  public void registerExistingQueueAfterStop() throws Exception {
    ((Stoppable) registry.lookupByName("vm").get()).stop();
    redefineTransientQueue();
  }

  @Test
  public void registerExistingTransientQueueAfterStopWithDifferentType() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException
        .expectMessage("<vm:config> 'mockConfig' is trying to define PERSISTENT queue 'transientQueue' which already exists is already configured in the Mule runtime as transient");

    ((Stoppable) registry.lookupByName("vm").get()).stop();
    defineQueue(TRANSIENT_QUEUE_NAME, QueueType.PERSISTENT);
  }

  @Test
  public void registerExistingPersistentQueueAfterStopWithDifferentType() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException
        .expectMessage("<vm:config> 'mockConfig' is trying to define TRANSIENT queue 'persistentQueue' which already exists is already configured in the Mule runtime as persistent");

    ((Stoppable) registry.lookupByName("vm").get()).stop();
    defineQueue(PERSISTENT_QUEUE_NAME, QueueType.TRANSIENT);
  }

  private void redefineTransientQueue() throws InitialisationException {
    defineQueue(TRANSIENT_QUEUE_NAME, QueueType.TRANSIENT);
  }

  private void defineQueue(String name, QueueType type) throws InitialisationException {
    VMConnector config = mock(VMConnector.class);
    when(config.getName()).thenReturn("mockConfig");
    QueueDefinition def = mock(QueueDefinition.class);
    when(def.getQueueName()).thenReturn(name);
    when(def.getQueueType()).thenReturn(type);
    connectorQueueManager.createQueues(config, asList(def));
  }
}
