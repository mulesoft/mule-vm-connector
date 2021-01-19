/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import org.mule.runtime.extension.api.client.DefaultOperationParameters;
import org.mule.runtime.extension.api.client.ExtensionsClient;

import javax.inject.Inject;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VMConsumeFromQueueWithListenerTestCase extends VMTestCase {

  private static final String ERROR_MESSAGE_FROM_FLOW =
      "Operation '<vm:consume>' in Flow 'consume' is trying to consume from queue 'transientQueue', but Flow "
          + "'transientListener' defines a <vm:listener> on that queue. It's not allowed to consume from a queue on "
          + "which a listener already exists";

  private static final String ERROR_MESSAGE_FROM_CLIENT =
      "Operation '<vm:consume>' is trying to consume from queue 'transientQueue', but Flow "
          + "'transientListener' defines a <vm:listener> on that queue. It's not allowed to consume from a queue on "
          + "which a listener already exists";
  @Rule
  public ExpectedException expectedException = none();

  @Inject
  private ExtensionsClient extensionsClient;

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"vm-configs.xml", "vm-listener-config.xml", "vm-consume-config.xml"};
  }

  @Test
  public void consumeFromQueueWithListener() throws Exception {
    expectException(ERROR_MESSAGE_FROM_FLOW);
    flowRunner("consume").run();
  }

  @Test
  public void consumeFromQueueWithListenerWithExtensionClient() throws Exception {
    expectException(ERROR_MESSAGE_FROM_CLIENT);
    extensionsClient.execute("VM", "consume", DefaultOperationParameters.builder()
        .configName("vm")
        .addParameter("queueName", "transientQueue")
        .build());
  }

  private void expectException(String expectedMessage) {
    expectedException.expectCause(instanceOf(IllegalArgumentException.class));
    expectedException.expectCause(new BaseMatcher<Throwable>() {

      @Override
      public boolean matches(Object item) {
        String message = ((Throwable) item).getMessage();
        assertThat(message, equalTo(expectedMessage));

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Got unexpected message");
      }
    });
  }
}
