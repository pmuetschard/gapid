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

import com.google.gapid.perfetto.common.Registry;
import com.google.gapid.perfetto.common.State.TrackState;

import java.util.function.Function;

public abstract class Track<Config, Data> {
  public static final Registry<TrackCreator<?>> REGISTRY = new Registry<TrackCreator<?>>();

  protected final TrackState trackState;

  public Track(TrackState trackState) {
    this.trackState = trackState;
  }

  @SuppressWarnings("unchecked")
  public Config getConfig() {
    return (Config)trackState.config;
  }

  @SuppressWarnings("unchecked")
  public Data getData() {
    return (Data)feGlobals().getTrackDataStore().get(trackState.id);
  }

  public String getTitle() {
    return trackState.name;
  }

  public int getHeight() {
    return 40;
  }

  public abstract void renderCanvas(RenderContext ctx);

  /**
   * @param x mouse x position
   * @param y mouse y position
   */
  public boolean onMouseMove(int x, int y) {
    return false;
  }

  public void onMouseOut() {
    // Do nothing.
  }

  public static interface TrackCreator<T extends Track<?, ?>> extends Registry.HasKind {
    public T create(TrackState state);
  }

  public static <T extends Track<?, ?>> void register(String kind, Function<TrackState, T> f) {
    REGISTRY.register(new TrackCreator<T>() {
      @Override
      public String getKind() {
        return kind;
      }

      @Override
      public T create(TrackState state) {
        return f.apply(state);
      }
    });
  }
}
