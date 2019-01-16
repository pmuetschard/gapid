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

import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;

import com.google.common.collect.Lists;
import com.google.gapid.perfetto.base.Remote;
import com.google.gapid.perfetto.common.Actions.DeferredAction;
import com.google.gapid.perfetto.common.State.EngineConfig;

import java.util.List;
import java.util.Map;

public class AppController extends Controller<AppController.State> {
  public AppController() {
    super(State.main);
  }

  @Override
  public List<ControllerInitializer<?>> run() {
    List<ControllerInitializer<?>> childControllers = Lists.newArrayList();
    for (Map.Entry<String, EngineConfig> e : ControllerGlobals.cGlobals().getState().engines.entrySet()) {
      childControllers.add(child(e.getValue().id, new TraceController.TraceControllerArgs(e.getValue().id)));
    }
    return childControllers;
  }

  public static enum State {
    main;
  }

  public static class Controllers implements Remote<Void> {
    @Override
    public Object invoke(Void method, Object[] args) {
      cGlobals().dispatch((DeferredAction<?>)args[0]);
      return null;
    }
  }
}
