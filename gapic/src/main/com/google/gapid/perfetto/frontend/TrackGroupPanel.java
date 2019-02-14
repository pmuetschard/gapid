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
import static com.google.gapid.perfetto.frontend.GridLines.drawGridLines;
import static com.google.gapid.perfetto.frontend.Panel.Hover.redrawIf;
import static com.google.gapid.perfetto.frontend.TrackPanel.TRACK_SHELL_WIDTH;
import static com.google.gapid.skia.RenderContext.Style.Fill;
import static com.google.gapid.util.Colors.hsla;

import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.State.TrackGroupState;
import com.google.gapid.perfetto.frontend.TrackPanel.TrackDragger;
import com.google.gapid.skia.RenderContext;
import com.google.gapid.widgets.Theme;

import org.eclipse.swt.graphics.RGBA;

public class TrackGroupPanel implements Panel {
  private static final int TITLE_HEIGHT = 40;
  private static final int BUTTON_SIZE = 24;
  private static final int BUTTON_MARGIN = 8;

  private final Tracks tracks;
  private final String trackGroupId;
  private final Panel.Group children;
  private final Button collapseButton;
  private final Button expandButton;

  public TrackGroupPanel(Theme theme, Tracks tracks, String trackGroupId) {
    this.tracks = tracks;
    this.trackGroupId = trackGroupId;
    this.children = new Panel.Group();
    for (String track : getState().tracks) {
      children.add(new TrackPanel(theme, tracks, track));
    }
    this.collapseButton = new Button(
        TRACK_SHELL_WIDTH - BUTTON_SIZE - BUTTON_MARGIN, (TITLE_HEIGHT - BUTTON_SIZE) / 2,
        BUTTON_SIZE, BUTTON_SIZE, theme.perfettoCollapse(), theme.perfettoCollapseHovered(),
        () -> feGlobals().dispatch(Actions.toggleTrackGroupCollapsed(trackGroupId)));
    this.expandButton = new Button(
        TRACK_SHELL_WIDTH - BUTTON_SIZE - BUTTON_MARGIN, (TITLE_HEIGHT - BUTTON_SIZE) / 2,
        BUTTON_SIZE, BUTTON_SIZE, theme.perfettoExpand(), theme.perfettoExpandHovered(),
        () -> feGlobals().dispatch(Actions.toggleTrackGroupCollapsed(trackGroupId)));
  }

  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    TrackGroupState state = getState();
    int height = getHeight();
    if (state.collapsed) {
      ctx.setColor(new RGBA(0x12, 0x12, 0x12, 0xff), hsla(190f, .49f, .97f, 255));
      ctx.drawRectangle(Fill, 0, 0, width, height);
      ctx.withClip(0, 0, TRACK_SHELL_WIDTH - 2 * BUTTON_MARGIN - BUTTON_SIZE, height, () ->
        ctx.drawText(state.name, 5, 10));

      ctx.setColor(new RGBA(0xda, 0xda, 0xda, 0xFF), null);
      ctx.drawLine(TRACK_SHELL_WIDTH - 1, 0, TRACK_SHELL_WIDTH - 1, height);

      expandButton.renderCanvas(ctx);

      ctx.withTranslation(TRACK_SHELL_WIDTH, 0, () ->
        ctx.withClip(0, 0, width - TRACK_SHELL_WIDTH, height, () -> {
          drawGridLines(ctx,
              feGlobals().getFrontendLocalState().timeScaleTrack,
              feGlobals().getFrontendLocalState().visibleWindowTime,
              height);
          tracks.get(state.summaryTrackId).renderCanvas(ctx);
        }));
      return;
    }

    ctx.setColor(new RGBA(0xff, 0xff, 0xff, 0xff), hsla(215f, .22f, .19f, 255));
    ctx.drawRectangle(Fill, 0, 0, width, TITLE_HEIGHT);
    ctx.withClip(0, 0, TRACK_SHELL_WIDTH - 2 * BUTTON_MARGIN - BUTTON_SIZE, height, () ->
      ctx.drawText(state.name, 5, 10));
    collapseButton.renderCanvas(ctx);

    //ctx.gc.setForeground(ctx.colors.get(0xda, 0xda, 0xda));
    //ctx.gc.drawLine(TRACK_SHELL_WIDTH - 1, 0, TRACK_SHELL_WIDTH - 1, height);

    ctx.withTranslation(0, TITLE_HEIGHT, () -> children.renderCanvas(ctx, width));
  }

  @Override
  public int getHeight() {
    TrackGroupState state = getState();
    if (state.collapsed) {
      return collapsedHeight(state);
    }

    return TITLE_HEIGHT + children.getHeight();
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
    TrackGroupState state = getState();
    if (state.collapsed) {
      if (x >= TRACK_SHELL_WIDTH) {
        boolean redraw = expandButton.onMouseOut();
        return redrawIf(tracks.get(state.summaryTrackId).onMouseMove(x - TRACK_SHELL_WIDTH, y) || redraw);
      } else {
        tracks.get(state.summaryTrackId).onMouseOut();
        return expandButton.onMouseMove(x, y);
      }
    } else {
      if (y >= TITLE_HEIGHT) {
        boolean redraw = collapseButton.onMouseOut();
        return children.onMouseMove(x, y - TITLE_HEIGHT).or(redrawIf(redraw));
      } else {
        return collapseButton.onMouseMove(x, y).or(Hover.redrawIf(children.onMouseOut()));
      }
    }
  }

  @Override
  public boolean onMouseOut() {
    return children.onMouseOut();
  }

  private int collapsedHeight(TrackGroupState state) {
    return Math.max(TITLE_HEIGHT, tracks.get(state.summaryTrackId).getHeight());
  }

  private TrackGroupState getState() {
    return assertExists(feGlobals().getState().trackGroups.get(trackGroupId));
  }
}
