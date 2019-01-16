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
package com.google.gapid.perfetto;

import com.google.gapid.models.Capture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Actions.DeferredAction;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.controller.AppController;
import com.google.gapid.perfetto.controller.ControllerGlobals;
import com.google.gapid.perfetto.frontend.FrontEnd;
import com.google.gapid.perfetto.frontend.FrontEndGlobals;
import com.google.gapid.perfetto.frontend.ViewerPage;
import com.google.gapid.perfetto.tracks.ChromeSlices;
import com.google.gapid.perfetto.tracks.Counter;
import com.google.gapid.perfetto.tracks.CpuFrequency;
import com.google.gapid.perfetto.tracks.CpuSlices;
import com.google.gapid.perfetto.tracks.ProcessSummary;
import com.google.gapid.server.Client;
import com.google.gapid.views.StatusBar;
import com.google.gapid.widgets.Theme;

import org.eclipse.swt.widgets.Composite;

public class TraceView {
  static {
    ChromeSlices.init();
    Counter.init();
    CpuFrequency.init();
    CpuSlices.init();
    ProcessSummary.init();
  }

  public static Composite show(Composite parent, StatusBar status, Client client, Capture capture, Theme theme) {
    ViewerPage page = new ViewerPage(parent, theme);

    FrontEnd fe = new FrontEnd(page);
    ControllerGlobals.INSTANCE.initialize(
        page, new Engine.GapisEngine(status, client, capture.getData().path), new AppController(), fe);
    FrontEndGlobals.INSTANCE.initialize(page, (m, args) -> {
      ControllerGlobals.INSTANCE.dispatch((DeferredAction<?>)args[0]);
      return null;
    });

    ControllerGlobals.INSTANCE.dispatch(Actions.openTrace(capture.getName()));
    return page;
  }
}
