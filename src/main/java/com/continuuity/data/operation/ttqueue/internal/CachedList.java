package com.continuuity.data.operation.ttqueue.internal;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public class CachedList<T> {
  final private List<T> list;
  private int currentIndex;

  public static final CachedList EMPTY_LIST = new CachedList(Collections.emptyList());
  public static <T> CachedList<T> emptyList() {
    return EMPTY_LIST;
  }

  public CachedList(List<T> list) {
    this.list = list;
    this.currentIndex = 0;
  }

  public T getNext() {
    if(currentIndex >= list.size()) {
      throw new IllegalStateException(String.format("Out of bounds access of cached list, size of list = %d", list.size()));
    }
    return list.get(currentIndex++);
  }

  public boolean hasNext() {
    return currentIndex < list.size();
  }
}
