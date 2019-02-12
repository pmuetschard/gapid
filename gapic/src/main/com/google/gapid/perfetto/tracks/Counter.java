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

import static com.google.gapid.perfetto.base.Logging.assertTrue;
import static com.google.gapid.perfetto.common.TimeSpan.fromNs;
import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;
import static com.google.gapid.perfetto.frontend.Checkerboard.checkerboardExcept;
import static com.google.gapid.perfetto.frontend.FrontEndGlobals.feGlobals;
import static com.google.gapid.perfetto.frontend.RenderContext.Style.Fill;
import static com.google.gapid.perfetto.frontend.RenderContext.Style.Stroke;
import static com.google.gapid.perfetto.frontend.RenderContext.Style.StrokeFill;
import static com.google.gapid.util.Colors.hsl;
import static com.google.gapid.util.MoreFutures.logFailure;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.base.BinarySearch;
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

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;

import java.util.List;
import java.util.logging.Logger;

public class Counter {
  protected static Logger LOG = Logger.getLogger(Counter.class.getName());

  public static final String COUNTER_TRACK_KIND = "CounterTrack";

  public static class Data {
    public final double start;
    public final double end;
    public final double resolution;
    public final double maxiumValue;
    public final double minimumValue;
    public final double[] timestamps;
    public final double[] values;

    public Data(double start, double end, double resolution, double maxiumValue,
        double minimumValue, double[] timestamps, double[] values) {
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.maxiumValue = maxiumValue;
      this.minimumValue = minimumValue;
      this.timestamps = timestamps;
      this.values = values;
    }
  }

  public static class Config {
    public final String name;
    public final double maximumValue;
    public final double minimumValue;
    public final int ref;

    public Config(String name, int ref) {
      this(name, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, ref);
    }

    public Config(String name, double maximumValue, double minimumValue, int ref) {
      this.name = name;
      this.maximumValue = maximumValue;
      this.minimumValue = minimumValue;
      this.ref = ref;
    }
  }

  public static class Controller extends TrackController<Config, Data> {
    private final Engine engine;
    private boolean busy = false;
    private boolean setup = false;
    private double maximumValueSeen = Double.NEGATIVE_INFINITY;
    private double minimumValueSeen = Double.POSITIVE_INFINITY;

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

      logFailure(LOG, transformAsync(setup(), $1 ->
        transformAsync(update(start, end, resolution), data ->
          cGlobals().onUiThread(() -> {
            publish(data);
            busy = false;
          }))));
    }

    private ListenableFuture<Void> setup() {
      if (setup) {
        return Futures.immediateFuture(null);
      }
      String query = "select max(value), min(value) from " +
          "counters where name = '" + getConfig().name + "' " +
          "and ref = " + getConfig().ref;
      return transformAsync(query(query), result -> {
        return cGlobals().onUiThread(() -> {
          maximumValueSeen = result.getColumns(0).getDoubleValues(0);
          minimumValueSeen = result.getColumns(1).getDoubleValues(0);
          setup = true;
        });
      });
    }

    private ListenableFuture<Data> update(double start, double end, double resolution) {
      long startNs = Math.round(start * 1e9);
      long endNs = Math.round(end * 1e9);
      String query = "select ts, value from counters" +
          " where " + startNs + " <= ts_end and ts <= " + endNs +
          " and name = '" + getConfig().name + "' and ref = " + getConfig().ref;
      return transform(query(query), rawResult -> {
        int numRows = (int)rawResult.getNumRecords();
        Data data = new Data(start, end, resolution, maximumValue(), minimumValue(),
            new double[numRows], new double[numRows]);
        List<Service.PerfettoQueryResult.ColumnValues> cols = rawResult.getColumnsList();
        for (int row = 0; row < numRows; row++) {
          double startSec = fromNs(cols.get(0).getLongValues(row));
          double value = cols.get(1).getDoubleValues(row);
          data.timestamps[row] = startSec;
          data.values[row] = value;
        }
        return data;
      });
    }

    private double maximumValue() {
      return Math.max(getConfig().maximumValue, maximumValueSeen);
    }

