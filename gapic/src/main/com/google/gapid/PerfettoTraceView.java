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
package com.google.gapid;

import com.google.gapid.models.Models;
import com.google.gapid.perfetto.QueryViewer;
import com.google.gapid.server.Client;
import com.google.gapid.widgets.Widgets;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

/**
 * Main view shown when a Perfetto trace is loaded.
 */
public class PerfettoTraceView extends Composite implements MainWindow.MainView {
  public PerfettoTraceView(Composite parent, Client client, Models models, Widgets widgets) {
    super(parent, SWT.NONE);
    setLayout(new FillLayout());
    new QueryViewer(this, client, models);
  }

  @Override
  public void updateViewMenu(MenuManager manager) {
    manager.removeAll();
  }
}
