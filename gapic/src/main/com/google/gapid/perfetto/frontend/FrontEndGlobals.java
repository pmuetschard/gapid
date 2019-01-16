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
package com.google.gapid.perfetto.frontend;

import static com.google.gapid.widgets.Widgets.scheduleIfNotDisposed;

import com.google.common.collect.Maps;
import com.google.gapid.perfetto.base.Remote;
import com.google.gapid.perfetto.common.Actions.DeferredAction;
import com.google.gapid.perfetto.common.State;

import java.util.List;
import java.util.Map;

public class FrontEndGlobals {
  public static final FrontEndGlobals INSTANCE = new FrontEndGlobals();
  public static FrontEndGlobals feGlobals() { return INSTANCE; }

  private ViewerPage view;
  private Remote<Void> controllers;
  private State state;
  private FrontendLocalState frontendLocalState;
  private Map<String, Object> trackDataStore;
  private Map<Integer, ThreadDesc> threads;
  private Map<String, List<QuantizedLoad>> overviewStore;

  public FrontEndGlobals() {
  }

  public void onUiThread(int delay, Runnable run) {
    scheduleIfNotDisposed(view, delay, () -> scheduleIfNotDisposed(view, run));
  }

  public void initialize(ViewerPage page, Remote<Void> controllersProxy) {
    view = page;
    controllers = controllersProxy;
    state = State.createEmptyState();
    frontendLocalState = new FrontendLocalState();
    trackDataStore = Maps.newHashMap();
    overviewStore = Maps.newHashMap();
    threads = Maps.newHashMap();
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public FrontendLocalState getFrontendLocalState() {
    return frontendLocalState;
  }

  public Map<String, Object> getTrackDataStore() {
    return trackDataStore;
  }

  public Map<Integer, ThreadDesc> getThreads() {
    return threads;
  }

  public Map<String, List<QuantizedLoad>> getOverviewStore() {
    return overviewStore;
  }

  public void dispatch(DeferredAction<?> action) {
    controllers.invoke(null, new Object[] { action });
  }
}
