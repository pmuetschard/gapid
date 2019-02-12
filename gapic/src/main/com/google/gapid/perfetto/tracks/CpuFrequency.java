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
import com.google.gapid.perfetto.frontend.TimeScale;
import com.google.gapid.perfetto.frontend.Track;
import com.google.gapid.proto.service.Service;

import org.eclipse.swt.graphics.RGB;

import java.util.List;
import java.util.logging.Logger;

public class CpuFrequency {
  protected static Logger LOG = Logger.getLogger(CpuFrequency.class.getName());

  public static final String CPU_FREQ_TRACK_KIND = "CpuFreqTrack";

  public static class Data {
    public final double start;
    public final double end;
    public final double resolution;
    public final double maximumValue;
    public final double[] tsStarts;
    public final double[] tsEnds;
    public final byte[] idles;
    public final int[] freqKHz;

    public Data(double start, double end, double resolution, double maximumValue, double[] tsStarts,
        double[] tsEnds, byte[] idles, int[] freqKHz) {
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.maximumValue = maximumValue;
      this.tsStarts = tsStarts;
      this.tsEnds = tsEnds;
      this.idles = idles;
      this.freqKHz = freqKHz;
    }
  }

  public static class Config {
    public final int cpu;
    public final double maximumValue;

    public Config(int cpu, double maximumValue) {
      this.cpu = cpu;
      this.maximumValue = maximumValue;
    }
  }

  public static class Controller extends TrackController<Config, Data> {
    private final Engine engine;
    private boolean busy = false;
    private boolean setup = false;
    private double maximumValueSeen = Double.NEGATIVE_INFINITY;

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

      String dropAct = "drop view if exists " + tableName("activity");
      String dropSpanAct = "drop table if exists " + tableName("span_activity");
      String dropFreqIdle = "drop table if exists " + tableName("freq_idle");
      String dropIdle = "drop view if exists " + tableName("idle");
      String dropFreq = "drop view if exists " + tableName("freq");
      String dropWin = "drop table if exists " + tableName("window");
      String createWin = "create virtual table " + tableName("window") + " using window";
      String createFreq = "create view " + tableName("freq") + " as " +
          "select ts,dur,ref as cpu,name as freq_name,value as freq_value " +
          "from counters " +
          "where name = 'cpufreq' and ref = " + getConfig().cpu + " and ref_type = 'cpu'";
      String createIdle = "create view " + tableName("idle") + " as " +
          "select ts,dur,ref as cpu,name as idle_name,value as idle_value " +
          "from counters " +
          "where name = 'cpuidle' and ref = " + getConfig().cpu + " and ref_type = 'cpu'";
      String createFreqIdle = "create virtual table " + tableName("freq_idle") + " using span_join(" +
          tableName("freq") + " PARTITIONED cpu, " + tableName("idle") + " PARTITIONED cpu)";
      String createSpanAct = "create virtual table " + tableName("span_activity") + " using span_join(" +
          tableName("freq_idle") + " PARTITIONED cpu, " + tableName("window") + " PARTITIONED cpu)";
      String createAct = "create view " + tableName("activity") + " as " +
          "select ts,dur,quantum_ts,cpu," +
          "case idle_value when 4294967295 then -1 else idle_value end as idle," +
          "freq_value as freq from " + tableName("span_activity");
      String selectMax = "select max(value) from counters where name = 'cpufreq' and ref = " + getConfig().cpu;
      return transformAsync(query(dropAct, dropSpanAct, dropFreqIdle, dropIdle, dropFreq, dropWin,
          createWin, createFreq, createIdle, createFreqIdle, createSpanAct, createAct), $ ->
        transformAsync(query(selectMax), maxResult ->
          cGlobals().onUiThread(() -> {
            maximumValueSeen = maxResult.getColumns(0).getDoubleValues(0);
            setup = true;
          })));
    }

