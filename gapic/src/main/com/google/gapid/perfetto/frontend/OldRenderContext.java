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

import com.google.common.collect.Lists;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;

import java.util.LinkedList;
import java.util.function.Consumer;

public class OldRenderContext {
  private final ColorCache colors;
  private final GC gc;

  private final LinkedList<Transform> transformStack = Lists.newLinkedList();

  public OldRenderContext(ColorCache colors, GC gc) {
    this.colors = colors;
    this.gc = gc;
  }

  public boolean needsDrawing(int y, int h) {
    Rectangle clip = gc.getClipping();
    return (y + h >= clip.y) && (y < clip.y + clip.height);
  }

  public Point textExtent(String text) {
    return gc.textExtent(text);
  }

  public void setColor(RGB stroke, RGB fill) {
    if (stroke != null) {
      gc.setForeground(colors.get(stroke));
    }
    if (fill != null) {
      gc.setBackground(colors.get(fill));
    }
  }

  public void drawLine(float x1, float y1, float x2, float y2) {
    gc.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
  }

  public void drawText(String text, float x, float y) {
    gc.drawText(text, (int)x, (int)y, SWT.DRAW_TRANSPARENT);
  }

  public void drawRectangle(Style style, float x, float y, float w, float h) {
    switch (style) {
      case Stroke:
        gc.drawRectangle((int)x, (int)y, (int)w, (int)h);
        break;
      case Fill:
        gc.fillRectangle((int)x, (int)y, (int)w, (int)h);
        break;
      case StrokeFill:
        gc.fillRectangle((int)x, (int)y, (int)w, (int)h);
        gc.drawRectangle((int)x, (int)y, (int)w, (int)h);
        break;
    }
  }

  public void drawImage(Image image, float sx, float sy, float sw, float sh, float dx, float dy, float dw, float dh) {
    gc.drawImage(image, (int)sx, (int)sy, (int)sw, (int)sh, (int)dx, (int)dy, (int)dw, (int)dh);
  }

  public void path(Style style, Consumer<Path> fun) {
    Path path = new Path(gc.getDevice());
    try {
      fun.accept(path);
      switch (style) {
        case Stroke:
          gc.drawPath(path);
          break;
        case Fill:
          gc.fillPath(path);
          break;
        case StrokeFill:
          gc.fillPath(path);
          gc.drawPath(path);
          break;
      }
    } finally {
      path.dispose();
    }
  }

  public void withTranslation(float x, float y, Runnable run) {
    Transform transform = new Transform(gc.getDevice());
    try {
      gc.getTransform(transform);
      transform.translate(x, y);
      gc.setTransform(transform);
      transformStack.add(transform);
      run.run();
      transformStack.removeLast(); // == transform
      gc.setTransform(transformStack.isEmpty() ? null : transformStack.getLast());
    } finally {
      transform.dispose();
    }
  }

  public void withClip(int x, int y, int width, int height, Runnable run) {
    Rectangle clip = gc.getClipping();
    try {
      gc.setClipping(x, y, width, height);
      run.run();
    } finally {
      gc.setClipping(clip);
    }
  }

  public void withAlpha(float alpha, Runnable run) {
    int current = gc.getAlpha();
    gc.setAlpha((int)(alpha * current));
    try {
      run.run();
    } finally {
      gc.setAlpha(current);
    }
  }

  public void withLineWidth(float w, Runnable run) {
    int current = gc.getLineWidth();
    gc.setLineWidth((int)w);
    try {
      run.run();
    } finally {
      gc.setLineWidth(current);
    }
  }

  public void withLineDash(int[] dash, Runnable run) {
    int[] current = gc.getLineDash();
    gc.setLineDash(dash);
    try {
      run.run();
    } finally {
      gc.setLineDash(current);
    }
  }

  public static enum Style {
    Stroke, Fill, StrokeFill,
  }
}
