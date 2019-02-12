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
package com.google.gapid.perfetto.tracks;

import static com.google.gapid.perfetto.common.TimeSpan.fromNs;
import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;
import static com.google.gapid.perfetto.frontend.Checkerboard.checkerboardExcept;
import static com.google.gapid.perfetto.frontend.FrontEndGlobals.feGlobals;
import static com.google.gapid.util.Colors.hsl;
import static com.google.gapid.util.MoreFutures.logFailure;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.State.TrackState;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.controller.TrackController;
import com.google.gapid.perfetto.frontend.RenderContext;
import com.google.gapid.perfetto.frontend.RenderContext.Style;
import com.google.gapid.perfetto.frontend.TimeScale;
import com.google.gapid.perfetto.frontend.Track;
import com.google.gapid.proto.service.Service;

import org.eclipse.swt.graphics.RGB;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ChromeSlices {
  protected static Logger LOG = Logger.getLogger(ChromeSlices.class.getName());

  public static final String SLICE_TRACK_KIND = "ChromeSliceTrack";

  public static class Data {
    public final double start;
    public final double end;
    public final double resolution;
    // Slices are stored in a columnar fashion. All fields have the same length.
    public final String[] strings;
    public final double[] starts;
    public final double[] ends;
    public final int[] depths;  // Index in |strings|.
    public final int[] titles;  // Index in |strings|.
    public final int[] categories;  // Index in |strings|.

    public Data(double start, double end, double resolution, String[] strings, double[] starts,
        double[] ends, int[] depths, int[] titles, int[] categories) {
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.strings = strings;
      this.starts = starts;
      this.ends = ends;
      this.depths = depths;
      this.titles = titles;
      this.categories = categories;
    }
  }

  public static class Config {
    public final int maxDepth;
    public final int upid;
    public final int utid;

    public Config(int maxDepth, int upid, int utid) {
      this.maxDepth = maxDepth;
      this.upid = upid;
      this.utid = utid;
    }
  }

  public static class Controller extends TrackController<Config, Data> {
    private final Engine engine;
    private boolean busy = false;

    public Controller(TrackControllerArgs args) {
      super(args);
      this.engine = args.engine;
    }

    @Override
    protected void onBoundsChange(double start, double end, double resolution) {
      if (busy) {
        return;
      }
      busy = true;
      logFailure(LOG, transformAsync(update(start, end, resolution), data ->
        cGlobals().onUiThread(() -> {
          publish(data);
          busy = false;
        })));
    }

    private ListenableFuture<Data> update(double start, double end, double resolution) {
      final int LIMIT = 10000;
      // TODO: "ts >= x - dur" below is inefficient because doesn't allow to use
      // any index. We need to introduce ts_lower_bound also for the slices table
      // (see sched table).
      String query = "select ts,dur,depth,cat,name from slices" +
          " where utid = " + getConfig().utid +
          " and ts >= " + Math.round(start * 1e9) + " - dur" +
          " and ts <= " + Math.round(end * 1e9) +
          " and dur >= " + Math.round(resolution * 1e9) +
          " order by ts" +
          " limit " + LIMIT;
      return transform(query(query), rawResult -> {
        int numRows = (int)rawResult.getNumRecords();
        double[] starts = new double[numRows];
        double[] ends = new double[numRows];
        int[] depths = new int[numRows];
        int[] titles = new int[numRows];
        int[] categories = new int[numRows];
        Map<String, Integer> strMap = Maps.newHashMap();
        List<String> strList = Lists.newArrayList();
        List<Service.PerfettoQueryResult.ColumnValues> cols = rawResult.getColumnsList();
        for (int row = 0; row < numRows; row++) {
          double startSec = fromNs(cols.get(0).getLongValues(row));
          starts[row] = startSec;
          ends[row] = startSec + fromNs(cols.get(1).getLongValues(row));
          depths[row] = (int)cols.get(2).getLongValues(row);
          categories[row] = strIntern(strMap, strList, cols.get(3).getStringValues(row));
          titles[row] = strIntern(strMap, strList, cols.get(4).getStringValues(row));
        }
        double fixedEnd = (numRows == LIMIT) ? ends[ends.length - 1] : end;
        return new Data(start, fixedEnd, resolution, strList.toArray(new String[strList.size()]),
            starts, ends, depths, titles, categories);
      });
    }

    private static int strIntern(Map<String, Integer> strMap, List<String> strList, String s) {
      Integer result = strMap.get(s);
      if (result == null) {
        result = strList.size();
        strList.add(s);
      }
      return result;
    }

    private ListenableFuture<Service.PerfettoQueryResult> query(String query) {
      return transform(engine.query(query), result -> {
        if (!result.getError().isEmpty()) {
          throw new RuntimeException("Query error: " + result.getError());
        }
        return result;
      });
    }
  }

  public static class ChromeTrack extends Track<Config, Data> {
    private static final int SLICE_HEIGHT = 20;
    private static final int TRACK_PADDING = 5;

    private int hoveredTitleId = -1;
    private boolean reqPending = false;

    public ChromeTrack(TrackState trackState) {
      super(trackState);
    }

    private static int hash(String s) {
      int hash = 0x811c9dc5;
      for (int i = 0; i < s.length(); i++) {
        hash ^= s.charAt(i);
        hash *= 16777619;
      }
      return hash & 0xFF;
    }

    private static double getCurResolution() {
      // Truncate the resolution to the closest power of 10.
      double resolution = feGlobals().getFrontendLocalState().timeScaleTrack.deltaPxToDuration(1);
      return Math.pow(10, Math.floor(Math.log10(resolution)));
    }

    private void reqDataDeferred() {
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      double reqStart = visibleWindowTime.start - visibleWindowTime.getDuration();
      double reqEnd = visibleWindowTime.end + visibleWindowTime.getDuration();
      double reqResolution = getCurResolution();
      reqPending = false;
      feGlobals().dispatch(Actions.reqTrackData(trackState.id, reqStart, reqEnd, reqResolution));
    }

    @Override
    public void renderCanvas(RenderContext ctx) {
      // TODO: fonts and colors should come from the CSS and not hardcoded here.

      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      Data data = getData();

      boolean inRange = data != null && visibleWindowTime.start >= data.start &&
          visibleWindowTime.end <= data.end;
      if (!inRange || data.resolution != getCurResolution()) {
        if (!reqPending) {
          reqPending = true;
          feGlobals().onUiThread(50, this::reqDataDeferred);
        }
      }
      if (data == null) {
        return;
      }

      checkerboardExcept(ctx,
          (int)timeScale.timeToPx(visibleWindowTime.start),
          (int)timeScale.timeToPx(visibleWindowTime.end),
          (int)timeScale.timeToPx(data.start),
          (int)timeScale.timeToPx(data.end));

      // TS2J: ctx.font = '12px Google Sans';
      // TS2J: ctx.textAlign = 'center';

      // TS2J: really? // measuretext is expensive so we only use it once.
      float charWidth = ctx.textExtent("abcdefghij").x / 10.0f;
      float pxEnd = (float)timeScale.timeToPx(visibleWindowTime.end);

      for (int i = 0; i < data.starts.length; i++) {
        double tStart = data.starts[i];
        double tEnd = data.ends[i];
        int depth = data.depths[i];
        String cat = data.strings[data.categories[i]];
        int titleId = data.titles[i];
        String title = data.strings[titleId];
        if (tEnd <= visibleWindowTime.start || tStart >= visibleWindowTime.end) {
          continue;
        }
        float rectXStart = Math.max((float)timeScale.timeToPx(tStart), 0);
        float rectXEnd = Math.min((float)timeScale.timeToPx(tEnd), pxEnd);
        float rectWidth = rectXEnd - rectXStart;
        if (rectWidth < /*TS2J: 0.1*/ 1) {
          continue;
        }
        int rectYStart = TRACK_PADDING + depth * SLICE_HEIGHT;

        boolean hovered = titleId == hoveredTitleId;
        float hue = hash(cat);
        float saturation = Math.min(20 + depth * 10, 70) / 100f;
        ctx.setColor(new RGB(0xff, 0xff, 0xff), hsl(hue, saturation, hovered ? .3f : .65f));

        ctx.drawRectangle(Style.Fill, rectXStart, rectYStart, rectWidth, SLICE_HEIGHT);

        float nameLength = title.length() * charWidth;
        float maxTextWidth = rectWidth - 15;
        String displayText = "";
        if (nameLength < maxTextWidth) {
          displayText = title.trim();
        } else {
          // -3 for the 3 ellipsis.
          int displayedChars = (int)Math.floor(maxTextWidth / charWidth) - 3;
          if (displayedChars > 3) {
            displayText = title.substring(0, displayedChars) + "...";
            nameLength = maxTextWidth;
          }
        }

        if (!displayText.isEmpty()) {
          // TS2J: double rectXCenter = rectXStart + rectWidth / 2;
          // TS2J: ctx.fillText(displayText, rectXCenter, rectYStart + SLICE_HEIGHT / 2);
          ctx.drawText(displayText,
              (rectXStart + (maxTextWidth - nameLength) / 2), rectYStart + SLICE_HEIGHT / 2 - 8);
        }
      }
    }

    @Override
    public boolean onMouseMove(int x, int y) {
      Data data = getData();
      hoveredTitleId = -1;
      if (data == null) {
        return false;
      }
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      if (y < TRACK_PADDING) {
        return false;
      }
      double t = timeScale.pxToTime(x);
      int depth = (int)Math.floor(y / SLICE_HEIGHT);
      for (int i =  0; i < data.starts.length; i++) {
        double tStart = data.starts[i];
        double tEnd = data.ends[i];
        int titleId = data.titles[i];
        if (tStart <= t && t <= tEnd && depth == data.depths[i]) {
          hoveredTitleId = titleId;
          return true;
        }
      }
      return false;
    }

    @Override
    public void onMouseOut() {
      hoveredTitleId = -1;
    }

    @Override
    public int getHeight() {
      return SLICE_HEIGHT * (getConfig().maxDepth + 1) + 2 * TRACK_PADDING;
    }
  }


  public static void init() {
    TrackController.register(SLICE_TRACK_KIND, Controller::new);
    Track.register(SLICE_TRACK_KIND, ChromeTrack::new);
  }
}
