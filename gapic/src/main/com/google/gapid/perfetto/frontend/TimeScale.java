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

import com.google.gapid.perfetto.common.TimeSpan;

public class TimeScale {
  private TimeSpan timeBounds;
  private int startPx;
  private int endPx;
  private double secPerPx = 0;

  public TimeScale(TimeSpan timeBounds, int startPx, int endPx) {
    this.timeBounds = timeBounds;
    this.startPx = startPx;
    this.endPx = endPx;
    updateSlope();
  }

  private void updateSlope() {
    secPerPx = timeBounds.getDuration() / (endPx - startPx);
  }

  public double getStart() {
    return timeBounds.start;
  }

  public double getEnd() {
    return timeBounds.end;
  }

  public double deltaTimeToPx(double time) {
    return Math.round(time / secPerPx);
  }

  public double timeToPx(double time) {
    return startPx + (time - timeBounds.start) / secPerPx;
  }

  public double pxToTime(double px) {
    return timeBounds.start + (px - startPx) * secPerPx;
  }

  public double deltaPxToDuration(double px) {
    return px * secPerPx;
  }

  public void setTimeBounds(TimeSpan timeBounds) {
    this.timeBounds = timeBounds;
    updateSlope();
  }

  public void setLimitsPx(int startPx, int endPx) {
    this.startPx = startPx;
    this.endPx = endPx;
    updateSlope();
  }
}
