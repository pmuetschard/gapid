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

import com.google.gapid.perfetto.frontend.Panel.Hover;
import com.google.gapid.skia.RenderContext;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class Button {
  private final int x, y, w, h;
  private final Image baseImage;
  protected final Image hoveredImage;
  protected final Runnable onClick;
  private boolean hovered = false;

  public Button(int x, int y, int w, int h, Image baseImage, Image hoveredImage, Runnable onClick) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.baseImage = baseImage;
    this.hoveredImage = hoveredImage;
    this.onClick = onClick;
  }

  public void renderCanvas(RenderContext ctx) {
    if (!hovered && baseImage == null) {
      return;
    }
    ctx.drawImage(hovered ? hoveredImage : baseImage, 0, 0, w, h, x, y, w, h);
  }

  public Hover onMouseMove(int mx, int my) {
    hovered = mx >= x && mx < x + w && my >= y && my < y + h;
    return hovered ? new Hover() {
      @Override
      public Cursor getCursor(Display display) {
        return display.getSystemCursor(SWT.CURSOR_HAND);
      }

      @Override
      public boolean redraw(Hover oldHover) {
        return hoveredImage != null;
      }

      @Override
      public void onClick() {
        onClick.run();
      }
    } : Hover.NONE;
  }

  public boolean onMouseOut() {
    boolean result = hovered;
    hovered = false;
    return result;
  }
}
