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

import static com.google.gapid.util.Colors.hsl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;

import java.util.concurrent.ExecutionException;

public class ColorCache {
  private final Device device;
  private final Cache<RGB, Color> cache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .<RGB, Color>removalListener(e -> e.getValue().dispose())
      .build();

  public ColorCache(Device device) {
    this.device = device;
  }

  public Color get(float h, float s, float l) {
    return get(hsl(h, s, l));
  }

  public Color get(int r, int g, int b) {
    return get(new RGB(r, g, b));
  }


  private Color get(RGB rgb) {
    try {
      return cache.get(rgb, () -> new Color(device, rgb));
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  public void dispose() {
    cache.invalidateAll();
  }

}
