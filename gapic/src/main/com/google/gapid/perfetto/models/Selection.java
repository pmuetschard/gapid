/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gapid.perfetto.models;

import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.models.CounterTrack.Values;
import com.google.gapid.perfetto.models.SliceTrack.Slice;
import com.google.gapid.perfetto.models.ThreadTrack.StateSlice;
import com.google.gapid.perfetto.views.MultiSelectionView;
import com.google.gapid.perfetto.views.State;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Data about the current selection in the UI.
 */
public interface Selection<Key> {
  public String getTitle();
  public boolean contains(Key key);
  public Composite buildUi(Composite parent, State state);
  public default void markTime(@SuppressWarnings("unused") State state) { /* do nothing */ }
  public default void zoom(@SuppressWarnings("unused") State state) { /* do nothing */ }
  public default boolean isEmpty() { return this == EMPTY_SELECTION; }

  @SuppressWarnings("rawtypes")
  public static final Selection EMPTY_SELECTION = new EmptySelection<>();

  @SuppressWarnings("unchecked")
  public static <K> Selection<K> emptySelection() {
      return EMPTY_SELECTION;
  }

  public static class EmptySelection<K> implements Selection<K> {
    @Override
    public String getTitle() {
      return "";
    }

    @Override
    public boolean contains(K key) {
      return false;
    }

    @Override
    public Composite buildUi(Composite parent, State state) {
      return new Composite(parent, SWT.NONE);
    }
  }

  public static class MultiSelection {
    private final NavigableMap<Kind<?>, Selection<?>> selections;

    public <Key> MultiSelection(Kind<Key> type, Selection<Key> selection) {
      this.selections = Maps.newTreeMap();
      this.selections.put(type, selection);
    }

    public MultiSelection(NavigableMap<Kind<?>, Selection<?>> selections) {
      this.selections = selections;
    }

    public Composite buildUi(Composite parent, State state) {
      if (selections.size() == 1) {
        return firstSelection().buildUi(parent, state);
      } else {
        return new MultiSelectionView(parent, selections, state);
      }
    }

    @SuppressWarnings("unchecked")
    public <Key> Selection<Key> getSelection(Kind<Key> type) {
      return selections.containsKey(type) ?
          (Selection<Key>) selections.get(type) : Selection.emptySelection();
    }

    public void markTime(State state) {
      // TODO: is this good enough?
      if (selections.size() == 1) {
        firstSelection().markTime(state);
      }
    }

    public void zoom(State state) {
      // TODO: handle zooming into multiple selections.
      if (selections.size() == 1) {
        firstSelection().zoom(state);
      }
    }

    private Selection<?> firstSelection() {
      return selections.firstEntry().getValue();
    }
  }

  public static class CombiningBuilder {
    private final Map<Kind<?>, ListenableFuture<Combinable<?>>> selections =
        Maps.newTreeMap();

    @SuppressWarnings("unchecked")
    public <T extends Combinable<T>> void add(
        Kind<?> type, ListenableFuture<Combinable<?>> selection) {
      selections.merge(type, selection, (f1, f2) -> transformAsync(f1, r1 ->
        transform(f2, r2 -> (((T)r1).combine((T)r2)))));
    }

    public ListenableFuture<MultiSelection> build() {
      return transform(Futures.allAsList(selections.values()), sels -> {
        Iterator<Kind<?>> keys = selections.keySet().iterator();
        Iterator<Combinable<?>> vals = sels.iterator();
        TreeMap<Kind<?>, Selection<?>> res = Maps.newTreeMap();
        while (keys.hasNext()) {
          res.put(keys.next(), vals.next().build());
        }
        return new MultiSelection(res);
      });
    }

    public static interface Combinable<T extends Combinable<T>> {
      public T combine(T other);
      public Selection<?> build();
    }
  }

  @SuppressWarnings("unused")
  public static class Kind<Key> implements Comparable<Kind<?>>{
    public static final Kind<Slice.Key> Thread = new Kind<Slice.Key>(0);
    public static final Kind<StateSlice.Key> ThreadState = new Kind<StateSlice.Key>(1);
    public static final Kind<Long> Cpu = new Kind<Long>(2);
    public static final Kind<Slice.Key> Gpu = new Kind<Slice.Key>(3);
    public static final Kind<Values.Key> Counter = new Kind<Values.Key>(4);
    public static final Kind<FrameEventsTrack.Slice.Key> FrameEvents = new Kind<FrameEventsTrack.Slice.Key>(5);

    public int priority;

    public Kind(int priority) {
      this.priority = priority;
    }

    @Override
    public int compareTo(Kind<?> other) {
      return this.priority - other.priority;
    }
  }
}
