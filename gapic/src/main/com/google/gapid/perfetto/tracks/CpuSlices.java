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
import static com.google.gapid.perfetto.tracks.Colors.colorForThread;
import static com.google.gapid.perfetto.tracks.Colors.hueForCpu;
import static com.google.gapid.skia.RenderContext.Style.Fill;
import static com.google.gapid.util.Colors.hsla;
import static com.google.gapid.util.MoreFutures.logFailure;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.State.TrackState;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.controller.TrackController;
import com.google.gapid.perfetto.frontend.ThreadDesc;
import com.google.gapid.perfetto.frontend.TimeScale;
import com.google.gapid.perfetto.frontend.Track;
import com.google.gapid.proto.service.Service;
import com.google.gapid.skia.RenderContext;

import org.eclipse.swt.graphics.RGBA;

import java.util.List;
import java.util.logging.Logger;

public class CpuSlices {
  protected static Logger LOG = Logger.getLogger(CpuSlices.class.getName());

  public static final String CPU_SLICE_TRACK_KIND = "CpuSliceTrack";

  public static class Data {
    public final Kind kind;
    public final double start;
    public final double end;
    public final double resolution;
    // summary
    public final double bucketSizeSeconds;
    public final double[] utilizations;
    // slice
    public final double[] starts;
    public final double[] ends;
    public final int[] utids;

    public Data(double start, double end, double resolution, double bucketSizeSeconds, double[] utilizations) {
      this.kind = Kind.summary;
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.bucketSizeSeconds = bucketSizeSeconds;
      this.utilizations = utilizations;
      this.starts = null;
      this.ends = null;
      this.utids = null;
    }

    public Data(double start, double end, double resolution, double[] starts, double[] ends, int[] utids) {
      this.kind = Kind.slice;
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.starts = starts;
      this.ends = ends;
      this.utids = utids;
      this.bucketSizeSeconds = 0;
      this.utilizations = null;
    }

    Data fixEnd() {
      return new Data(start, ends[ends.length - 1], resolution, starts, ends, utids);
    }

    public static enum Kind {
      summary, slice;
    }
  }

  public static class Config {
    public final int cpu;

    public Config(int cpu) {
      this.cpu = cpu;
    }
  }

  public static class Controller extends TrackController<Config, Data> {
    private final Engine engine;
    private boolean busy = false;
    private boolean setup = false;

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
      String dropSpan = "drop table if exists " + tableName("span");
      String dropWin = "drop table if exists " + tableName("window");
      String createWin = "create virtual table " + tableName("window") + " using window";
      String createSpan = "create virtual table " + tableName("span") +
          " using span_join(sched PARTITIONED cpu, " + tableName("window") + " PARTITIONED cpu)";
      return transformAsync(query(dropSpan, dropWin, createWin, createSpan), $ ->
          cGlobals().onUiThread(() -> { setup = true; }));
    }

    private ListenableFuture<Data> update(double start, double end, double resolution) {
      long startNs = Math.round(start * 1e9);
      long endNs = Math.round(end * 1e9);
      boolean isQuantized = resolution >= 0.001;
      long bucketSizeNs = Math.round(resolution * 10 * 1e9);
      long windowStartNs = isQuantized ?
          (long)Math.floor((double)startNs / bucketSizeNs) * bucketSizeNs : startNs;
      long windowDurNs = Math.max(1, endNs - windowStartNs);
      String query = "update " + tableName("window") + " set" +
          " window_start=" + windowStartNs + "," +
          " window_dur=" + windowDurNs + "," +
          " quantum=" + (isQuantized ? bucketSizeNs : 0) +
          " where rowid = 0";
      return transformAsync(query(query), $ -> {
        if (isQuantized) {
          return computeSummary(fromNs(windowStartNs), end, resolution, bucketSizeNs);
        } else {
          return computeSlices(fromNs(windowStartNs), end, resolution);
        }
      });
    }

    private ListenableFuture<Data> computeSummary(
        double start, double end, double resolution, long bucketSizeNs) {
      double startNs = Math.round(start * 1e9);
      double endNs = Math.round(end * 1e9);
      int numBuckets = (int)Math.ceil((endNs - startNs) / bucketSizeNs);
      String query = "select" +
          " quantum_ts as bucket," +
          " sum(dur)/cast(" + bucketSizeNs + " as float) as utilization" +
          " from " + tableName("span") +
          " where cpu = " + getConfig().cpu +
          " and utid != 0" +
          " group by quantum_ts";
      return transform(query(query), rawResult -> {
        Data summary = new Data(start, end, resolution, fromNs(bucketSizeNs), new double[numBuckets]);
        int numRows = (int)rawResult.getNumRecords();
        List<Service.PerfettoQueryResult.ColumnValues> cols = rawResult.getColumnsList();
        for (int row = 0; row < numRows; row++) {
          int bucket = (int)cols.get(0).getLongValues(row);
          summary.utilizations[bucket] = cols.get(1).getDoubleValues(row);
        }
        return summary;
      });
    }

