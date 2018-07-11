/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import org.mule.runtime.api.message.Error;

/**
 * Specialization of {@link VMMessage} used to communicate that a flow that processed a value
 * obtained through a VM queue has failed
 *
 * @since 2.0
 */
public class VMErrorResponse extends VMMessage {

  private Error error;

  public VMErrorResponse(Error error, String correlationId) {
    super(null, correlationId);
    this.error = error;
  }

  public Error getError() {
    return error;
  }
}
