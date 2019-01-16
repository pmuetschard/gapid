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
import static com.google.gapid.perfetto.tracks.Colors.hueForCpu;

import com.google.gapid.perfetto.common.TimeSpan;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import java.util.List;
import java.util.Map;

public class OverviewTimelinePanel implements Panel {
  private static final int HEADER_HEIGHT = 25;
  private static final int TRACK_HEIGHT = 75;
  private static final int HANDLE_WIDTH = 3;
  private static final int HANDLE_HEIGHT = 25;

  public OverviewTimelinePanel() {
  }

  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    TimeScale timeScale = feGlobals().getFrontendLocalState().totalTimeScale;

    // Draw time labels on the top header.
    // TS2J: ctx.font = '10px Google Sans';
    ctx.gc.setBackground(ctx.colors.get(0x99, 0x99, 0x99));
    ctx.gc.setForeground(ctx.colors.get(0x99, 0x99, 0x99));
    for (int i = 0; i < 100; i++) {
      double xPos = i * width / 100.0;
      double t = timeScale.pxToTime(xPos);
      if (xPos < 0) {
        continue;
      } else if (xPos > width) {
        break;
      }

      if (i % 10 == 0) {
        ctx.gc.fillRectangle((int)xPos, 0, 1, HEADER_HEIGHT - 5);
        ctx.gc.drawText(timeToString(t - timeScale.getStart()), (int)xPos + 5, 10, SWT.DRAW_TRANSPARENT);
      } else {
        ctx.gc.fillRectangle((int)xPos, 0, 1, 5);
      }
    }

    // Draw mini-tracks with quanitzed density for each process.
    Map<String, List<QuantizedLoad>> overviewStore = feGlobals().getOverviewStore();
    if (!overviewStore.isEmpty()) {
      int numTracks = overviewStore.size();
      int y = 0;
      double trackHeight = (TRACK_HEIGHT - 2.0) / numTracks;
      for (String key : overviewStore.keySet()) {
        List<QuantizedLoad> loads = overviewStore.get(key);
        for (int i = 0; i < loads.size(); i++) {
          double xStart =  Math.floor(timeScale.timeToPx(loads.get(i).startSec));
          double xEnd = Math.ceil(timeScale.timeToPx(loads.get(i).endSec));
          double yOff = Math.floor(HEADER_HEIGHT + y * trackHeight);
          float lightness = Math.max(0, (float)(1 - loads.get(i).load * 0.7));
          ctx.gc.setBackground(ctx.colors.get(hueForCpu(y), .5f, lightness));
          ctx.gc.fillRectangle((int)xStart, (int)yOff, (int)(xEnd - xStart), (int)Math.ceil(trackHeight));
        }
        y++;
      }
    }

    // Draw bottom border.
    ctx.gc.setBackground(ctx.colors.get(219f, .4f, .5f));
    ctx.gc.fillRectangle(0, HEADER_HEIGHT + TRACK_HEIGHT - 2, width, 2);

    // Draw semi-opaque rects that occlude the non-visible time range.
    TimeSpan vizTime = feGlobals().getFrontendLocalState().visibleWindowTime;
    double vizStartPx = timeScale.timeToPx(vizTime.start);
    double vizEndPx = timeScale.timeToPx(vizTime.end);

    ctx.withAlpha(.8f, () -> {
      ctx.gc.setBackground(ctx.colors.get(200, 200, 200));
      ctx.gc.fillRectangle(0, HEADER_HEIGHT, (int)vizStartPx, TRACK_HEIGHT);
      ctx.gc.fillRectangle((int)vizEndPx, HEADER_HEIGHT, (int)(width - vizEndPx), TRACK_HEIGHT);
    });

