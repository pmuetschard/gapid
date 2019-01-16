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

import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.frontend.TrackPanel.TrackDragger;

import org.eclipse.swt.SWT;

public class TimeAxisPanel implements Panel {
  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
    TimeSpan range = feGlobals().getFrontendLocalState().visibleWindowTime;
    // TS2J: ctx.font = '10px Google Sans';

    ctx.gc.setBackground(ctx.colors.get(0x99, 0x99, 0x99));
    ctx.gc.setForeground(ctx.colors.get(0x99, 0x99, 0x99));

    double desiredSteps = (double)width / DESIRED_PX_PER_STEP;
    double step = getGridStepSize(range.getDuration(), desiredSteps);
    double start = Math.round(range.start / step) * step;

    for (double s = start; step > 0 && s < range.end; s += step) {
      double xPos = TrackPanel.TRACK_SHELL_WIDTH;
      xPos += Math.floor(timeScale.timeToPx(s));
      if (xPos < TrackPanel.TRACK_SHELL_WIDTH) {
        continue;
      } else if (xPos > width) {
        break;
      }
      ctx.gc.fillRectangle((int)xPos, 0, 1, 30);
      ctx.gc.drawText(timeToString(s - range.start), (int)xPos + 5, 10, SWT.DRAW_TRANSPARENT);
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
