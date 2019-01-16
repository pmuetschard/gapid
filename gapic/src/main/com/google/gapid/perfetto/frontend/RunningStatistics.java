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

import java.util.Arrays;

public class RunningStatistics {
  private int count = 0;
  private double mean = 0;
  private double lastValue = 0;
  private double[] buffer = new double[10];
  private int pos = 0;

  public RunningStatistics() {
  }

  public void addValue(double value) {
    lastValue = value;
    buffer[pos++] = value;
    if (pos >= buffer.length) {
      pos = 0;
    }
    count++;
    mean = (mean * count + value) / count;
  }

  public double getMean() {
    return mean;
  }

  public int getCount() {
    return count;
  }

  public double getBufferMean() {
    if (count < buffer.length) {
      return Arrays.stream(buffer, 0, count).reduce(0, (a, b) -> a + b) / count;
    } else {
      return Arrays.stream(buffer).reduce(0, (a, b) -> a + b) / buffer.length;
    }
  }

  public int getBufferSize() {
    return (count < buffer.length) ? count : buffer.length;
  }

  public int getMaxBufferSize() {
    return buffer.length;
  }

  public double getLast() {
    return lastValue;
  }

  @Override
  public String toString() {
    return String.format("Last: %.2fms | Avg: %.2fms | Avg%d: %.2fms",
        getLast(), getMean(), getBufferSize(), getBufferMean());
  }
}