    private ListenableFuture<Data> computeSlices(double start, double end, double resolution) {
      // TODO(hjd): Remove LIMIT
      final int LIMIT = 50000;
      String query = "select ts,dur,utid from " + tableName("span") +
          " where cpu = " + getConfig().cpu +
          " and utid != 0" +
          " limit " + LIMIT;
      return transform(query(query), rawResult -> {
        int numRows = (int)rawResult.getNumRecords();
        Data slices = new Data(
            start, end, resolution, new double[numRows], new double[numRows], new int[numRows]);
        List<Service.PerfettoQueryResult.ColumnValues> cols = rawResult.getColumnsList();
        for (int row = 0; row < numRows; row++) {
          double startSec = fromNs(cols.get(0).getLongValues(row));
          slices.starts[row] = startSec;
          slices.ends[row] = startSec + fromNs(cols.get(1).getLongValues(row));
          slices.utids[row] = (int)cols.get(2).getLongValues(row);
        }
        return (numRows == LIMIT) ? slices.fixEnd() : slices;
      });
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

  public static class CpuTrack extends Track<Config, Data> {
    private static final int MARGIN_TOP = 5;
    private static final int RECT_HEIGHT = 30;

    private int mouseXpos;
    private boolean reqPending = false;
    private float hue;
    private int utidHoveredInThisTrack = -1;

    public CpuTrack(TrackState trackState) {
      super(trackState);
      this.hue = hueForCpu(getConfig().cpu);
    }

    private static String cropText(String str, double charWidth, double maxTextWidth) {
      String displayText = "";
      double nameLength = str.length() * charWidth;
      if (nameLength < maxTextWidth) {
        displayText = str;
      } else {
        // -3 for the 3 ellipsis.
        int displayedChars = (int)Math.floor(maxTextWidth / charWidth) - 3;
        if (displayedChars > 3) {
          displayText = str.substring(0, displayedChars) + "...";
        }
      }
      return displayText;
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

      switch (data.kind) {
        case slice: renderSlices(ctx, data); break;
        case summary: renderSummary(ctx, data); break;
      }
    }

    private void renderSummary(RenderContext ctx, Data data) {
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      double startPx = Math.floor(timeScale.timeToPx(visibleWindowTime.start));
      double bottomY = MARGIN_TOP + RECT_HEIGHT;

      ctx.setColor(null, hsla(hue, .5f, .6f, 255));
      ctx.path(Fill, path -> {
        double lastX = startPx;
        double lastY = bottomY;

        path.moveTo((float)lastX, (float)lastY);
        for (int i = 0; i < data.utilizations.length; i++) {
          double utilization = data.utilizations[i];
          double startTime = i * data.bucketSizeSeconds + data.start;

          lastX = Math.floor(timeScale.timeToPx(startTime));

          path.lineTo((float)lastX, (float)lastY);
          lastY = MARGIN_TOP + Math.round(RECT_HEIGHT * (1 - utilization));
          path.lineTo((float)lastX, (float)lastY);
        }
        path.lineTo((float)lastX, (float)bottomY);
        path.close();
      });
    }

    private void renderSlices(RenderContext ctx, Data data) {
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      assertTrue(data.starts.length == data.ends.length);
      assertTrue(data.starts.length == data.utids.length);

      // TS2J: ctx.textAlign = 'center';
      // TS2J: ctx.font = '12px Google Sans';
      // TS2J: really? // measuretext is expensive so we only use it once.
      float charWidth = ctx.textExtent("dbpqaouk").x / 8.0f;

      boolean isHovering = feGlobals().getFrontendLocalState().hoveredUtid != -1;

      for (int i = 0; i < data.starts.length; i++) {
        double tStart = data.starts[i];
        double tEnd = data.ends[i];
        int utid = data.utids[i];
        if (tEnd <= visibleWindowTime.start || tStart >= visibleWindowTime.end) {
          continue;
        }
        float rectStart = (float)timeScale.timeToPx(tStart);
        float rectEnd = (float)timeScale.timeToPx(tEnd);
        float rectWidth = rectEnd - rectStart;
        if (rectWidth < /*TS2J: 0.1*/ 1) {
          continue;
        }

        ThreadDesc threadInfo = feGlobals().getThreads().get(utid);

        // TODO: consider de-duplicating this code with the copied one from
        // chrome_slices/frontend.ts.
        String title = "[utid:" + utid + "]";
        String subTitle = "";
        int pid = -1;

        if (threadInfo != null) {
          if (threadInfo.pid != 0) {
            pid = threadInfo.pid;
            String procName = threadInfo.procName;
            title = procName + " [" + threadInfo.pid + "]";
            subTitle = threadInfo.threadName + " [" + threadInfo.tid + "]";
          } else {
            title = threadInfo.threadName + " [" + threadInfo.tid + "]";
          }
        }

        boolean isThreadHovered = feGlobals().getFrontendLocalState().hoveredUtid == utid;
        boolean isProcessHovered = feGlobals().getFrontendLocalState().hoveredPid == pid;
        Colors.HSL color = colorForThread(threadInfo);
        if (isHovering && !isThreadHovered) {
          if (!isProcessHovered) {
            color = color.adjusted(color.h, 0, 90);
          } else {
            color = color.adjusted(color.h, color.s - 20, Math.min(color.l + 30, 80));
          }
        } else {
          color = color.adjusted(color.h, color.s - 20, Math.min(color.l + 10,  60));
        }

        ctx.setColor(new RGBA(0xff, 0xff, 0xff, 0xff), color.swt());
        ctx.drawRectangle(Fill, rectStart, MARGIN_TOP, (rectEnd - rectStart), RECT_HEIGHT);

        // Don't render text when we have less than 5px to play with.
        if (rectWidth < 5) {
          continue;
        }

        float maxTextWidth = rectWidth - 4;
        title = cropText(title, charWidth, maxTextWidth);
        subTitle = cropText(subTitle, charWidth, maxTextWidth);

        // TS2J: const rectXCenter = rectStart + rectWidth / 2;
        if (!title.isEmpty()) {
          // TS2J: ctx.font = '12px Google Sans';
          // TS2J: ctx.fillText(title, rectXCenter, MARGIN_TOP + RECT_HEIGHT / 2 - 3);
          ctx.drawText(title,
              (rectStart + (maxTextWidth - title.length() * charWidth) / 2),
              MARGIN_TOP + RECT_HEIGHT / 2 - 13);
        }
        if (!subTitle.isEmpty()) {
          // TS2J: ctx.font = '10px Google Sans';
          // TS2J: ctx.fillText(subTitle, rectXCenter, MARGIN_TOP + RECT_HEIGHT / 2 + 11);
          String sub = subTitle;
          //ctx.withAlpha(.6f, () ->
            ctx.drawText(sub,
                (rectStart + (maxTextWidth - sub.length() * charWidth) / 2),
                MARGIN_TOP + RECT_HEIGHT / 2);
        }
      }

      ThreadDesc hoveredThread = feGlobals().getThreads().get(utidHoveredInThisTrack);
      if (hoveredThread != null) {
        String line1 = "";
        String line2 = "";
        if (hoveredThread.pid != 0) {
          line1 = "P: " + hoveredThread.procName + " [" + hoveredThread.pid + "]";
          line2 = "T: " + hoveredThread.threadName + " [" + hoveredThread.tid + "]";
        } else {
          line1 = "T: " + hoveredThread.threadName + " [" + hoveredThread.tid + "]";
        }

        // TS2J: ctx.font = '10px Google Sans';
        int line1Width = ctx.textExtent(line1).x;
        int line2Width = ctx.textExtent(line2).x;
        int width = Math.max(line1Width, line2Width);

        ctx.setColor(hsla(200f, .5f, .4f, 255), new RGBA(0xff, 0xff, 0xff, 229));
        ctx.drawRectangle(Fill, mouseXpos, MARGIN_TOP, width + 16, RECT_HEIGHT);
        ctx.drawText(line1, mouseXpos + 8, 8);
        ctx.drawText(line2, mouseXpos + 8, 18);
      }
    }

    @Override
    public boolean onMouseMove(int x, int y) {
      Data data = getData();
      mouseXpos = x;
      if (data == null || data.kind == Data.Kind.summary) {
        return false;
      }
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      if (y < MARGIN_TOP ||y > MARGIN_TOP + RECT_HEIGHT) {
        utidHoveredInThisTrack = -1;
        feGlobals().getFrontendLocalState().setHoveredUtidAndPid(-1, -1);
        return false;
      }
      double t = timeScale.pxToTime(x);
      int hoveredUtid = -1;

      for (int i = 0; i < data.starts.length; i++) {
        double tStart = data.starts[i];
        double tEnd = data.ends[i];
        int utid = data.utids[i];
        if (tStart <= t && t <= tEnd) {
          hoveredUtid = utid;
          break;
        }
      }
      utidHoveredInThisTrack = hoveredUtid;
      ThreadDesc threadInfo = feGlobals().getThreads().get(hoveredUtid);
      int hoveredPid = (threadInfo != null) ? (threadInfo.pid != 0) ? threadInfo.pid : -1 : -1;
      feGlobals().getFrontendLocalState().setHoveredUtidAndPid(hoveredUtid, hoveredPid);
      return hoveredUtid != -1;
    }

    @Override
    public void onMouseOut() {
      utidHoveredInThisTrack = -1;
      feGlobals().getFrontendLocalState().setHoveredUtidAndPid(-1, -1);
      mouseXpos = 0;
    }
  }

  public static void init() {
    TrackController.register(CPU_SLICE_TRACK_KIND, Controller::new);
    Track.register(CPU_SLICE_TRACK_KIND, CpuTrack::new);
  }
}
