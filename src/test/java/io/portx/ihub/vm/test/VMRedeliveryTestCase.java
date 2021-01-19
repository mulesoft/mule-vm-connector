/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static org.mule.tck.probe.PollingProber.check;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.util.queue.Queue;

import org.junit.Test;

public class VMRedeliveryTestCase extends VMTestCase {

  public static final int DELAY = 100;
  private static int REDELIVERY_COUNT;
  private static boolean REDELIVERY_EXHAUSTED;

  public static class RedeliveryCount implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      REDELIVERY_COUNT++;
      return event;
    }
  }

  public static class RedeliveryExhausted implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      REDELIVERY_EXHAUSTED = true;
      return event;
    }
  }

  @Override
  protected void doSetUp() throws Exception {
    REDELIVERY_COUNT = 0;
    REDELIVERY_EXHAUSTED = false;
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-configs.xml", "vm-redelivery-config.xml"};
  }

  @Test
  public void redelivery() throws Exception {
    Queue queue = getQueue("persistentQueue");
    queue.offer(STRING_PAYLOAD, TIMEOUT);

    check(TIMEOUT, DELAY, () -> REDELIVERY_EXHAUSTED);
    check(TIMEOUT, DELAY, () -> REDELIVERY_COUNT == 4);
    check(TIMEOUT, DELAY, () -> queue.size() == 0);
  }
}