    private ListenableFuture<Data> update(double start, double end, double resolution) {
      long startNs = Math.round(start * 1e9);
      long endNs = Math.round(end * 1e9);
      long windowDurNs = Math.max(1, endNs - startNs);
      String update = "update " + tableName("window") + " set" +
          " window_start=" + startNs + "," +
          " window_dur=" + windowDurNs + "," +
          " quantum = 0";
      return transformAsync(query(update), $ -> {
        String query = "select ts, dur, cast(idle as DOUBLE), freq from " + tableName("activity");
        return transform(query(query), freqResult -> {
          int numRows = (int)freqResult.getNumRecords();
          Data data = new Data(start, end, resolution, maximumValue(), new double[numRows],
              new double[numRows], new byte[numRows], new int[numRows]);
          List<Service.PerfettoQueryResult.ColumnValues> cols = freqResult.getColumnsList();
          for (int row = 0; row < numRows; row++) {
            double startSec = fromNs(cols.get(0).getLongValues(row));
            data.tsStarts[row] = startSec;
            data.tsEnds[row] = startSec + fromNs(cols.get(1).getLongValues(row));
            data.idles[row] = (byte)cols.get(2).getDoubleValues(row);
            data.freqKHz[row] = (int)cols.get(3).getDoubleValues(row);
          }
          return data;
        });
      });
    }

    private double maximumValue() {
      return Math.max(getConfig().maximumValue, maximumValueSeen);
    }

    private ListenableFuture<Service.PerfettoQueryResult> query(String... queries) {
      return query(queries, 0);
    }

