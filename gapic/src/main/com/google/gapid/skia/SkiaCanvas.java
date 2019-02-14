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
package com.google.gapid.skia;

import static org.eclipse.swt.internal.DPIUtil.getDeviceZoom;

import com.google.gapid.glcanvas.GlCanvas;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.lwjgl.system.Library;

public abstract class SkiaCanvas extends GlCanvas {
  static {
    Library.loadSystem("gapid_skia");
  }

  public SkiaCanvas(Composite parent, int style) {
    super(parent, style);

    setCurrent();
    long ctxRef = Skia.newContext();
    addListener(SWT.Resize, e -> {
      setCurrent();
      Rectangle ca = getClientArea();
      Skia.resize(ctxRef, getDeviceZoom() / 100f, Math.max(1, ca.width), Math.max(1, ca.height));
    });
    addListener(SWT.Paint, e -> {
      long start = System.currentTimeMillis();
      setCurrent();
      draw(new RenderContext(ctxRef));
      Skia.flush(ctxRef);
      swapBuffers();
      System.err.println(System.currentTimeMillis() - start);
    });
    addListener(SWT.Dispose, e -> Skia.dispose(ctxRef));
  }

  @Override
  protected void terminate() {
    // Do nothing.
  }

  protected abstract void draw(RenderContext ctx);
}
