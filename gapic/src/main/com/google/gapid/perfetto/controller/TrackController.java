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
import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;

import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.Registry;
import com.google.gapid.perfetto.common.State.TrackDataRequest;
import com.google.gapid.perfetto.common.State.TrackState;

import java.util.List;
import java.util.function.Function;

public abstract class TrackController<Config, Data> extends Controller<TrackController.State> {
  public static final Registry<FactoryProvider> REGISTRY = new Registry<FactoryProvider>();

  private final String trackId;

  public TrackController(TrackControllerArgs args) {
    super(State.main);
    this.trackId = args.trackId;
  }

  public TrackState getTrackState() {
    return assertExists(cGlobals().getState().tracks.get(trackId));
  }

  @SuppressWarnings("unchecked")
  public Config getConfig() {
    return (Config)getTrackState().config;
  }

  public void publish(Data data) {
    cGlobals().publish(ControllerGlobals.PublishType.TrackData, new ControllerGlobals.TrackData<Data>(trackId, data));
  }

  public String tableName(String prefix) {
    // Derive table name from, since that is unique for each track.
    // Track ID can be UUID but '-' is not valid for sql table name.
    String idSuffix = trackId.replace('-', '_');
    return prefix + "_" + idSuffix;
  }

  @Override
  public List<ControllerInitializer<?>> run() {
    TrackDataRequest dataReq = getTrackState().dataReq;
    if (dataReq == null) {
      return null;
    }

    cGlobals().dispatch(Actions.clearTrackDataReq(trackId));
    onBoundsChange(dataReq.start, dataReq.end, dataReq.resolution);
    return null;
  }

  // Must be overridden by the track implementation. Is invoked when the track
  // frontend runs out of cached data. The derived track controller is expected
  // to publish new track data in response to this call.
  protected abstract void onBoundsChange(double start, double end, double resolution);

  public static class TrackControllerArgs {
    public final String trackId;
    public final Engine engine;

    public TrackControllerArgs(String trackId, Engine engine) {
      this.trackId = trackId;
      this.engine = engine;
    }
  }

  public static enum State {
    main;
  }

  // TS2J: sigh, sigh, sigh... It's a FactoryFactory :(
  public static interface FactoryProvider extends Registry.HasKind {
    public ControllerFactory<TrackController<?, ?>> factory(TrackControllerArgs args);
  }

  public static void register(String kind, Function<TrackControllerArgs, TrackController<?, ?>> f) {
    REGISTRY.register(new FactoryProvider() {
      @Override
      public String getKind() {
        return kind;
      }

      @Override
      public ControllerFactory<TrackController<?, ?>> factory(TrackControllerArgs args) {
        return () -> f.apply(args);
      }
    });
  }
}
