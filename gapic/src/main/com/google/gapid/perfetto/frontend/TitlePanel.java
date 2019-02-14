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

public class TitlePanel implements Panel {
  private final String title;

  public TitlePanel(String title) {
    this.title = title;
  }

  @Override
  public void renderCanvas(RenderContext ctx, int width) {
    ctx.setColor(new RGBA(0x28, 0x32, 0x3e, 0xFF), new RGBA(0xc7, 0xd0, 0xdb, 0xFF));
    ctx.drawRectangle(Fill, 0, 0, width, 28);
    ctx.drawText(title, 5, 5);
  }

  @Override
  public int getHeight() {
    return 28;
  }
}
