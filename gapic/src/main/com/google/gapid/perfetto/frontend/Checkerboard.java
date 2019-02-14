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

import static com.google.gapid.skia.RenderContext.Style.Fill;

import com.google.gapid.skia.RenderContext;

import org.eclipse.swt.graphics.RGBA;

public class Checkerboard {
  private static final int SLICE_HEIGHT = 32;
  private static final int  TRACK_PADDING = 5;

  private Checkerboard() {
  }

  public static void checkerboard(RenderContext ctx, int leftPx, int rightPx) {
    int widthPx = rightPx - leftPx;
    // TS2J: ctx.font = '12px Google Sans';
    ctx.setColor(new RGBA(0x66, 0x66, 0x66, 0xFF), new RGBA(0xEE, 0xEE, 0xEE, 0xFF));
    ctx.drawRectangle(Fill, leftPx, TRACK_PADDING, widthPx, SLICE_HEIGHT);
    ctx.drawText("loading...", leftPx + widthPx / 2f, TRACK_PADDING + SLICE_HEIGHT / 2f);
  }

  public static void checkerboardExcept(
      RenderContext ctx, int startPx, int endPx, int leftPx, int rightPx) {
    // [leftPx, rightPx] doesn't overlap [startPx, endPx] at all:
    if (rightPx <= startPx || leftPx >= endPx) {
      checkerboard(ctx, startPx, endPx);
      return;
    }

    // Checkerboard [startPx, leftPx]:
    if (leftPx > startPx) {
      checkerboard(ctx, startPx, leftPx);
    }

    // Checkerboard [rightPx, endPx]:
    if (rightPx < endPx) {
      checkerboard(ctx, rightPx, endPx);
    }
  }
}
