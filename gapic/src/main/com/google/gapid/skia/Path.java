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

import java.util.Arrays;

public class Path {
  private int[] buffer = new int[3 * 128];
  private int size = 0;
  private boolean closed = false;

  public void moveTo(float x, float y) {
    ensureCapacity();
    buffer[size++] = 0;
    buffer[size++] = Float.floatToIntBits(x);
    buffer[size++] = Float.floatToIntBits(y);
  }

  public void lineTo(float x, float y) {
    ensureCapacity();
    buffer[size++] = 1;
    buffer[size++] = Float.floatToIntBits(x);
    buffer[size++] = Float.floatToIntBits(y);
  }

  public void close() {
    closed = true;
  }

  private void ensureCapacity() {
    if (size + 3 > buffer.length) {
      buffer = Arrays.copyOf(buffer, buffer.length * 2);
    }
  }

  void draw(long ctx, int stroke, int fill, float lineWidth, int style) {
    Skia.drawPath(ctx, stroke, fill, lineWidth, style, buffer, size, closed);
  }
}