    private double minimumValue() {
      return Math.min(getConfig().minimumValue, minimumValueSeen);
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

  public static class CounterTrack extends Track<Config, Data> {
    private static final float MARGIN_TOP = 4.5f;
    private static final float RECT_HEIGHT = 30;

    private boolean reqPending = false;
    private int mouseXpos = 0;
    private Double hoveredValue = null;
    private Double hoveredTs = null;
    private Double hoveredTsEnd = null;

    public CounterTrack(TrackState trackState) {
      super(trackState);
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

      assertTrue(data.timestamps.length == data.values.length);

      double startPx = timeScale.timeToPx(visibleWindowTime.start);
      double endPx = timeScale.timeToPx(visibleWindowTime.end);
      double zeroY = MARGIN_TOP + RECT_HEIGHT / (data.minimumValue < 0 ? 2 : 1);

      // Quantize the Y axis to quarters of powers of tens (7.5K, 10K, 12.5K).
      double maxValue = Math.max(data.maxiumValue, 0);

      double yMax = Math.max(Math.abs(data.minimumValue), maxValue);
      final String[] kUnits = new String[] { "", "K", "M", "G", "T", "E" };
      double exp = Math.ceil(Math.log10(Math.max(yMax, 1)));
      double pow10 = Math.pow(10, exp);
      yMax = Math.ceil(yMax / (pow10 / 4)) * (pow10 / 4);
      double yRange = data.minimumValue < 0 ? yMax * 2 : yMax;
      int unitGroup = (int)Math.floor(exp / 3);
      String yLabel = (yMax / Math.pow(10, unitGroup * 3)) + " " + kUnits[unitGroup];
      // There are 360deg of hue. We want a scale that starts at green with
      // exp <= 3 (<= 1KB), goes orange around exp = 6 (~1MB) and red/violet
      // around exp >= 9 (1GB).
      // The hue scale looks like this:
      // 0                              180                                 360
      // Red        orange         green | blue         purple          magenta
      // So we want to start @ 180deg with pow=0, go down to 0deg and then wrap
      // back from 360deg back to 180deg.
      double expCapped = Math.min(Math.max(0, exp - 3), 9);
      float hue = (180 - (float)Math.floor(expCapped * (180 / 6)) + 360) % 360;

      ctx.setColor(hsl(hue, .45f, .45f), hsl(hue, .45f, .45f));
      ctx.path(StrokeFill, path -> {
        double lastX = startPx, lastY = zeroY;
        path.moveTo((float)lastX, (float)lastY);
        for (int i = 0; i < data.values.length; i++) {
          double value = data.values[i];
          double startTime = data.timestamps[i];
          double nextY = zeroY - Math.round((value / yRange) * RECT_HEIGHT);
          if (nextY == lastY) {
            continue;
          }

          lastX = Math.floor(timeScale.timeToPx(startTime));
          path.lineTo((float)lastX, (float)lastY);
          path.lineTo((float)lastX, (float)nextY);
          lastY = nextY;
        }
        path.lineTo((float)endPx, (float)lastY);
        path.lineTo((float)endPx, (float)zeroY);
        path.close();
      });

      ctx.setColor(hsl(hue, 0.1f, 0.15f), null);
      ctx.withLineDash(new int[] { 2, 4 }, () ->
        ctx.path(Style.Stroke, path -> {
          path.moveTo(0, (float)zeroY);
          path.lineTo((float)endPx, (float)zeroY);
          path.close();
        }));

      // TS2J: ctx.font = '10px Google Sans';

      if (hoveredValue != null && hoveredTs != null) {
        // TODO(hjd): Add units.
        String text = "value: " + hoveredValue;
        int width = ctx.textExtent(text).x;

        ctx.setColor(hsl(hue, .45f, .45f), hsl(hue, .45f, .75f));
        double xStart = Math.floor(timeScale.timeToPx(hoveredTs));
        double xEnd = hoveredTsEnd == null ? endPx : Math.floor(timeScale.timeToPx(hoveredTsEnd));
        double y = zeroY - Math.round((hoveredValue / yRange) * RECT_HEIGHT);

        // Highlight line.
        ctx.withLineWidth(3, () ->
          ctx.path(Stroke, path -> {
            path.moveTo((float)xStart, (float)y);
            path.lineTo((float)xEnd, (float)y);
          }));

        // Draw change marker.
        ctx.path(StrokeFill, path -> {
          path.addArc((float)xStart, (float)y, 3, 3, 0, 360);
        });

        // Draw the tooltip.
        ctx.setColor(hsl(200f, .5f, .4f), new RGB(0xff, 0xff, 0xff));
        ctx.withAlpha(.8f, () -> {
          ctx.drawRectangle(Fill, mouseXpos + 5, MARGIN_TOP, width + 16, RECT_HEIGHT);
        });
        ctx.drawText(text, mouseXpos + 8, MARGIN_TOP + RECT_HEIGHT / 2 - 5);
      }

      // Write the Y scale on the top left corner.
      ctx.setColor(new RGB(0x66, 0x66, 0x66), new RGB(0xff, 0xff, 0xff));
      ctx.withAlpha(.6f, () -> {
        ctx.drawRectangle(Fill, 0, 0, 40, 16);
      });
      ctx.drawText(yLabel, 5, 3);

      checkerboardExcept(ctx,
          (int)timeScale.timeToPx(visibleWindowTime.start),
          (int)timeScale.timeToPx(visibleWindowTime.end),
          (int)timeScale.timeToPx(data.start),
          (int)timeScale.timeToPx(data.end));
    }

    @Override
    public boolean onMouseMove(int x, int y) {
      Data data = getData();
      if (data == null) {
        return false;
      }
      mouseXpos = x;
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      double time = timeScale.pxToTime(x);

      Point xy = BinarySearch.searchSegment(data.timestamps, time);
      hoveredTs = xy.x == -1 ? null : data.timestamps[xy.x];
      hoveredTsEnd = xy.y == -1 ? null : data.timestamps[xy.y];
      hoveredValue = xy.x == -1 ? null : data.values[xy.x];
      return true;
    }

    @Override
    public void onMouseOut() {
      hoveredValue = null;
      hoveredTs = null;
    }
  }

  public static void init() {
    TrackController.register(COUNTER_TRACK_KIND, Controller::new);
    Track.register(COUNTER_TRACK_KIND, CounterTrack::new);
  }
}
