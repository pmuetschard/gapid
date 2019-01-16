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

import static com.google.gapid.perfetto.frontend.FrontEndGlobals.feGlobals;

import com.google.common.collect.Lists;
import com.google.gapid.perfetto.base.Remote;
import com.google.gapid.perfetto.common.State;
import com.google.gapid.perfetto.common.State.TraceTime;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.controller.ControllerGlobals;

import java.util.List;
import java.util.Map;

public class FrontEnd implements Remote<FrontEnd.Method> {
  private final ViewerPage view;

  public FrontEnd(ViewerPage view) {
    this.view = view;
  }

  @Override
  public Object invoke(Method method, Object[] args) {
    switch (method) {
      case updateState:
        feGlobals().setState((State)args[0]);
        TraceTime vizTraceTime = feGlobals().getState().visibleTraceTime;
        if (vizTraceTime.lastUpdate > feGlobals().getFrontendLocalState().getVisibleTimeLastUpdate()) {
          feGlobals().getFrontendLocalState().updateVisibleTime(
              new TimeSpan(vizTraceTime.startSec, vizTraceTime.endSec));
        }
        redraw(true);
        break;

      case publishOverviewData:
        @SuppressWarnings("unchecked") Map<String, ?> overviewData = (Map<String, ?>)args[0];
        Map<String, List<QuantizedLoad>> mine = feGlobals().getOverviewStore();
        for (Map.Entry<String, ?> e : overviewData.entrySet()) {
          List<QuantizedLoad> list = mine.computeIfAbsent(e.getKey(), $ -> Lists.newArrayList());
          if (e.getValue() instanceof QuantizedLoad) {
            list.add((QuantizedLoad)e.getValue());
          } else {
            @SuppressWarnings("unchecked")
            List<QuantizedLoad> newList = (List<QuantizedLoad>)e.getValue();
            list.addAll(newList);
          }
        }
        redraw(false);
        break;

      case publishTrackData:
        ControllerGlobals.TrackData<?> data = (ControllerGlobals.TrackData<?>)args[0];
        feGlobals().getTrackDataStore().put(data.id, data.data);
        redraw(false);
        break;

      case publishThreads:
        feGlobals().getThreads().clear();
        @SuppressWarnings("unchecked") List<ThreadDesc> threads = (List<ThreadDesc>)args[0];
        for (ThreadDesc thread : threads) {
          feGlobals().getThreads().put(thread.utid, thread);
        }
        redraw(false);
        break;

      case publishQueryResult:
        // TODO
        break;

      case publishLegacyTrace:
        // Ignore
        break;
    }
    return null; // sigh
  }

  private void redraw(boolean update) {
    if (update) {
      view.updateUi();
    } else {
      view.scheduleRedraw();
    }
  }

  public static enum Method {
    updateState, publishOverviewData, publishTrackData, publishThreads, publishQueryResult, publishLegacyTrace;
  }
}