    // Draw brushes.
    int y = HEADER_HEIGHT + (TRACK_HEIGHT - HANDLE_HEIGHT) / 2;
    ctx.gc.setBackground(ctx.colors.get(0x33, 0x33, 0x33));
    ctx.gc.fillRectangle((int)vizStartPx, HEADER_HEIGHT, 1, TRACK_HEIGHT);
    ctx.gc.fillRectangle((int)vizEndPx, HEADER_HEIGHT, 1, TRACK_HEIGHT);
    ctx.gc.fillRectangle((int)vizStartPx - HANDLE_WIDTH, y, HANDLE_WIDTH, HANDLE_HEIGHT);
    ctx.gc.fillRectangle((int)vizEndPx + 1, y, HANDLE_WIDTH, HANDLE_HEIGHT);
  }

  @Override
  public int getHeight() {
    return HEADER_HEIGHT + TRACK_HEIGHT;
  }

  @Override
  public Hover onMouseMove(int startX, int startY) {
    TimeScale timeScale = feGlobals().getFrontendLocalState().totalTimeScale;
    TimeSpan vizTime = feGlobals().getFrontendLocalState().visibleWindowTime;
    int vizStartPx = (int)timeScale.timeToPx(vizTime.start);
    int vizEndPx = (int)timeScale.timeToPx(vizTime.end);
    if (startX >= vizStartPx - HANDLE_WIDTH && startX <= vizStartPx) {
      return Hover.cursor(SWT.CURSOR_SIZEW);
    } else if (startX >= vizEndPx && startX <= vizEndPx + HANDLE_WIDTH) {
      return Hover.cursor(SWT.CURSOR_SIZEE);
    }
    return Hover.cursor(SWT.CURSOR_IBEAM);
  }

  @Override
  public Dragger onDragStart(int startX, int startY) {
    TimeScale timeScale = feGlobals().getFrontendLocalState().totalTimeScale;
    TimeSpan vizTime = feGlobals().getFrontendLocalState().visibleWindowTime;
    int vizStartPx = (int)timeScale.timeToPx(vizTime.start);
    int vizEndPx = (int)timeScale.timeToPx(vizTime.end);
    if (startX >= vizStartPx - HANDLE_WIDTH && startX <= vizStartPx) {
      return handleDragger(0, vizStartPx, vizEndPx);
    } else if (startX >= vizEndPx && startX <= vizEndPx + HANDLE_WIDTH) {
      return handleDragger(1, vizStartPx, vizEndPx);
    }

    return new Dragger() {
      @Override
      public boolean onDrag(Point dragStart, int x, int y) {
        double start = timeScale.pxToTime(dragStart.x);
        double end = timeScale.pxToTime(x);
        if (end < start) {
          feGlobals().getFrontendLocalState().updateVisibleTime(new TimeSpan(end, start));
        } else if (end > start) {
          feGlobals().getFrontendLocalState().updateVisibleTime(new TimeSpan(start, end));
        }
        return true;
      }

      @Override
      public boolean onDragEnd(Point dragStart, int x, int y) {
        return onDrag(dragStart, x, y);
      }

      @Override
      public Cursor getCursor(Display display) {
        return display.getSystemCursor(SWT.CURSOR_IBEAM);
      }
    };
  }

  private static Dragger handleDragger(int handle, int vizStart, int vizEnd) {
    return new Dragger() {
      private int currentHandle = handle;
      private int start = vizStart, end = vizEnd;

      @Override
      public boolean onDrag(Point dragStart, int x, int y) {
        int delta = x - dragStart.x;
        if (currentHandle == 0 && start + delta > end) {
          currentHandle = 1;
          int t = end;
          end = start;
          start = t;
        } else if (currentHandle == 1 && end + delta < start) {
          currentHandle = 0;
          int t = start;
          start = end;
          end = t;
        }

        TimeScale timeScale = feGlobals().getFrontendLocalState().totalTimeScale;
        double s = timeScale.pxToTime((currentHandle == 0) ? start + delta : start);
        double e = timeScale.pxToTime((currentHandle == 1) ? end + delta : end);
        if (e < s) {
          feGlobals().getFrontendLocalState().updateVisibleTime(new TimeSpan(e, s));
        } else if (e > s) {
          feGlobals().getFrontendLocalState().updateVisibleTime(new TimeSpan(s, e));
        }
        return true;
      }

      @Override
      public boolean onDragEnd(Point dragStart, int x, int y) {
        return false;
      }

      @Override
      public Cursor getCursor(Display display) {
        return display.getSystemCursor((currentHandle == 0) ? SWT.CURSOR_SIZEW : SWT.CURSOR_SIZEE);
      }
    };
  }
}
