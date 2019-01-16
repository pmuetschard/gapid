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
import static com.google.gapid.perfetto.frontend.GridLines.drawGridLines;
import static com.google.gapid.perfetto.frontend.Panel.Hover.redrawIf;

import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.widgets.Theme;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

public class TrackPanel implements Panel {
  public static final int TRACK_SHELL_WIDTH = 250;
  private static final int BUTTON_SIZE = 24;
  private static final int BUTTON_MARGIN = 8;

  private final Tracks tracks;
  private final String trackId;
  private final Button pinButton;
  private boolean buttonVisible = false;

  public TrackPanel(Theme theme, Tracks tracks, String trackId) {
    this.tracks = tracks;
    this.trackId = trackId;
    boolean pinned = isPinned(trackId);
    this.pinButton = new Button(
        TRACK_SHELL_WIDTH - BUTTON_SIZE - BUTTON_MARGIN, (getHeight() - BUTTON_SIZE) / 2,
        BUTTON_SIZE, BUTTON_SIZE,
        pinned ? theme.perfettoPinned() : theme.perfettoPin(),
        pinned ? theme.perfettoPin() : theme.perfettoPinned(),
        () -> feGlobals().dispatch(Actions.toggleTrackPinned(trackId)));
  }

  private static boolean isPinned(String trackId) {
    return feGlobals().getState().pinnedTracks.contains(trackId);
  }

  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    Track<?, ?> track = tracks.get(trackId);
    int height = track.getHeight();

    ctx.gc.setForeground(ctx.colors.get(0x3c, 0x4b, 0x5d));
    ctx.withClip(0, 0, TRACK_SHELL_WIDTH, height, () ->
      ctx.gc.drawText(track.getTitle(), 10, 5, SWT.DRAW_TRANSPARENT));

    ctx.gc.setForeground(ctx.colors.get(0xda, 0xda, 0xda));
    ctx.gc.drawLine(TRACK_SHELL_WIDTH - 1, 0, TRACK_SHELL_WIDTH - 1, height);

    if (buttonVisible) {
      pinButton.renderCanvas(ctx);
    }

    ctx.withTranslation(TRACK_SHELL_WIDTH, 0, () ->
      ctx.withClip(0, 0, width - TRACK_SHELL_WIDTH, height, () -> {
        drawGridLines(ctx,
            feGlobals().getFrontendLocalState().timeScaleTrack,
            feGlobals().getFrontendLocalState().visibleWindowTime,
            height);
        track.renderCanvas(ctx);
      }));
  }

  @Override
  public int getHeight() {
    return tracks.get(trackId).getHeight();
  }

  @Override
  public Dragger onDragStart(int x, int y) {
    if (x >= TRACK_SHELL_WIDTH) {
      return new TrackDragger();
    }
    return Dragger.NONE;
  }

  @Override
  public Hover onMouseMove(int x, int y) {
    Track<?, ?> track = tracks.get(trackId);
    if (x < TRACK_SHELL_WIDTH) {
      track.onMouseOut();

      boolean result = !buttonVisible;
      buttonVisible = true;
      return pinButton.onMouseMove(x, y).or(redrawIf(result));
    } else {
      boolean result = buttonVisible;
      buttonVisible = false;
      pinButton.onMouseOut();

      return redrawIf(track.onMouseMove(x - TRACK_SHELL_WIDTH, y) || result);
    }
  }

  @Override
  public boolean onMouseOut() {
    boolean result = buttonVisible;
    buttonVisible = false;
    pinButton.onMouseOut();
    tracks.get(trackId).onMouseOut();
    return result;
  }

  public static class TrackDragger implements Dragger {
    private final TimeSpan atStart = feGlobals().getFrontendLocalState().visibleWindowTime;

    public TrackDragger() {
    }

    @Override
    public boolean onDrag(Point dragStart, int x, int y) {
      feGlobals().getFrontendLocalState().drag(atStart, x - dragStart.x, false);
      return true;
    }

    @Override
    public boolean onDragEnd(Point dragStart, int x, int y) {
      return onDrag(dragStart, x, y);
    }

    @Override
    public Cursor getCursor(Display display) {
      return display.getSystemCursor(SWT.CURSOR_SIZEWE);
    }
  }
}
