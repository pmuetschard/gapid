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

import com.google.common.collect.Sets;
import com.google.gapid.skia.RenderContext;

import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import java.util.Iterator;
import java.util.LinkedHashSet;

public interface Panel {
  public void renderCanvas(RenderContext ctx, int width);
  public int getHeight();

  @SuppressWarnings("unused")
  public default Dragger onDragStart(int x, int y) { return Dragger.NONE; }
  @SuppressWarnings("unused")
  public default Hover onMouseMove(int x, int y) { return Hover.NONE; }
  /** @return whether redraw is required, irregardless of the current hover. */
  public default boolean onMouseOut() { return false; }

  public static interface Dragger {
    public static final Dragger NONE = new Dragger() {
      @Override
      public boolean onDrag(Point dragStart, int x, int y) {
        return false;
      }

      @Override
      public boolean onDragEnd(Point dragStart, int x, int y) {
        return false;
      }
    };

    @SuppressWarnings("unused")
    public default Cursor getCursor(Display display) { return null; }
    /** @return whether a redraw is required */
    public boolean onDrag(Point dragStart, int x, int y);
    /** @return whether a redraw is required */
    public boolean onDragEnd(Point dragStart, int x, int y);
  }

  public static interface Hover {
    public static final Hover NONE = new Hover() {
      @Override
      public boolean redraw(Hover oldHover) {
        return oldHover != this && oldHover.redraw(this);
      }
    };
    public static final Hover REDRAW = new Hover() {
      @Override
      public boolean redraw(Hover oldHover) {
        return true;
      }
    };

    @SuppressWarnings("unused")
    public default Cursor getCursor(Display display) { return null; }
    public boolean redraw(Hover oldHover);
    public default void onClick() { /* do nothing */ }

    public default Hover or(Hover other) {
      return (this == NONE) ? other : this;
    }

    public static Hover redrawIf(boolean redraw) {
      return redraw ? REDRAW : NONE;
    }

    public static Hover cursor(int cursor) {
      return new Hover() {
        @Override
        public boolean redraw(Hover oldHover) {
          return false;
        }

        @Override
        public Cursor getCursor(Display display) {
          return display.getSystemCursor(cursor);
        }
      };
    }
  }

  public static class Group implements Panel {
    private LinkedHashSet<Panel> panels = Sets.newLinkedHashSet();

    public Group() {
    }

    public Group add(Panel child) {
      panels.add(child);
      return this;
    }

    public Group remove(Panel child) {
      panels.remove(child);
      return this;
    }

    public Group clear() {
      panels.clear();
      return this;
    }

    @Override
    public void renderCanvas(RenderContext ctx, int width) {
      int y = 0;
      for (Panel panel : panels) {
        int h = panel.getHeight();
        if (ctx.needsDrawing(0, y, width, h)) {
          ctx.withTranslation(0, y, () ->
            ctx.withClip(0, 0, width, h, () ->
              panel.renderCanvas(ctx, width)));
        }
        y += h;
      }
    }

    @Override
    public int getHeight() {
      int h = 0;
      for (Panel panel : panels) {
        h += panel.getHeight();
      }
      return h;
    }

    @Override
    public Dragger onDragStart(int x, int y) {
      int curY = 0;
      for (Panel panel : panels) {
        int h = panel.getHeight();
        if (curY <= y && curY + h > y) {
          return panel.onDragStart(x, y - curY);
        }
        curY += h;
      }
      return Dragger.NONE;
    }

    @Override
    public Hover onMouseMove(int x, int y) {
      boolean redraw = false;
      int curY = 0;
      for (Iterator<Panel> it = panels.iterator(); it.hasNext(); ) {
        Panel panel = it.next();
        int h = panel.getHeight();
        if (curY <= y && curY + h > y) {
          while (it.hasNext()) {
            redraw = it.next().onMouseOut() || redraw;
          }
          return panel.onMouseMove(x, y - curY).or(Hover.redrawIf(redraw));
        }
        redraw = panel.onMouseOut() || redraw;
        curY += h;
      }
      return Hover.redrawIf(redraw);
    }

    @Override
    public boolean onMouseOut() {
      boolean result = false;
      for (Panel panel : panels) {
        result = panel.onMouseOut() || result;
      }
      return result;
    }
  }
}
