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
package com.google.gapid.perfetto.base;

import org.eclipse.swt.graphics.Point;

public class BinarySearch {
  private BinarySearch() {
  }

  public static int search(double[] haystack, double needle) {
    return search(haystack, needle, 0, haystack.length);
  }

  // TS2J: can we use Arrays.binarySearch here?
  private static int search(double[] haystack, double needle, int i, int j) {
    if (i == j) {
      return -1;
    } else if (i + 1 == j) {
      return (needle >= haystack[i]) ? i : -1;
    }

    int mid = (i + j) >>> 1;
    if (needle < haystack[mid]) {
      return search(haystack, needle, i, mid);
    } else {
      return search(haystack, needle, mid, j);
    }
  }

  public static Point searchSegment(double[] haystack, double needle) {
    if (haystack.length == 0) {
      return new Point(-1, -1);
    }

    int left = search(haystack, needle);
    if (left == -1) {
      return new Point(-1, 0);
    } else if (left + 1 == haystack.length) {
      return new Point(left, -1);
    } else {
      return new Point(left, left + 1);
    }
  }
}
