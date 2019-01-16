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
import static com.google.gapid.util.MoreFutures.logFailure;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.State.TrackState;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.controller.TrackController;
import com.google.gapid.perfetto.frontend.RenderContext;
import com.google.gapid.perfetto.frontend.TimeScale;
import com.google.gapid.perfetto.frontend.Track;
import com.google.gapid.proto.service.Service;

import java.util.List;
import java.util.logging.Logger;

public class ProcessSummary {
  protected static Logger LOG = Logger.getLogger(ProcessSummary.class.getName());

  public static final String PROCESS_SUMMARY_TRACK = "ProcessSummaryTrack";

  public static class Data {
    public final double start;
    public final double end;
    public final double resolution;
    public final double bucketSizeSeconds;
    public final double[] utilizations;

    public Data(double start, double end, double resolution, double bucketSizeSeconds,
        double[] utilizations) {
      this.start = start;
      this.end = end;
      this.resolution = resolution;
      this.bucketSizeSeconds = bucketSizeSeconds;
      this.utilizations = utilizations;
    }
  }

  public static class Config {
    public final int pidForColor;
    public final int upid;
    public final int utid;

    public Config(int pidForColor, int upid, int utid) {
      this.pidForColor = pidForColor;
      this.upid = upid;
      this.utid = utid;
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
      String processSliceView = tableName("process_slice_view");
      String dropProcessSliceView = "drop view if exists " + processSliceView;
      String dropSpan = "drop table if exists " + tableName("span");
      String dropWin = "drop table if exists " + tableName("window");
      String createWin = "create virtual table " + tableName("window") + " using window";
      String createSpan = "create virtual table " + tableName("span") +
          " using span_join(" + processSliceView + " PARTITIONED cpu, " +
          tableName("window") + " PARTITIONED cpu)";

      return transformAsync(query(dropProcessSliceView, dropSpan, dropWin, createWin), $1 ->
        transformAsync(getThreadIds(), threadIds -> {
          String createSliceView = "create view " + processSliceView + " as" +
              // 0 as cpu is a dummy column to perform span join on.
              " select ts, dur/" + threadIds.size() + " as dur, 0 as cpu" +
              " from slices where depth = 0 and utid in" +
              // TODO(dproy): This query is faster if we write it as x < utid < y.
              " (" + Joiner.on(",").join(threadIds) + ")";
          return transformAsync(query(createSliceView, createSpan), $2 ->
            cGlobals().onUiThread(() -> { setup = true; }));
        }));
    }

    private ListenableFuture<List<Long>> getThreadIds() {
      if (getConfig().upid == 0) {
        return Futures.immediateFuture(Lists.newArrayList((long)getConfig().utid));
      }
      String query = "select utid from thread where upid=" + getConfig().upid;
      return transform(query(query), threadQuery ->
        threadQuery.getColumns(0).getLongValuesList());
    }

    private ListenableFuture<Data> update(double start, double end, double resolution) {
      long startNs = Math.round(start * 1e9);
      long endNs = Math.round(end * 1e9);
      long bucketSizeNs = Math.round(resolution * 10 * 1e9);
      long windowStartNs = (long)Math.floor((double)startNs / bucketSizeNs) * bucketSizeNs;
      long windowDurNs = Math.max(1, endNs - windowStartNs);
      String query = "update " + tableName("window") + " set" +
          " window_start=" + windowStartNs + "," +
          " window_dur=" + windowDurNs + "," +
          " quantum=" + bucketSizeNs +
          " where rowid = 0";
      return transformAsync(query(query), $ ->
        computeSummary(fromNs(windowStartNs), end, resolution, bucketSizeNs));
    }

    private ListenableFuture<Data> computeSummary(
        double start, double end, double resolution, long bucketSizeNs) {
      double startNs = Math.round(start * 1e9);
      double endNs = Math.round(end * 1e9);
      int numBuckets = (int)Math.ceil((endNs - startNs) / bucketSizeNs);
      String query = "select quantum_ts as bucket," +
          " sum(dur)/cast(" + bucketSizeNs + " as float) as utilization" +
          " from " + tableName("span") +
          " where cpu = 0" +
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

  public static class SummaryTrack extends Track<Config, Data> {
    private static int MARGIN_TOP;
    private static int RECT_HEIGHT = 30;

    private boolean reqPending = false;
    private float hue;

    public SummaryTrack(TrackState trackState) {
      super(trackState);
      this.hue = (128 + (32 * this.getConfig().upid)) % 256;
    }

    private void reqDataDeferred() {
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      double reqStart = visibleWindowTime.start - visibleWindowTime.getDuration();
      double reqEnd = visibleWindowTime.end + visibleWindowTime.getDuration();
      double reqResolution = getCurResolution();
      reqPending = false;
      feGlobals().dispatch(Actions.reqTrackData(trackState.id, reqStart, reqEnd, reqResolution));
    }

    private static double getCurResolution() {
      // Truncate the resolution to the closest power of 10.
      double resolution = feGlobals().getFrontendLocalState().timeScaleTrack.deltaPxToDuration(1);
      return Math.pow(10, Math.floor(Math.log10(resolution)));
    }

    @Override
    public void renderCanvas(RenderContext ctx) {
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

      renderSummary(ctx, data);
    }

    private void renderSummary(RenderContext ctx, Data data) {
      TimeScale timeScale = feGlobals().getFrontendLocalState().timeScaleTrack;
      TimeSpan visibleWindowTime = feGlobals().getFrontendLocalState().visibleWindowTime;
      double startPx = Math.floor(timeScale.timeToPx(visibleWindowTime.start));
      double bottomY = MARGIN_TOP + RECT_HEIGHT;

      ctx.gc.setBackground(ctx.colors.get(hue, .5f, .6f));
      ctx.path(path -> {
        double lastX = startPx;
        double lastY = bottomY;

        path.moveTo((float)lastX, (float)lastY);
        for (int i = 0; i < data.utilizations.length; i++) {
          double utilization = Math.min(data.utilizations[i], 1);
          double startTime = i * data.bucketSizeSeconds + data.start;

          lastX = Math.floor(timeScale.timeToPx(startTime));

          path.lineTo((float)lastX, (float)lastY);
          lastY = MARGIN_TOP + Math.round(RECT_HEIGHT * (1 - utilization));
          path.lineTo((float)lastX, (float)lastY);
        }
        path.lineTo((float)lastX, (float)bottomY);
        path.close();
        ctx.gc.fillPath(path);
      });
    }
  }

  public static void init() {
    TrackController.register(PROCESS_SUMMARY_TRACK, Controller::new);
    Track.register(PROCESS_SUMMARY_TRACK, SummaryTrack::new);
  }
}
