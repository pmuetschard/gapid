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

import static com.google.gapid.perfetto.base.Logging.assertExists;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class Actions {
  public static interface DeferredAction<T> extends Consumer<T> {
    // Just an alias.
  }

  public static interface StateAction extends DeferredAction<State> {
    // Just an alias.
  }

  public static StateAction navigate(String route) {
    return state -> state.route = route;
  }

  public static StateAction openTrace(String name) {
    return state -> {
      state.clear();
      String id = String.valueOf(state.nextId++);
      state.engines.put(id, new State.EngineConfig(id, false, name));
      state.route = "/viewer";
    };
  }

  public static StateAction addTrack(String argId, String engineId, String kind, String name,
      String trackGroup, Object config) {
    return state -> {
      String id = (argId == null) ? String.valueOf(state.nextId++) : argId;
      state.tracks.put(id, new State.TrackState(id, engineId, kind, name, trackGroup, null, config));
      if (State.SCROLLING_TRACK_GROUP.equals(trackGroup)) {
        state.scrollingTracks.add(id);
      } else if (trackGroup != null) {
        assertExists(state.trackGroups.get(trackGroup)).tracks.add(id);
      }
    };
  }

  public static StateAction addTrackGroup(String engineId, String name, String id,
      String summaryTrackId, boolean collapsed) {
    return state -> {
      state.trackGroups.put(id, new State.TrackGroupState(
          id, engineId, name, collapsed, Lists.newArrayList(), summaryTrackId));
    };
  }

  public static StateAction reqTrackData(String trackId, double start, double end, double resolution) {
    return state -> {
      state.tracks.get(trackId).dataReq = new State.TrackDataRequest(start, end, resolution);
    };
  }

  public static StateAction clearTrackDataReq(String trackId) {
    return state -> {
      state.tracks.get(trackId).dataReq = null;
    };
  }

  public static StateAction executeQuery(String queryId, String engineId, String query) {
    return state -> {
      state.queries.put(queryId, new State.QueryConfig(queryId, engineId, query));
    };
  }

  public static StateAction deleteQuery(String queryId) {
    return state -> {
      state.queries.remove(queryId);
    };
  }

  public static StateAction moveTrack(String srcId, MoveOp op, String dstId) {
    Consumer<List<String>> moveWithinTrackList = trackList -> {
      List<String> newList = Lists.newArrayList();
      for (String curTrackId : trackList) {
        if (curTrackId.equals(dstId) && op == MoveOp.Before) {
          newList.add(srcId);
        }
        if (!curTrackId.equals(srcId)) {
          newList.add(curTrackId);
        }
        if (curTrackId.equals(dstId) && op == MoveOp.After) {
          newList.add(srcId);
        }
      }

      trackList.clear();
      trackList.addAll(newList);
    };
    return state -> {
      moveWithinTrackList.accept(state.pinnedTracks);
      moveWithinTrackList.accept(state.scrollingTracks);
    };
  }

  public static enum MoveOp {
    Before, After;
  }

  public static StateAction toggleTrackPinned(String trackId) {
    return state -> {
      boolean isPinned = state.pinnedTracks.contains(trackId);
      String trackGroup = assertExists(state.tracks.get(trackId)).trackGroup;

      if (isPinned) {
        state.pinnedTracks.remove(trackId);
        if (State.SCROLLING_TRACK_GROUP.equals(trackGroup)) {
          state.scrollingTracks.add(0, trackId);
        }
      } else {
        if (State.SCROLLING_TRACK_GROUP.equals(trackGroup)) {
          state.scrollingTracks.remove(trackId);
        }
        state.pinnedTracks.add(trackId);
      }
    };
  }

  public static StateAction toggleTrackGroupCollapsed(String trackGroupId) {
    return state -> {
      State.TrackGroupState trackGroup = assertExists(state.trackGroups.get(trackGroupId));
      trackGroup.collapsed = !trackGroup.collapsed;
    };
  }

  public static StateAction setEngineReady(String engineId, boolean ready) {
    return state -> {
      state.engines.get(engineId).ready = ready;
    };
  }

  public static StateAction createPermalink() {
    return state -> {
      state.permalink = new State.PermalinkConfig(String.valueOf(state.nextId++), null);
    };
  }

  public static StateAction setPermalink(String requestId, String hash) {
    return state -> {
      if (requestId.equals(state.permalink.requestId)) {
        state.permalink.hash = hash;
      }
    };
  }

  public static StateAction loadPermalink(String hash) {
    return state -> {
      state.permalink = new State.PermalinkConfig(String.valueOf(state.nextId++), hash);
    };
  }

  public static StateAction clearPermalink() {
    return state -> {
      state.permalink = new State.PermalinkConfig(null, null);
    };
  }

  public static StateAction setTraceTime(State.TraceTime args) {
    return state -> {
      state.traceTime = args;
    };
  }

  public static StateAction setVisibleTraceTime(State.TraceTime args) {
    return state -> {
      state.visibleTraceTime = args;
    };
  }

  public static StateAction updateStatus(State.Status args) {
    return state -> {
      state.status = args;
    };
  }

  // TODO(hjd): Remove setState - it causes problems due to reuse of ids.
  public static StateAction setState(State newState) {
    return new SetStateAction(newState);
  }

  public static StateAction setConfig(State.RecordConfig config) {
    return state -> {
      state.recordConfig = config;
    };
  }

  // TODO(hjd): Parametrize this to increase type safety. See comments on
  // aosp/778194
  public static StateAction setConfigControl(String name, Object value) {
    return state -> {
      state.recordConfig.extras.put(name,  value);
    };
  }

  public static StateAction addConfigControl(String name, String... optionsToAdd) {
    return state -> {
      @SuppressWarnings("unchecked")
      List<String> options = (List<String>)state.recordConfig.extras.get(name);
      for (String option : optionsToAdd) {
        if (!options.contains(option)) {
          options.add(option);
        }
      }
    };
  }

  public static StateAction removeConfigControl(String name, String... optionsToRemove) {
    return state -> {
      @SuppressWarnings("unchecked")
      List<String> options = (List<String>)state.recordConfig.extras.get(name);
      options.removeAll(Arrays.asList(optionsToRemove));
    };
  }

  public static StateAction toggleDisplayConfigAsPbtxt() {
    return state -> {
      state.displayConfigAsPbtxt = !state.displayConfigAsPbtxt;
    };
  }

  public static class SetStateAction implements StateAction {
    public final State newState;

    public SetStateAction(State newState) {
      this.newState = newState;
    }

    @Override
    public void accept(State t) {
      // This has to be handled at a higher level since we can't
      // replace the whole tree here however we still need a method here
      // so it appears on the proxy Actions class.
      throw new RuntimeException("Called setState on StateActions.");
    }
  }
}
