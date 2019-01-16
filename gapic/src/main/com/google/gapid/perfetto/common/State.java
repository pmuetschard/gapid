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
package com.google.gapid.perfetto.common;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class State {
  public static final String SCROLLING_TRACK_GROUP = "ScrollingTracks";

  public static class TrackState {
    public String id;
    public String engineId;
    public String kind;
    public String name;
    public String trackGroup;
    public TrackDataRequest dataReq;
    public Object config; // TS2J: ??

    public TrackState(String id, String engineId, String kind, String name, String trackGroup,
        TrackDataRequest dataReq, Object config) {
      this.id = id;
      this.engineId = engineId;
      this.kind = kind;
      this.name = name;
      this.trackGroup = trackGroup;
      this.dataReq = dataReq;
      this.config = config;
    }
  }

  public static class TrackGroupState {
    public String id;
    public String engineId;
    public String name;
    public boolean collapsed;
    public List<String> tracks;  // Child track ids.
    public String summaryTrackId;

    public TrackGroupState(String id, String engineId, String name, boolean collapsed,
        List<String> tracks, String summaryTrackId) {
      this.id = id;
      this.engineId = engineId;
      this.name = name;
      this.collapsed = collapsed;
      this.tracks = tracks;
      this.summaryTrackId = summaryTrackId;
    }
  }

  public static class TrackDataRequest {
    public double start, end, resolution;

    public TrackDataRequest(double start, double end, double resolution) {
      this.start = start;
      this.end = end;
      this.resolution = resolution;
    }
  }

  public static class EngineConfig {
    public String id;
    public boolean ready;
    public String source; // TS2J: String|File

    public EngineConfig(String id, boolean ready, String source) {
      this.id = id;
      this.ready = ready;
      this.source = source;
    }
  }

  public static class QueryConfig {
    public String id;
    public String engineId;
    public String query;

    public QueryConfig(String id, String engineId, String query) {
      this.id = id;
      this.engineId = engineId;
      this.query = query;
    }
  }

  public static class PermalinkConfig {
    public String requestId;  // Set by the frontend to request a new permalink.
    public String hash; // Set by the controller when the link has been created.

    public PermalinkConfig(String requestId, String hash) {
      this.requestId = requestId;
      this.hash = hash;
    }
  }

  public static class RecordConfig {
    // [key: string]: null|number|boolean|string|string[]; TS2J: ?????
    public final Map<String, Object> extras = Maps.newHashMap();

    // Global settings
    public int durationSeconds;
    public boolean writeIntoFile;
    public int fileWritePeriodMs;

    // Buffer setup
    public int bufferSizeMb;

    // Ftrace
    public boolean ftrace;
    public String[] ftraceEvents;
    public String[] atraceCategories;
    public String[] atraceApps;
    public int ftraceDrainPeriodMs;
    public int ftraceBufferSizeKb;

    // Ps
    public boolean processMetadata;
    public boolean scanAllProcessesOnStart;
    public int procStatusPeriodMs;

    // SysStats
    public boolean sysStats;
    public int meminfoPeriodMs;
    public String[] meminfoCounters;
    public int vmstatPeriodMs;
    public String[] vmstatCounters;
    public int statPeriodMs;
    public String[] statCounters;

    // Battery and power
    public boolean power;
    public int batteryPeriodMs;
    public String[] batteryCounters;

    public RecordConfig(int durationSeconds, boolean writeIntoFile, int fileWritePeriodMs,
        int bufferSizeMb, boolean ftrace, String[] ftraceEvents, String[] atraceCategories,
        String[] atraceApps, int ftraceDrainPeriodMs, int ftraceBufferSizeKb,
        boolean processMetadata, boolean scanAllProcessesOnStart, int procStatusPeriodMs,
        boolean sysStats, int meminfoPeriodMs, String[] meminfoCounters, int vmstatPeriodMs,
        String[] vmstatCounters, int statPeriodMs, String[] statCounters, boolean power,
        int batteryPeriodMs, String[] batteryCounters) {
      this.durationSeconds = durationSeconds;
      this.writeIntoFile = writeIntoFile;
      this.fileWritePeriodMs = fileWritePeriodMs;
      this.bufferSizeMb = bufferSizeMb;
      this.ftrace = ftrace;
      this.ftraceEvents = ftraceEvents;
      this.atraceCategories = atraceCategories;
      this.atraceApps = atraceApps;
      this.ftraceDrainPeriodMs = ftraceDrainPeriodMs;
      this.ftraceBufferSizeKb = ftraceBufferSizeKb;
      this.processMetadata = processMetadata;
      this.scanAllProcessesOnStart = scanAllProcessesOnStart;
      this.procStatusPeriodMs = procStatusPeriodMs;
      this.sysStats = sysStats;
      this.meminfoPeriodMs = meminfoPeriodMs;
      this.meminfoCounters = meminfoCounters;
      this.vmstatPeriodMs = vmstatPeriodMs;
      this.vmstatCounters = vmstatCounters;
      this.statPeriodMs = statPeriodMs;
      this.statCounters = statCounters;
      this.power = power;
      this.batteryPeriodMs = batteryPeriodMs;
      this.batteryCounters = batteryCounters;
    }
  }

  public static class TraceTime {
    public double startSec;
    public double endSec;
    public long lastUpdate;  // Epoch in seconds (Date.now() / 1000).

    public TraceTime(double startSec, double endSec, long lastUpdate) {
      this.startSec = startSec;
      this.endSec = endSec;
      this.lastUpdate = lastUpdate;
    }

    public static TraceTime getDefault() {
      return new TraceTime(0, 10, 0);
    }
  }

  public static class Status {
    public String msg;
    public long timestamp;  // Epoch in seconds (Date.now() / 1000).

    public Status(String msg, long timestamp) {
      this.msg = msg;
      this.timestamp = timestamp;
    }
  }


  public String route;
  public int nextId;

  /**
   * State of the ConfigEditor.
   */
  public RecordConfig recordConfig;
  public boolean displayConfigAsPbtxt;

  /**
   * Open traces.
   */
  public Map<String, EngineConfig> engines;
  public TraceTime traceTime;
  public TraceTime visibleTraceTime;
  public Map<String, TrackGroupState> trackGroups;
  public Map<String, TrackState> tracks;
  public List<String> scrollingTracks; // TS2J: LinkedHashSet ?
  public List<String> pinnedTracks; // TS2J: LinkedHashSet ?
  public Map<String, QueryConfig> queries;
  public PermalinkConfig permalink;
  public Status status;

  public State(String route, int nextId, RecordConfig recordConfig, boolean displayConfigAsPbtxt,
      Map<String, EngineConfig> engines, TraceTime traceTime, TraceTime visibleTraceTime,
      Map<String, TrackGroupState> trackGroups, Map<String, TrackState> tracks,
      List<String> scrollingTracks, List<String> pinnedTracks, Map<String, QueryConfig> queries,
      PermalinkConfig permalink, Status status) {
    this.route = route;
    this.nextId = nextId;
    this.recordConfig = recordConfig;
    this.displayConfigAsPbtxt = displayConfigAsPbtxt;
    this.engines = engines;
    this.traceTime = traceTime;
    this.visibleTraceTime = visibleTraceTime;
    this.trackGroups = trackGroups;
    this.tracks = tracks;
    this.scrollingTracks = scrollingTracks;
    this.pinnedTracks = pinnedTracks;
    this.queries = queries;
    this.permalink = permalink;
    this.status = status;
  }

  public void clear() {
    engines.clear();
    traceTime = TraceTime.getDefault();
    visibleTraceTime = TraceTime.getDefault();
    trackGroups.clear();
    tracks.clear();
    scrollingTracks.clear();
    pinnedTracks.clear();
    queries.clear();
    permalink = new PermalinkConfig(null, null);
    status = new Status("", 0);
  }

  public static State createEmptyState() {
    return new State(null, 0, createEmptyRecordConfig(), false, Maps.newHashMap(),
        TraceTime.getDefault(), TraceTime.getDefault(), Maps.newHashMap(), Maps.newHashMap(),
        Lists.newArrayList(), Lists.newArrayList(), Maps.newHashMap(),
        new PermalinkConfig(null, null), new Status("", 0));
  }

  public static RecordConfig createEmptyRecordConfig() {
    return new RecordConfig(10, false, 0, 10, false, new String[0], new String[0], new String[0], 0,
        2 * 1024, false, false, 0, false, 0, new String[0], 0, new String[0], 0, new String[0],
        false, 1000, new String[0]);
  }
}
