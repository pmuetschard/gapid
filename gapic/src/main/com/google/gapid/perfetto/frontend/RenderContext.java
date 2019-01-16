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

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;

import java.util.LinkedList;
import java.util.function.Consumer;

public class RenderContext {
  public final ColorCache colors;
  public final GC gc;

  private final LinkedList<Transform> transformStack = Lists.newLinkedList();

  public RenderContext(ColorCache colors, GC gc) {
    this.colors = colors;
    this.gc = gc;
  }

  public void path(Consumer<Path> fun) {
    Path path = new Path(gc.getDevice());
    try {
      fun.accept(path);
    } finally {
      path.dispose();
    }
  }

  public Color systemColor(int name) {
    return gc.getDevice().getSystemColor(name);
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
}
