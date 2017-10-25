/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.api;

import static java.util.Optional.empty;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

import java.util.Optional;

/**
 * Error types for the VM connector
 *
 * @since 1.0
 */
public enum VMError implements ErrorTypeDefinition<VMError> {

  /**
   * Timeout exceeded waiting for response on a queue
   */
  QUEUE_TIMEOUT,

  /**
   * The referenced queue is empty.
   */
  EMPTY_QUEUE;


  @Override
  public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
    return empty();
  }

}
