/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal.connection;

import static org.mule.runtime.api.connection.ConnectionValidationResult.success;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.extension.api.connectivity.NoConnectivityTest;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Returns instances of {@link VMConnection}
 *
 * @since 1.0
 */
public class VMConnectionProvider implements ConnectionProvider<VMConnection>, NoConnectivityTest {

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  private QueueManager queueManager;

  @Override
  public VMConnection connect() throws ConnectionException {
    return new VMConnection(queueManager.getQueueSession());
  }

  @Override
  public void disconnect(VMConnection connection) {
    connection.close();
  }

  @Override
  public ConnectionValidationResult validate(VMConnection connection) {
    return success();
  }
}
