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

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;

import java.util.function.Consumer;

public class RenderContext {
  private final long ctx;
  private int stroke = 0xFF000000;
  private int fill = 0xFF000000;
  private float lineWidth = 1;

  public RenderContext(long ctx) {
    this.ctx = ctx;
  }

  public void clear(RGBA color) {
    Skia.clear(ctx, rgba(color));
  }

  public boolean needsDrawing(int x, int y, int w, int h) {
    return !Skia.quickReject(ctx, x, y, w, h);
  }

  public Point textExtent(String text) {
    return new Point(50, 10); // TODO
  }

  public void setColor(RGBA newStroke, RGBA newFill) {
    if (newStroke != null) {
      stroke = rgba(newStroke);
    }
    if (newFill != null) {
      fill = rgba(newFill);
    }
  }

  public void drawLine(float x1, float y1, float x2, float y2) {
    Skia.drawLine(ctx, stroke, lineWidth, x1, y1, x2, y2);
  }

  public void drawText(String text, float x, float y) {
    Skia.drawText(ctx, stroke, text, x, y + 8);
  }

  public void drawRectangle(Style style, float x, float y, float w, float h) {
    Skia.drawRect(ctx, stroke, fill, lineWidth, style.mask, x, y, w, h);
  }

  public void drawCircle(Style style, float cx, float cy, float r) {
    Skia.drawCircle(ctx, stroke, fill, lineWidth, style.mask, cx, cy, r);
  }

  public void drawImage(Image image, float sx, float sy, float sw, float sh, float dx, float dy, float dw, float dh) {
    // TODO
  }

  public void path(Style style, Consumer<Path> fun) {
    Path path = new Path();
    fun.accept(path);
    path.draw(ctx, stroke, fill, lineWidth, style.mask);
  }

  public void withTranslation(float x, float y, Runnable run) {
    Skia.translate(ctx, x, y);
    try {
      run.run();
    } finally {
      Skia.restore(ctx);
    }
  }

  public void withClip(float x, float y, float w, float h, Runnable run) {
    Skia.clip(ctx, x, y, w, h);
    try {
      run.run();
    } finally {
      Skia.restore(ctx);
    }
  }

  public void withLineWidth(float w, Runnable run) {
    float current = lineWidth;
    lineWidth = w;
    try {
      run.run();
    } finally {
      lineWidth = current;
    }
  }

  public void withLineDash(int[] dash, Runnable run) {
    // TODO
    run.run();
  }

  private static int rgba(RGBA c) {
    RGB rgb = c.rgb;
    return (c.alpha << 24) | (rgb.red << 16) | (rgb.green << 8) | rgb.blue;
  }

  public static enum Style {
    Stroke(2), Fill(1), StrokeFill(3);

    public final int mask;

    private Style(int mask) {
      this.mask = mask;
    }
  }
}