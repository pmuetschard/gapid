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

import static com.google.gapid.perfetto.frontend.RenderContext.Style.Stroke;

import com.google.gapid.perfetto.common.TimeSpan;

import org.eclipse.swt.graphics.RGB;

import java.util.function.DoubleUnaryOperator;

public class GridLines {
  public static final int DESIRED_PX_PER_STEP = 80;

  public static void drawGridLines(RenderContext ctx, TimeScale x, TimeSpan timeSpan, int height) {
    double width = x.deltaTimeToPx(timeSpan.getDuration());
    double desiredSteps = width / DESIRED_PX_PER_STEP;
    double step = getGridStepSize(timeSpan.getDuration(), desiredSteps);
    double start = Math.round(timeSpan.start / step) * step;

    ctx.setColor(new RGB(0xda, 0xda, 0xda), null);

    ctx.path(Stroke, path -> {
      for (double sec = start; sec < timeSpan.end; sec += step) {
        int xPos = (int)Math.floor(x.timeToPx(sec));

        if (xPos >= 0 && xPos <= width) {
          path.moveTo(xPos, 0);
          path.lineTo(xPos, height);
        }
      }
    });
  }

  /**
   * Returns the step size of a grid line in seconds.
   * The returned step size has two properties:
   * (1) It is 1, 2, or 5, multiplied by some integer power of 10.
   * (2) The number steps in |range| produced by |stepSize| is as close as
   *     possible to |desiredSteps|.
   */
  public static double getGridStepSize(double range, double desiredSteps) {
    // First, get the largest possible power of 10 that is smaller than the
    // desired step size, and set it to the current step size.
    // For example, if the range is 2345ms and the desired steps is 10, then the
    // desired step size is 234.5 and the step size will be set to 100.
    double desiredStepSize = range / desiredSteps;
    double zeros = Math.floor(Math.log10(desiredStepSize));
    double initialStepSize = Math.pow(10, zeros);

    // This function first calculates how many steps within the range a certain
    // stepSize will produce, and returns the difference between that and
    // desiredSteps.
    DoubleUnaryOperator distToDesired = (evaluatedStepSize) ->
        Math.abs(range / evaluatedStepSize - desiredSteps);

    // We know that |initialStepSize| is a power of 10, and
    // initialStepSize <= desiredStepSize <= 10 * initialStepSize. There are four
    // possible candidates for final step size: 1, 2, 5 or 10 * initialStepSize.
    // We pick the candidate that minimizes distToDesired(stepSize).
    final double[] stepSizeMultipliers = {2, 5, 10};

    double minimalDistance = distToDesired.applyAsDouble(initialStepSize);
    double minimizingStepSize = initialStepSize;

    for (double multiplier : stepSizeMultipliers) {
      double newStepSize = multiplier * initialStepSize;
      double newDistance = distToDesired.applyAsDouble(newStepSize);
      if (newDistance < minimalDistance) {
        minimalDistance = newDistance;
        minimizingStepSize = newStepSize;
      }
    }
    return minimizingStepSize;
  }
}
