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
import static com.google.gapid.perfetto.frontend.TrackPanel.TRACK_SHELL_WIDTH;
import static com.google.gapid.widgets.Widgets.withLayoutData;
import static com.google.gapid.widgets.Widgets.withMargin;

import com.google.common.collect.Lists;
import com.google.gapid.perfetto.common.State;
import com.google.gapid.perfetto.common.State.TrackGroupState;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.widgets.Theme;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;

import java.util.Collections;
import java.util.List;

public class ViewerPage extends Composite {
  private final Theme theme;
  private final PanelCanvas topCanvas;
  private final PanelCanvas bottomCanvas;
  private final Panel.Group topRoot;
  private final Panel.Group bottomRoot;
  private final Tracks tracks;

  private static final double ZOOM_FACTOR_SCALE = 0.02;
  private static final double MAX_ZOOM_SPAN_SEC = 1e-4;  // 0.1 ms

  public ViewerPage(Composite parent, Theme theme) {
    super(parent, SWT.NONE);
    this.theme = theme;
    this.topRoot = new Panel.Group();
    this.bottomRoot = new Panel.Group();
    this.tracks = new Tracks();
    ColorCache colorCache = new ColorCache(getDisplay());

    setLayout(withMargin(new GridLayout(1, false), 0, 0));
    topCanvas = withLayoutData(new PanelCanvas(this, colorCache, topRoot),
        new GridData(SWT.FILL, SWT.TOP, true, false));
    bottomCanvas = withLayoutData(new PanelCanvas(this, colorCache, bottomRoot),
        new GridData(SWT.FILL, SWT.FILL, true, true));

    addListener(SWT.Resize, e -> {
      Rectangle size = getClientArea();
      FrontendLocalState state = feGlobals().getFrontendLocalState();
      state.timeScaleTop.setLimitsPx(0, size.width);
      state.timeScaleTrack.setLimitsPx(0, size.width - TRACK_SHELL_WIDTH);
      state.totalTimeScale.setLimitsPx(0, size.width);
    });
    Listener zoomListener = e -> {
      if ((e.stateMask & SWT.MODIFIER_MASK) == SWT.MOD1) {
        e.doit = false;
        double zoomFactor = e.count * ZOOM_FACTOR_SCALE;
        TimeSpan vizTime = feGlobals().getFrontendLocalState().visibleWindowTime;
        TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;

        double cursorTime = timeScale.pxToTime(e.x - TRACK_SHELL_WIDTH);
        double curSpanSec = vizTime.getDuration();
        double newSpanSec = Math.max(curSpanSec - curSpanSec * zoomFactor, MAX_ZOOM_SPAN_SEC);
        double newStartSec = cursorTime - (newSpanSec / curSpanSec) * (cursorTime - vizTime.start);
        double newEndSec = newStartSec + newSpanSec;
        feGlobals().getFrontendLocalState().updateVisibleTime(new TimeSpan(newStartSec,  newEndSec));
      }
    };
    topCanvas.addListener(SWT.MouseWheel, zoomListener);
    bottomCanvas.addListener(SWT.MouseWheel, zoomListener);

    addListener(SWT.Dispose, e -> {
      colorCache.dispose();
    });
  }

  public void updateUi() {
    State state = feGlobals().getState();
    // TODO: optimize
    topRoot.clear();
    topRoot.add(new OverviewTimelinePanel());
    topRoot.add(new TimeAxisPanel());
    for (String track : state.pinnedTracks) {
      topRoot.add(new TrackPanel(theme, tracks, track));
    }
    topRoot.add(new LinePanel());

    bottomRoot.clear();
    bottomRoot.add(new TitlePanel("Tracks"));
    for (String track : state.scrollingTracks) {
      bottomRoot.add(new TrackPanel(theme, tracks, track));
    }
    List<TrackGroupState> groups = Lists.newArrayList(state.trackGroups.values());
    Collections.sort(groups, (g1, g2) -> g1.name.compareTo(g2.name));
    for (TrackGroupState group : groups) {
      bottomRoot.add(new TrackGroupPanel(theme, tracks, group.id));
    }
    topCanvas.layout(true);
    bottomCanvas.layout(true);
    layout(true);
    scheduleRedraw();
  }

  public void scheduleRedraw() {
    topCanvas.redraw();
    bottomCanvas.redraw();
  }
}
