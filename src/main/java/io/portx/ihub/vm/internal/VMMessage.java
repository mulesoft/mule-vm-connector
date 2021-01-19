/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal;

import static java.util.Optional.ofNullable;
import org.mule.runtime.api.metadata.TypedValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a message taken from or sent to a VM queue
 *
 * @since 1.1
 */
public class VMMessage implements Serializable {

  private final TypedValue<Serializable> value;
  private final String correlationId;
  private final Map<String, TypedValue<Serializable>> properties;

  public VMMessage(TypedValue<Serializable> value, String correlationId) {
    this(value, new HashMap(), correlationId);
  }

  public VMMessage(TypedValue<Serializable> value, Map<String, TypedValue<Serializable>> properties, String correlationId) {
    this.value = value;
    this.correlationId = correlationId;
    this.properties = properties;
  }

  public TypedValue<Serializable> getValue() {
    return value;
  }

  public Optional<String> getCorrelationId() {
    return ofNullable(correlationId);
  }

  public Optional<Map<String, TypedValue<Serializable>>> getProperties() {
    return ofNullable(properties);
  }
}
