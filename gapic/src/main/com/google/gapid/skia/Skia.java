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

class Skia {
  public static native long newContext();
  public static native boolean resize(long ctx, float zoom, int width, int height);

  public static native void clear(long ctx, int color);
  public static native void flush(long ctx);

  public static native void translate(long ctx, float dx, float dy);
  public static native void clip(long ctx, float x, float y, float w, float h);
  public static native boolean quickReject(long ctx, float x, float y, float w, float h);
  public static native void restore(long ctx);

  public static native void drawLine(long ctx, int color, float lineWidth, float x1, float y1, float x2, float y2);
  public static native void drawRect(long ctx, int stroke, int fill, float lineWidth, int maks, float x, float y, float w, float h);
  public static native void drawCircle(long ctx, int stroke, int fill, float lineWidth, int mask, float cx, float cy, float r);
  public static native void drawPath(long ctx, int stroke, int fill, float lineWidth, int mask, int[] data, int size, boolean closed);
  public static native void drawText(long ctx, int color, String text, float x, float y);

  public static native long dispose(long ctx);
}