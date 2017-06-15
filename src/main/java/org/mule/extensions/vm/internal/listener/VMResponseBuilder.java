/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal.listener;

import static org.mule.runtime.extension.api.annotation.param.Optional.PAYLOAD;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.io.Serializable;

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
  @Content
  @Optional(defaultValue = PAYLOAD)
  private TypedValue<Serializable> body;

  public TypedValue<Serializable> getBody() {
    return body;
  }
}
