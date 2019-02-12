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

import static com.google.gapid.perfetto.common.TimeSpan.timeToString;
import static com.google.gapid.perfetto.frontend.FrontEndGlobals.feGlobals;
import static com.google.gapid.perfetto.frontend.GridLines.DESIRED_PX_PER_STEP;
import static com.google.gapid.perfetto.frontend.GridLines.getGridStepSize;
import static com.google.gapid.perfetto.frontend.RenderContext.Style.Fill;

import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.frontend.TrackPanel.TrackDragger;

import org.eclipse.swt.graphics.RGB;

public class TimeAxisPanel implements Panel {
  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
    TimeSpan range = feGlobals().getFrontendLocalState().visibleWindowTime;
    // TS2J: ctx.font = '10px Google Sans';

    ctx.setColor(new RGB(0x99, 0x99, 0x99), new RGB(0x99, 0x99, 0x99));

    double desiredSteps = (double)width / DESIRED_PX_PER_STEP;
    double step = getGridStepSize(range.getDuration(), desiredSteps);
    double start = Math.round(range.start / step) * step;

    for (double s = start; step > 0 && s < range.end; s += step) {
      float xPos = TrackPanel.TRACK_SHELL_WIDTH;
      xPos += Math.floor(timeScale.timeToPx(s));
      if (xPos < TrackPanel.TRACK_SHELL_WIDTH) {
        continue;
      } else if (xPos > width) {
        break;
      }
      ctx.drawRectangle(Fill, xPos, 0, 1, 30);
      ctx.drawText(timeToString(s - range.start), xPos + 5, 10);
    }
  }

  @Override
  public Dragger onDragStart(int x, int y) {
    if (x >= TrackPanel.TRACK_SHELL_WIDTH) {
      return new TrackDragger();
    }
    return Dragger.NONE;
  }

  @Override
  public int getHeight() {
    return 30;
  }
}
