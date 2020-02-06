/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal.util;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.toMap;
import static org.mule.runtime.api.metadata.DataType.builder;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
import static org.mule.runtime.core.api.util.IOUtils.toByteArray;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.Cursor;
import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;

public final class StreamingUtils {

  private StreamingUtils() {}

  /**
   * If the {@code typedValue} has a {@link Map} whose values are a stream payload (instance of {@link Cursor}) then this method
   * returns a new {@link TypedValue} which payload has an equivalent, already consumed structure. This functionality makes sense
   * for cases like caching in which the contents of the stream need to survive the completion of the event that generated it.
   * <p>
   * If the payload is a a {@link InputStream} or a {@link CursorStreamProvider}, then it will be consumed into a {@code byte[]}
   * <p>
   * If the payload is a {@link Iterator} or a {@link CursorIteratorProvider}, then the contents will be consumed into a
   * {@link List}.
   * <p>
   * In any other case, the same {@code typedValue} is returned
   *
   * @param typedValue a typed value which might have a {@link Map} containing repeatable payload or might be a repeatable payload
   *        itself
   * @return a {@link TypedValue}
   * @since 2.1.0
   */
  public static TypedValue resolveCursors(TypedValue<Serializable> typedValue) {
    Object payload = typedValue.getValue();
    DataType dataType = typedValue.getDataType();

    TypedValue newTypedValue = typedValue;
    Object newPayload = null;

    if (payload instanceof Map) {
      newPayload = ((Map<?, ?>) payload).entrySet()
          .stream()
          .collect(toMap(Entry::getKey, entry -> consumeValue(entry.getValue())));
    } else {
      newPayload = consumeValue(payload);
    }

    if (newPayload != payload) {
      newTypedValue = new TypedValue(newPayload, builder(dataType).type(newPayload.getClass()).build());
    }

    return newTypedValue;
  }

  private static Object consumeValue(Object value) {
    if (value instanceof InputStream) {
      value = toByteArray(((InputStream) value));
    } else if (value instanceof Iterator) {
      value = asList((Iterator) value);
    } else if (value instanceof CursorStreamProvider) {
      CursorStream cursorStream = null;
      try {
        cursorStream = ((CursorStreamProvider) value).openCursor();
        value = toByteArray(cursorStream);
      } finally {
        closeQuietly(cursorStream);
      }
    } else if (value instanceof CursorIteratorProvider) {
      CursorIterator cursorIterator = null;
      try {
        cursorIterator = ((CursorIteratorProvider) value).openCursor();
        value = asList(cursorIterator);
      } finally {
        closeQuietly(cursorIterator);
      }
    }

    return value;
  }

  private static Object asList(Iterator value) {
    List consumed = new LinkedList<>();
    value.forEachRemaining(consumed::add);
    return consumed;
  }

}

