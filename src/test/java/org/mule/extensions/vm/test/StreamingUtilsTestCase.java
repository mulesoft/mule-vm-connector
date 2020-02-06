/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.test;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.Test;
import org.mule.extensions.vm.internal.util.StreamingUtils;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;

public class StreamingUtilsTestCase {

  @Test
  public void resolveIterator() {
    List<String> data = asList("hello", "world");
    TypedValue typedValue = TypedValue.of(data.iterator());
    TypedValue resolvedTypedValue = StreamingUtils.resolveCursors(typedValue);
    assertThat(resolvedTypedValue.getValue(), is(equalTo(data)));
  }

  @Test
  public void resolveInputStream() {
    byte[] data = "hello".getBytes();
    TypedValue typedValue = TypedValue.of(new ByteArrayInputStream(data));
    TypedValue resolvedTypedValue = StreamingUtils.resolveCursors(typedValue);
    assertThat(resolvedTypedValue.getValue(), is(equalTo(data)));
  }

  @Test
  public void resolveCursorIteratorProvider() {
    List<String> data = asList("hello", "world");
    CursorIteratorProvider cursorIteratorProvider = VMPublishTestCase.mockCursorIteratorProvider(data.iterator());
    TypedValue typedValue = TypedValue.of(cursorIteratorProvider);
    TypedValue resolvedTypedValue = StreamingUtils.resolveCursors(typedValue);
    assertThat(resolvedTypedValue.getValue(), is(equalTo(data)));
  }

  @Test
  public void resolveCursorIterator() {
    List<String> data = asList("hello", "world");
    CursorIterator cursorIterator = VMPublishTestCase.mockCursorIterator(data.iterator());
    TypedValue typedValue = TypedValue.of(cursorIterator);
    TypedValue resolvedTypedValue = StreamingUtils.resolveCursors(typedValue);
    assertThat(resolvedTypedValue.getValue(), is(equalTo(data)));
  }
}
