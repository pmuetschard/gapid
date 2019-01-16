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

import com.google.common.collect.Maps;
import com.google.gapid.perfetto.common.State.TrackState;

import java.util.Map;

public class Tracks {
  private final Map<String, Track<?, ?>> tracks = Maps.newHashMap();

  public Tracks() {
  }

  public void update() {
    // Remove all panels not in the state.
    tracks.keySet().retainAll(feGlobals().getState().tracks.keySet());
  }

  public Track<?, ?> get(String id) {
    return tracks.computeIfAbsent(id, key -> {
      TrackState state = getState(key);
      return Track.REGISTRY.get(state.kind).create(state);
    });
  }

  private static TrackState getState(String trackId) {
    return assertExists(feGlobals().getState().tracks.get(trackId));
  }
}
