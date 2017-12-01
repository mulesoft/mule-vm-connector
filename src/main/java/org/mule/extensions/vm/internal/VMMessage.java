/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal;

import static java.util.Optional.ofNullable;
import org.mule.runtime.api.metadata.TypedValue;

import java.io.Serializable;
import java.util.Optional;

/**
 * Represents a message taken from or sent to a VM queue
 *
 * @since 1.1
 */
public class VMMessage implements Serializable {

  private final TypedValue<Serializable> value;
  private final String correlationId;

  public VMMessage(TypedValue<Serializable> value, String correlationId) {
    this.value = value;
    this.correlationId = correlationId;
  }

  public TypedValue<Serializable> getValue() {
    return value;
  }

  public Optional<String> getCorrelationId() {
    return ofNullable(correlationId);
  }
}
