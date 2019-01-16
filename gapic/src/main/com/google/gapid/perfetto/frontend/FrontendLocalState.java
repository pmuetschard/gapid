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

import static com.google.gapid.perfetto.base.Logging.assertExists;
import static com.google.gapid.perfetto.frontend.FrontEndGlobals.feGlobals;

import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.State.TraceTime;
import com.google.gapid.perfetto.common.TimeSpan;

public class FrontendLocalState {
  public TimeSpan visibleWindowTime = new TimeSpan(0, 10);
  public TimeScale timeScaleTop = new TimeScale(visibleWindowTime, 0, 0);
  public TimeScale timeScaleTrack = new TimeScale(visibleWindowTime, 0, 0);
  public TimeScale totalTimeScale = new TimeScale(visibleWindowTime, 0, 0);
  private long visibleTimeLastUpdate = 0;
  private TimeSpan pendingGlobalTimeUpdate;
  public boolean perfDebug = false;
  public int hoveredUtid = -1;
  public int hoveredPid = -1;

  // TODO: there is some redundancy in the fact that both |visibleWindowTime|
  // and a |timeScale| have a notion of time range. That should live in one
  // place only.
  public void updateVisibleTime(TimeSpan ts) {
    TraceTime traceTime = feGlobals().getState().traceTime;
    totalTimeScale.setTimeBounds(new TimeSpan(traceTime.startSec, traceTime.endSec));
    double startSec = Math.max(ts.start, traceTime.startSec);
    double endSec = Math.min(ts.end, traceTime.endSec);
    visibleWindowTime = new TimeSpan(startSec, endSec);
    timeScaleTop.setTimeBounds(visibleWindowTime);
    timeScaleTrack.setTimeBounds(visibleWindowTime);
    visibleTimeLastUpdate = System.currentTimeMillis() / 1000;
    // Post a delayed update to the controller.
    boolean alreadyPosted = pendingGlobalTimeUpdate != null;
    pendingGlobalTimeUpdate = visibleWindowTime;
    if (alreadyPosted) {
      return;
    }
    visibleTimeLastUpdate = System.currentTimeMillis() / 1000;
    feGlobals().dispatch(Actions.setVisibleTraceTime(new TraceTime(
        pendingGlobalTimeUpdate.start, pendingGlobalTimeUpdate.end, visibleTimeLastUpdate)));
    pendingGlobalTimeUpdate = null;
  }

  public long getVisibleTimeLastUpdate() {
    return visibleTimeLastUpdate;
  }

  /*
  public boolean togglePerfDebug() {
    this.perfDebug = !this.perfDebug;
    feGlobals().getRafScheduler().scheduleFullRedraw();
  }
  */

  public void setHoveredUtidAndPid(int utid, int pid) {
    this.hoveredUtid = utid;
    this.hoveredPid = pid;
    //feGlobals().getRafScheduler().scheduleFullRedraw();
  }


  public void drag(TimeSpan atDragStart, int deltaPx, boolean top) {
    assertExists(atDragStart);
    double delta = (top ? timeScaleTop : timeScaleTrack).deltaPxToDuration(-deltaPx);
    TraceTime traceTime = feGlobals().getState().traceTime;
    double start = atDragStart.start + delta;
    double end = atDragStart.end + delta;
    if (start < traceTime.startSec) {
      start = traceTime.startSec;
      end = start + atDragStart.getDuration();
    } else if (end > traceTime.endSec) {
      end = traceTime.endSec;
      start = end - atDragStart.getDuration();
    }
    updateVisibleTime(new TimeSpan(start, end));
  }
}
