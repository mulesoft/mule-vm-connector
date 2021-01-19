/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package io.portx.ihub.vm.internal.listener;

import static java.util.Optional.ofNullable;
import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.runtime.parameter.ParameterResolver;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * a Response builder parameter group for the vm:listener
 *
 * @since 1.0
 */
public class VMResponseBuilder {

  // TODO: MULE-13074 - this should be a ParameterResolver for lazy evaluation
  // TODO: MULE-13070 - It should not be necessary to mark it as default payload
  /**
   * The content to send to the reply-To queue. Will only be used and evaluated if
   * the message got into the queue through the {@code vm:publish-consume} operation
   */
  @Parameter
  @Content(primary = true)
  @Optional(defaultValue = PAYLOAD)
  private TypedValue<Serializable> content;

  /**
   * The properties to send along with the content
   */
  @Parameter
  @Content
  @Optional
  Map<String, TypedValue<Serializable>> properties;

  public TypedValue<Serializable> getContent() {
    return content;
  }

  public java.util.Optional<Map<String, TypedValue<Serializable>>> getProperties() {
    return ofNullable(properties);
  }
}
