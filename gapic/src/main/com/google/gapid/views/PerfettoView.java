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
package com.google.gapid.views;

import static com.google.gapid.widgets.Widgets.createButton;
import static com.google.gapid.widgets.Widgets.createComposite;
import static com.google.gapid.widgets.Widgets.createLabel;
import static com.google.gapid.widgets.Widgets.scheduleIfNotDisposed;

import com.google.gapid.models.Capture;
import com.google.gapid.models.Models;
import com.google.gapid.perfetto.TraceView;
import com.google.gapid.util.Loadable.Message;
import com.google.gapid.widgets.Widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public class PerfettoView extends Composite implements Tab, Capture.Listener {
  private final Models models;
  private final Button goButton;
  private final Label message;

  public PerfettoView(Composite parent, Models models, Widgets widgets) {
    super(parent, SWT.NONE);
    this.models = models;

    setLayout(new GridLayout(1, false));

    Composite buttonAndLabel = createComposite(this, new GridLayout(2, false));
    goButton = createButton(buttonAndLabel, "Go", e -> getReport());
    goButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    goButton.setEnabled(false);
    message = createLabel(buttonAndLabel, "");
    message.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

    Widgets.withLayoutData(new TraceView(this, models, widgets),
        new GridData(SWT.FILL, SWT.FILL, true, true));

    models.capture.addListener(this);
    addListener(SWT.Dispose, e -> {
      models.capture.removeListener(this);
    });

    if (models.capture.isGraphics()) {
      onCaptureLoaded(null);
    }
  }

  @Override
  public void onCaptureLoadingStart(boolean maintainState) {
    goButton.setEnabled(false);
  }

  @Override
  public void onCaptureLoaded(Message error) {
    if (models.capture.isGraphics()) {
      goButton.setEnabled(true);
    }
  }

  private void getReport() {
    models.perfetto.captureReplay(
        models.capture.getData().path, models.devices.getReplayDevicePath(), msg ->
        scheduleIfNotDisposed(this, () -> {
            message.setText(msg);
            message.requestLayout();
          }));
  }

  @Override
  public Control getControl() {
    return this;
  }

  @Override
  public void reinitialize() {
    onCaptureLoadingStart(false);
    if (models.capture.isLoaded()) {
      onCaptureLoaded(null);
    }
  }
}