    private ListenableFuture<Service.PerfettoQueryResult> query(String[] queries, int idx) {
      return transformAsync(engine.query(queries[idx]), result -> {
        if (!result.getError().isEmpty()) {
          return Futures.immediateFailedFuture(new Exception("Query error: " + result.getError()));
        }
        if (idx + 1 >= queries.length) {
          return Futures.immediateFuture(result);
        }
        return query(queries, idx + 1);
      });
    }
  }

  public static class FreqTrack extends Track<Config, Data> {
    private static final float MARGIN_TOP = 4.5f;
    private static final float RECT_HEIGHT = 30;

    private boolean reqPending = false;
    private int mouseXpos = 0;
    private Integer hoveredValue = null;
    private Double hoveredTs = null;
    private Double hoveredTsEnd = null;
    private Byte hoveredIdle = null;

    public FreqTrack(TrackState trackState) {
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

      assertTrue(data.tsStarts.length == data.freqKHz.length);
      assertTrue(data.freqKHz.length == data.idles.length);

      double startPx = timeScale.timeToPx(visibleWindowTime.start);
      double endPx = timeScale.timeToPx(visibleWindowTime.end);
      float zeroY = MARGIN_TOP + RECT_HEIGHT;

      final String[] kUnits = new String[] { "", "K", "M", "G", "T", "E" };
      double exp = Math.ceil(Math.log10(Math.max(data.maximumValue, 1)));
      double pow10 = Math.pow(10, exp);
      double yMax = Math.ceil(data.maximumValue / (pow10 / 4)) * (pow10 / 4);
      int unitGroup = (int)Math.floor(exp / 3);
      // The values we have for cpufreq are in kHz so +1 to unitGroup.
      String yLabel = (yMax / Math.pow(10, unitGroup * 3)) + " " + kUnits[unitGroup + 1] + "Hz";

      // Draw the CPU frequency graph.
      float hue = Colors.hueForCpu(this.getConfig().cpu);
      ctx.setColor(hsl(hue, .4f, .55f), hsl(hue, .45f, .7f));
      ctx.path(StrokeFill, path -> {
        double lastX = startPx, lastY = zeroY;
        path.moveTo((float)lastX, (float)lastY);
        for (int i = 0; i < data.freqKHz.length; i++) {
          double value = data.freqKHz[i];
          double startTime = data.tsStarts[i];
          double nextY = zeroY - Math.round((value / yMax) * RECT_HEIGHT);
          if (nextY == lastY) {
            continue;
          }

          lastX = Math.floor(timeScale.timeToPx(startTime));
          path.lineTo((float)lastX, (float)lastY);
          path.lineTo((float)lastX, (float)nextY);
          lastY = nextY;
        }
        // Find the end time for the last frequency event and then draw
        // down to zero to show that we do not have data after that point.
        double endTime = data.tsEnds[data.freqKHz.length - 1];
        double finalX = Math.floor(timeScale.timeToPx(endTime));
        path.lineTo((float)finalX, (float)lastY);
        path.lineTo((float)finalX, zeroY);
        path.lineTo((float)endPx, zeroY);
        path.close();
      });

      // Draw CPU idle rectangles that overlay the CPU freq graph.
      ctx.setColor(null, new RGB(240, 240, 240));
      float bottomY = MARGIN_TOP + RECT_HEIGHT;

      for (int i = 0; i < data.freqKHz.length; i++) {
        if (data.idles[i] >= 0) {
          float value = data.freqKHz[i];
          float firstX = (float)Math.floor(timeScale.timeToPx(data.tsStarts[i]));
          float secondX = (float)Math.floor(timeScale.timeToPx(data.tsEnds[i]));
          float lastY = zeroY - Math.round((value / yMax) * RECT_HEIGHT);
          ctx.drawRectangle(Fill, firstX, bottomY, secondX - firstX, lastY - bottomY);
        }
      }

      // TS2J: ctx.font = '10px Google Sans';
      if (hoveredValue != null && hoveredTs != null) {
        String text = "freq: " + hoveredValue + "kHz";
        int width = ctx.textExtent(text).x;

        ctx.setColor(hsl(hue, .45f, .45f), hsl(hue, .45f, .75f));

        double xStart = Math.floor(timeScale.timeToPx(hoveredTs));
        double xEnd = hoveredTsEnd == null ? endPx : Math.floor(timeScale.timeToPx(hoveredTsEnd));
        double y = zeroY - Math.round((hoveredValue / yMax) * RECT_HEIGHT);

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
          ctx.drawRectangle(Fill, mouseXpos + 5, (int)MARGIN_TOP, width + 16, (int)RECT_HEIGHT);
        });
        ctx.drawText(text, mouseXpos + 10, (MARGIN_TOP + RECT_HEIGHT / 2 - 5));
        if (hoveredIdle != null && hoveredIdle != -1) {
          String idle = "idle: " + (hoveredIdle + 1);
          ctx.drawText(idle, mouseXpos + 10, (MARGIN_TOP + RECT_HEIGHT / 2 + 5));
        }
      }

      // Write the Y scale on the top left corner.
      ctx.setColor(new RGB(0x66, 0x66, 0x66), new RGB(0xff, 0xff, 0xff));
      ctx.withAlpha(.6f, () -> {
        ctx.drawRectangle(Fill, 0, 0, 40, 16);
      });
      ctx.drawText(yLabel, 5, 3);

      // If the cached trace slices don't fully cover the visible time range,
      // show a gray rectangle with a "Loading..." label.
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

      int index = BinarySearch.search(data.tsStarts, time);
      hoveredTs = index == -1 ? null : data.tsStarts[index];
      hoveredTsEnd = index == -1 ? null : data.tsEnds[index];
      hoveredValue = index == -1 ? null : data.freqKHz[index];
      hoveredIdle = index == -1 ? null : data.idles[index];
      return true;
    }

    @Override
    public void onMouseOut() {
      hoveredValue = null;
      hoveredTs = null;
      hoveredTsEnd = null;
      hoveredIdle = null;
    }
  }

  public static void init() {
    TrackController.register(CPU_FREQ_TRACK_KIND, Controller::new);
    Track.register(CPU_FREQ_TRACK_KIND, FreqTrack::new);
  }
}
