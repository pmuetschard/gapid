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
package com.google.gapid.perfetto.controller;

import static com.google.gapid.perfetto.base.Logging.assertExists;
import static com.google.gapid.perfetto.base.Logging.assertTrue;
import static com.google.gapid.perfetto.common.State.SCROLLING_TRACK_GROUP;
import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;
import static com.google.gapid.perfetto.tracks.ChromeSlices.SLICE_TRACK_KIND;
import static com.google.gapid.perfetto.tracks.Counter.COUNTER_TRACK_KIND;
import static com.google.gapid.perfetto.tracks.CpuFrequency.CPU_FREQ_TRACK_KIND;
import static com.google.gapid.perfetto.tracks.CpuSlices.CPU_SLICE_TRACK_KIND;
import static com.google.gapid.perfetto.tracks.ProcessSummary.PROCESS_SUMMARY_TRACK;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.MoreFutures.transformAsync;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Actions.DeferredAction;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.State.EngineConfig;
import com.google.gapid.perfetto.common.State.QueryConfig;
import com.google.gapid.perfetto.common.State.Status;
import com.google.gapid.perfetto.common.State.TraceTime;
import com.google.gapid.perfetto.common.State.TrackState;
import com.google.gapid.perfetto.common.TimeSpan;
import com.google.gapid.perfetto.controller.QueryController.QueryControllerArgs;
import com.google.gapid.perfetto.frontend.QuantizedLoad;
import com.google.gapid.perfetto.frontend.ThreadDesc;
import com.google.gapid.perfetto.tracks.ChromeSlices;
import com.google.gapid.perfetto.tracks.Counter;
import com.google.gapid.perfetto.tracks.CpuFrequency;
import com.google.gapid.perfetto.tracks.CpuSlices;
import com.google.gapid.perfetto.tracks.ProcessSummary;
import com.google.gapid.proto.service.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TraceController extends Controller<TraceController.State> {
  private static final int OVERVIEW_NUM_STEPS = 100;

  private Engine engine;
  private final String engineId;

  public TraceController(String engineId) {
    super(State.init);
    this.engineId = engineId;
  }

  @Override
  public List<ControllerInitializer<?>> run() {
    EngineConfig engineCfg = assertExists(cGlobals().getState().engines.get(engineId));
    switch (getState()) {
      case init:
        cGlobals().dispatch(Actions.setEngineReady(engineId, false));
        cGlobals().thenOnUiThread(loadTrace(), $ -> cGlobals().dispatch(Actions.setEngineReady(engineId, true)));
        updateStatus("Opening trace");
        setState(State.loading_trace);
        break;

      case loading_trace:
        if (engine == null || !engineCfg.ready) {
          break;
        }
        setState(State.ready);
        break;

      case ready:
        // At this point we are ready to serve queries and handle tracks.
        assertExists(engine);
        assertTrue(engineCfg.ready);
        List<ControllerInitializer<?>> childControllers = Lists.newArrayList();

        // Create a TrackController for each track.
        for (Map.Entry<String, TrackState> e : cGlobals().getState().tracks.entrySet()) {
          if (!e.getValue().engineId.equals(engineId)) {
            continue;
          }
          if (!TrackController.REGISTRY.has(e.getValue().kind)) {
            continue;
          }
          TrackController.FactoryProvider factory = TrackController.REGISTRY.get(e.getValue().kind);
          childControllers.add(child(e.getKey(),
              factory.factory(new TrackController.TrackControllerArgs(e.getKey(), engine))));
        }

        // Create a QueryController for each query.
        for (Map.Entry<String, QueryConfig> e : cGlobals().getState().queries.entrySet()) {
          childControllers.add(child(e.getKey(), new QueryControllerArgs(e.getKey(), engine)));
        }

        return childControllers;

    }
    return null;
  }

  // TS2J: we could possibly do some of this in parallel.
  private ListenableFuture<Void> loadTrace() {
    engine = cGlobals().getEngine();
    // TS2J: we don't need to load the trace, it's already loaded.
    return transformAsync(engine.getTraceTimeBounds(), traceTime -> {
      TraceTime traceTimeState = new TraceTime(
          traceTime.start, traceTime.end, System.currentTimeMillis() / 1000);
      List<DeferredAction<?>> actions = Lists.newArrayList();
      actions.add(Actions.setTraceTime(traceTimeState));
      actions.add(Actions.navigate("/viewer"));
      if (cGlobals().getState().visibleTraceTime.lastUpdate == 0) {
        actions.add(Actions.setVisibleTraceTime(traceTimeState));
      }
      return transformAsync(cGlobals().onUiThread(() -> cGlobals().dispatch(actions)), $1 -> {
        if (cGlobals().getState().pinnedTracks.isEmpty() && cGlobals().getState().scrollingTracks.isEmpty()) {
          return transformAsync(listTracks(), $2 ->
            transformAsync(listThreads(), $3 -> loadTimelineOverview(traceTime)));
        } else {
          return transformAsync(listThreads(), $4 -> loadTimelineOverview(traceTime));
        }
      });
    });
  }

  private ListenableFuture<Void> listTracks() {
    updateStatus("Loading tracks");
    assertExists(engine);
    ListenableFuture<List<DeferredAction<?>>> cpuTracks = transformAsync(
        engine.getNumberOfCpus(), numCpus -> {
      String query = "select max(value) from counters where name = 'cpufreq'";
      return transformAsync(engine.query(query), maxFreq -> {
        List<DeferredAction<?>> addToTrackActions = Lists.newArrayList();
        for (int cpu = 0; cpu < numCpus; cpu++) {
          addToTrackActions.add(Actions.addTrack(null, engineId, CPU_SLICE_TRACK_KIND, "CPU " + cpu,
              SCROLLING_TRACK_GROUP, new CpuSlices.Config(cpu)));
        }
        return listTracksCpuFreq(addToTrackActions, 0, numCpus, maxFreq.getColumns(0).getDoubleValues(0));
      });
    });

    return transformAsync(cpuTracks,
        actions -> cGlobals().onUiThread(() -> cGlobals().dispatch(actions)));
  }

  private ListenableFuture<List<DeferredAction<?>>> listTracksCpuFreq(
      List<DeferredAction<?>> addToTrackActions, int cpu, int maxCpu, double maxFreq) {
    if (cpu >= maxCpu) {
      return listTracksCounters(addToTrackActions);
    }
    String query = "select value from counters where name = 'cpufreq' and ref = " + cpu + " limit 1";
    return transformAsync(engine.query(query), freqExists -> {
      if (freqExists.getNumRecords() > 0) {
        addToTrackActions.add(Actions.addTrack(null, engineId, CPU_FREQ_TRACK_KIND,
            "CPU " + cpu + " Frequency", SCROLLING_TRACK_GROUP,
            new CpuFrequency.Config(cpu, maxFreq)));
      }
      return listTracksCpuFreq(addToTrackActions, cpu + 1, maxCpu, maxFreq);
    });
  }

  private ListenableFuture<List<DeferredAction<?>>> listTracksCounters(
      List<DeferredAction<?>> addToTrackActions) {
    String query = "select name, ref, ref_type, count(ref_type) " +
        "from counters " +
        "where ref is not null " +
        "group by name, ref, ref_type " +
        "order by ref_type desc";
    return transformAsync(engine.query(query), counters -> {
      Set<Integer> counterUpids = Sets.newHashSet();
      Set<Integer> counterUtids = Sets.newHashSet();
      for (int i = 0; i < counters.getNumRecords(); i++) {
        int ref = (int)counters.getColumns(1).getLongValues(i);
        String refType = counters.getColumns(2).getStringValues(i);
        if ("upid".equals(refType)) {
          counterUpids.add(ref);
        } else if ("utid".equals(refType)) {
          counterUtids.add(ref);
        }
      }

      // Add all the global counter tracks that are not bound to any pid/tid,
      // the ones for which refType == NULL.
      for (int i = 0; i < counters.getNumRecords(); i++) {
        String name = counters.getColumns(0).getStringValues(i);
        String refType = counters.getColumns(2).getStringValues(i);
        if (!"[NULL]".equals(refType)) {
          continue;
        }
        addToTrackActions.add(Actions.addTrack(null, engineId, COUNTER_TRACK_KIND, name,
            SCROLLING_TRACK_GROUP, new Counter.Config(name, 0)));
      }
      return listTracksSlices(addToTrackActions, counters, counterUpids, counterUtids);
    });
  }

  private ListenableFuture<List<DeferredAction<?>>> listTracksSlices(
      List<DeferredAction<?>> addToTrackActions, Service.PerfettoQueryResult counters,
      Set<Integer> counterUpids, Set<Integer> counterUtids) {
    String query = "select utid, max(depth) from slices group by utid";
    return transformAsync(engine.query(query), maxDepthQuery -> {
      Map<Integer, Integer> utidToMaxDepth = Maps.newHashMap();
      for (int i = 0; i < maxDepthQuery.getNumRecords(); i++) {
        int utid = (int)maxDepthQuery.getColumns(0).getLongValues(i);
        int maxDepth = (int)maxDepthQuery.getColumns(1).getLongValues(i);
        utidToMaxDepth.put(utid, maxDepth);
      }
      return listTracksThreads(
          addToTrackActions, counters, counterUpids, counterUtids, utidToMaxDepth);
    });
  }

  private ListenableFuture<List<DeferredAction<?>>> listTracksThreads(
      List<DeferredAction<?>> addToTrackActions, Service.PerfettoQueryResult counters,
      Set<Integer> counterUpids, Set<Integer> counterUtids, Map<Integer, Integer> utidToMaxDepth) {
    String query = "select utid, tid, upid, pid, thread.name as threadName, process.name as processName, total_dur " +
        "from thread left join process using(upid) left join (" +
        "  select upid, sum(dur) as total_dur" +
        "  from sched join thread using(utid)" +
        "  group by upid) using(upid) " +
        "group by utid, upid " +
        "order by total_dur desc, upid, utid";
    return transform(engine.query(query), threadQuery -> {
      Map<Integer, String> upidToUuid = Maps.newHashMap();
      Map<Integer, String> utidToUuid = Maps.newHashMap();
      List<DeferredAction<?>> addSummaryTrackActions = Lists.newArrayList();
      List<DeferredAction<?>> addTrackGroupActions = Lists.newArrayList();
      for (int i = 0; i < threadQuery.getNumRecords(); i++) {
        boolean hasUpid = !threadQuery.getColumns(2).getIsNulls(i);
        int utid = (int)threadQuery.getColumns(0).getLongValues(i);
        int tid = (int)threadQuery.getColumns(1).getLongValues(i);
        int upid = hasUpid ? (int)threadQuery.getColumns(2).getLongValues(i) : 0;
        int pid = (int)threadQuery.getColumns(3).getLongValues(i);
        String threadName = threadQuery.getColumns(4).getStringValues(i);
        String processName = threadQuery.getColumns(5).getStringValues(i);
        Integer maxDepth = utidToMaxDepth.get(utid);
        if (maxDepth == null && (!hasUpid || !counterUpids.contains(upid)) && !counterUtids.contains(utid)) {
          continue;
        }

        // Group by upid if present else by utid.
        String pUuid = hasUpid ? upidToUuid.get(upid) : utidToUuid.get(utid);
        if (pUuid == null) {
          pUuid = UUID.randomUUID().toString();
          String summaryTrackId = UUID.randomUUID().toString();
          if (hasUpid) {
            upidToUuid.put(upid, pUuid);
          } else {
            utidToUuid.put(utid,  pUuid);
          }
          int pidForColor = pid != 0 ? pid : tid != 0 ? tid : upid != 0 ? upid : utid;
          addSummaryTrackActions.add(Actions.addTrack(
              summaryTrackId, engineId, PROCESS_SUMMARY_TRACK, (hasUpid ? pid : tid) + " summary",
              null, new ProcessSummary.Config(pidForColor, upid, utid)));
          addTrackGroupActions.add(Actions.addTrackGroup(
              engineId, hasUpid ? (processName + " " + pid) : (threadName + " " + tid), pUuid,
              summaryTrackId, true));

          for (int j = 0; j < counters.getNumRecords(); j++) {
            String name = counters.getColumns(0).getStringValues(j);
            int ref = (int)counters.getColumns(1).getLongValues(j);
            String refType = counters.getColumns(2).getStringValues(j);
            if (!"upid".equals(refType) || ref != upid) {
              continue;
            }
            addTrackGroupActions.add(Actions.addTrack(
                null, engineId, COUNTER_TRACK_KIND, name, pUuid, new Counter.Config(name,  ref)));
          }
        }

        for (int j = 0; j < counters.getNumRecords(); j++) {
          String name = counters.getColumns(0).getStringValues(j);
          int ref = (int)counters.getColumns(1).getLongValues(j);
          String refType = counters.getColumns(2).getStringValues(j);

          if (!"utid".equals(refType) || ref != utid) {
            continue;
          }
          addTrackGroupActions.add(Actions.addTrack(
              null, engineId, COUNTER_TRACK_KIND, name, pUuid, new Counter.Config(name, ref)));
        }

        if (maxDepth != null) {
          addToTrackActions.add(Actions.addTrack(
              null, engineId, SLICE_TRACK_KIND, threadName + " [" + tid + "]", pUuid,
              new ChromeSlices.Config(maxDepth, upid, utid)));
        }
      }
      return concat(addSummaryTrackActions, addTrackGroupActions, addToTrackActions);
    });
  }

  @SafeVarargs
  private static List<DeferredAction<?>> concat(List<DeferredAction<?>>...actions) {
    List<DeferredAction<?>> res = Lists.newArrayList();
    for (List<DeferredAction<?>> list : actions) {
      res.addAll(list);
    }
    return res;
  }

  private ListenableFuture<Void> listThreads() {
    updateStatus("Reading thread list");
    String sqlQuery = "select utid, tid, pid, thread.name, " +
        "ifnull(process.name, thread.name) " +
        "from thread left join process using(upid)";
    return transform(assertExists(engine).query(sqlQuery), threadRows -> {
      List<ThreadDesc> threads = Lists.newArrayList();
      for (int i = 0; i < threadRows.getNumRecords(); i++) {
        int utid = (int)threadRows.getColumns(0).getLongValues(i);
        int tid = (int)threadRows.getColumns(1).getLongValues(i);
        int pid = (int)threadRows.getColumns(2).getLongValues(i);
        String threadName = threadRows.getColumns(3).getStringValues(i);
        String procName = threadRows.getColumns(4).getStringValues(i);
        threads.add(new ThreadDesc(utid,tid, threadName, pid, procName));
      }
      cGlobals().onUiThread(() -> cGlobals().publish(ControllerGlobals.PublishType.Threads, threads));
      return null;
    });
  }

  private ListenableFuture<Void> loadTimelineOverview(TimeSpan traceTime) {
    assertExists(engine);
    double stepSec = traceTime.getDuration() / OVERVIEW_NUM_STEPS;
    return transformAsync(loadTimelineOverviewStep(traceTime, stepSec, 0, false), hasSchedOverview -> {
      if (hasSchedOverview) {
        return Futures.immediateFuture(null);
      }
      double traceStartNs = traceTime.start * 1e9;
      double stepSecNs = stepSec * 1e9;
      String query = "select bucket, upid, sum(utid_sum) / cast(${stepSecNs} as float) " +
          "as upid_sum from thread inner join " +
          "(select cast((ts - " + traceStartNs + "/" + stepSecNs + " as int) as bucket, " +
          "sum(dur) as utid_sum, utid from slices group by bucket, utid) " +
          "using(utid) group by bucket, upid)";
      return transform(engine.query(query), sliceSummaryQuery -> {
        Map<Integer, List<QuantizedLoad>> slicesData = Maps.newHashMap();
        for (int i = 0; i < sliceSummaryQuery.getNumRecords(); i++) {
          int bucket = (int)sliceSummaryQuery.getColumns(0).getLongValues(i);
          int upid = (int)sliceSummaryQuery.getColumns(1).getLongValues(i);
          double load = sliceSummaryQuery.getColumns(2).getDoubleValues(i);
          double startSec = traceTime.start + stepSec * bucket;
          double endSec = startSec + stepSec;
          List<QuantizedLoad> loadArray = slicesData.get(upid);
          if (loadArray == null) {
            slicesData.put(upid, loadArray = Lists.newArrayList());
          }
          loadArray.add(new QuantizedLoad(startSec, endSec, load));
        }
        cGlobals().onUiThread(() -> cGlobals().publish(ControllerGlobals.PublishType.OverviewData, slicesData));
        return null;
      });
    });
  }

  private ListenableFuture<Boolean>
      loadTimelineOverviewStep(TimeSpan traceTime, double stepSec, int step, boolean hasSchedOverview) {
    updateStatus(String.format("Loading overview %.1f%%", 100.0 * (step + 1) / OVERVIEW_NUM_STEPS));
    double startSec = traceTime.start + step * stepSec;
    long startNs = (long)Math.floor(startSec * 1e9);
    double endSec = startSec + stepSec;
    long endNs = (long)Math.ceil(endSec * 1e9);
    String query = "select sum(dur)/" + stepSec + "/1e9, cpu from sched " +
        "where ts >= " + startNs + " and ts < " + endNs + " and utid != 0 " +
        "group by cpu order by cpu";
    return transformAsync(engine.query(query), schedRows -> {
      boolean newHasSchedOverview = hasSchedOverview;
      Map<String, QuantizedLoad> schedData = Maps.newHashMap();
      for (int i = 0; i < schedRows.getNumRecords(); i++) {
        double load = schedRows.getColumns(0).getDoubleValues(i);
        int cpu = (int)schedRows.getColumns(1).getLongValues(i);
        schedData.put(String.valueOf(cpu), new QuantizedLoad(startSec, endSec, load));
        newHasSchedOverview = true;
      }
      cGlobals().onUiThread(() -> cGlobals().publish(ControllerGlobals.PublishType.OverviewData, schedData));

      if (step + 1 >= OVERVIEW_NUM_STEPS) {
        return Futures.immediateFuture(newHasSchedOverview);
      } else {
        return loadTimelineOverviewStep(traceTime, stepSec, step + 1, newHasSchedOverview);
      }
    });
  }

  private static void updateStatus(String msg) {
    cGlobals().onUiThread(() -> cGlobals().dispatch(
        Actions.updateStatus(new Status(msg, System.currentTimeMillis() / 1000))));
  }

  public static enum State {
    init, loading_trace, ready;
  }

  public static class TraceControllerArgs implements ControllerFactory<TraceController> {
    private String engineId;

    public TraceControllerArgs(String engineId) {
      this.engineId = engineId;
    }

    @Override
    public TraceController build() {
      return new TraceController(engineId);
    }
  }
}
