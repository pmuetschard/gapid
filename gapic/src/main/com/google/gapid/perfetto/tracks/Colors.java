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
package com.google.gapid.perfetto.tracks;

import static com.google.gapid.util.Colors.hsl;

import com.google.gapid.perfetto.frontend.ThreadDesc;

import org.eclipse.swt.graphics.RGB;

public class Colors {
  private static final HSL[] MD_PALETTE = new HSL[] {
      new HSL("red", 4, 90, 58),
      new HSL("pink", 340, 82, 52),
      new HSL("purple", 291, 64, 42),
      new HSL("deep purple", 262, 52, 47),
      new HSL("indigo", 231, 48, 48),
      new HSL("blue", 207, 90, 54),
      new HSL("light blue", 199, 98, 48),
      new HSL("cyan", 187, 100, 42),
      new HSL("teal", 174, 100, 29),
      new HSL("green", 122, 39, 49),
      new HSL("light green", 88, 50, 53),
      new HSL("lime", 66, 70, 54),
      new HSL("amber", 45, 100, 51),
      new HSL("orange", 36, 100, 50),
      new HSL("deep orange", 14, 100, 57),
      new HSL("brown", 16, 25, 38),
      new HSL("blue gray", 200, 18, 46),
      new HSL("yellow", 54, 100, 62),
  };
  private static final HSL GRAY_COLOR = new HSL("gray", 0, 0, 62);

  private Colors() {
  }

  public static float hueForCpu(int cpu) {
    return (128 + (32 * cpu)) % 256;
  }

  public static HSL colorForTid(int tid) {
    return MD_PALETTE[hash(String.valueOf(tid), MD_PALETTE.length)];
  }

  public static HSL colorForThread(ThreadDesc thread) {
    if (thread == null) {
      return GRAY_COLOR;
    }
    int tid = thread.pid != 0 ? thread.pid : thread.tid;
    return colorForTid(tid);
  }

  private static int hash(String s, int max) {
    int hash = 0x811c9dc5;
    for (int i = 0; i < s.length(); i++) {
      hash ^= s.charAt(i);
      hash *= 16777619;
    }
    return Math.abs(hash) % max;
  }

  public static class HSL {
    @SuppressWarnings("unused") // TS2J
    public final String name;
    public final int h, s, l;

    public HSL(String name, int h, int s, int l) {
      this.name = name;
      this.h = h;
      this.s = s;
      this.l = l;
    }

    public HSL adjusted(int newH, int newS, int newL) {
      return new HSL(name, clamp(newH, 0, 360), clamp(newS, 0, 100), clamp(newL, 0, 100));
    }

    public RGB swt() {
      return hsl(h, s / 100f, l / 100f);
    }

    private static int clamp(int x, int min, int max) {
      return Math.min(Math.max(x, min), max);
    }
  }
}
