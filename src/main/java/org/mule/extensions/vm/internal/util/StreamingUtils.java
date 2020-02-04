/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.vm.internal.util;

import org.mule.runtime.api.metadata.CollectionDataType;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MapDataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.streaming.Cursor;
import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.stream.Collectors;

import java.util.Map;
import java.util.Collection;
import java.util.List;

import static org.mule.runtime.api.metadata.DataType.builder;
import static org.mule.runtime.core.api.util.IOUtils.toByteArray;

public class StreamingUtils {

  /**
   * If the {@code typedValue} has a {@link Collection} or {@link Map} whose values are a stream payload (instance of
   * {@link Cursor}), then this method returns a new {@link TypedValue} which payload has an equivalent, already consumed
   * structure. This functionality makes sense for cases like caching in which the contents of the stream need to survive the
   * completion of the event that generated it.
   * <p>
   * If the payload is a a {@link CursorStream} or a {@link CursorStreamProvider}, then it will be consumed into a {@code byte[]}
   * <p>
   * If the payload is a {@link CursorIterator} or a {@link CursorIteratorProvider}, then the contents will be consumed into a
   * {@link List}.
   * <p>
   * In any other case, the same input event is returned
   *
   * @param typedValue a typed value which might have a {@link Collection} or {@link Map} containing repeatable payload
   * @return a {@link TypedValue}
   * @since 4.3.0
   */
  public static TypedValue resolveCursors(TypedValue<Serializable> typedValue) {
    Object payload = typedValue.getValue();
    DataType dataType = typedValue.getDataType();

    TypedValue newDataType = typedValue;
    Object newPayload = null;

    if (dataType instanceof MapDataType) {
      newPayload = ((Map<?, ?>) payload).entrySet()
          .stream()
          .collect(Collectors.toMap(Map.Entry::getKey, entry -> consumeValue(entry.getValue())));
    } else if (dataType instanceof CollectionDataType) {
      newPayload = ((Collection<?>) payload)
          .stream()
          .map(StreamingUtils::consumeValue)
          .collect(Collectors.toList());
    }

    if (newPayload != null) {
      newDataType = new TypedValue(newPayload, builder(dataType).type(newPayload.getClass()).build());
    }

    return newDataType;
  }

  private static Object consumeValue(Object value) {
    if (value instanceof CursorStream) {
      value = toByteArray(((CursorStream) value));
    } else if (value instanceof CursorStreamProvider) {
      value = toByteArray(((CursorStreamProvider) value).openCursor());
    } else if (value instanceof CursorIterator) {
      value = asList((CursorIterator) value);
    } else if (value instanceof CursorIteratorProvider) {
      value = asList(((CursorIteratorProvider) value).openCursor());
    }

    return value;
  }

  private static Object asList(CursorIterator value) {
    List consumed = new LinkedList<>();
    value.forEachRemaining(consumed::add);
    return consumed;
  }

}

