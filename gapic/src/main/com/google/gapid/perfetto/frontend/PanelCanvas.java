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

import com.google.gapid.perfetto.frontend.Panel.Hover;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;

public class PanelCanvas extends Canvas {
  private final Panel panel;
  private final ScrollBar bar;
  private Point mouseDown = null, dragStart = null;
  private Panel.Hover hover = Panel.Hover.NONE;
  private Panel.Dragger dragger = Panel.Dragger.NONE;

  public PanelCanvas(Composite parent, ColorCache colorCache, Panel panel) {
    super(parent, SWT.V_SCROLL | SWT.NO_BACKGROUND);
    this.panel = panel;
    this.bar = getVerticalBar();

    addListener(SWT.Paint, e -> {
      Rectangle size = getClientArea();
      e.gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
      e.gc.fillRectangle(size);

      int offset = bar.isVisible() ? bar.getSelection() : 0;
      RenderContext ctx = new RenderContext(colorCache, e.gc);
      ctx.withTranslation(0, -offset, () -> panel.renderCanvas(ctx, size.width));
    });
    addListener(SWT.MouseDown, e -> {
      if (e.button == 1) {
        mouseDown = new Point(e.x, e.y + getScrollOffset());
      }
    });
    addListener(SWT.MouseMove, e -> {
      if (mouseDown != null) {
        if (dragStart == null) {
          dragStart = mouseDown;
          dragger = panel.onDragStart(dragStart.x, dragStart.y);
          if (dragger.onDrag(dragStart, e.x, e.y + getScrollOffset())) {
            redraw();
          }
          setCursor(dragger.getCursor(getDisplay()));
        } else if (dragger.onDrag(dragStart, e.x, e.y + getScrollOffset())) {
          setCursor(dragger.getCursor(getDisplay()));
          redraw();
        }
      } else {
        setHover(panel.onMouseMove(e.x, e.y + getScrollOffset()), false);
      }
    });
    addListener(SWT.MouseExit, e -> {
      setHover(Panel.Hover.NONE, panel.onMouseOut());
    });
    addListener(SWT.MouseUp, e -> {
      if (e.button == 1) {
        mouseDown = null;
        if (dragStart != null) {
          dragger.onDragEnd(dragStart, e.x, e.y);
          dragStart = null;
          setCursor(null);
        } else {
          hover.onClick();
          setHover(Hover.NONE, false);
        }
      }
    });

    addListener(SWT.Resize, e -> updateScrollbar());
    bar.addListener(SWT.Selection, e -> redraw());
  }

  private void setHover(Panel.Hover newHover, boolean forceRedraw) {
    if (forceRedraw || newHover.redraw(hover)) {
      redraw();
    }
    hover = newHover;
    setCursor(hover.getCursor(getDisplay()));
  }

  @Override
  public Point computeSize(int wHint, int hHint, boolean changed) {
    return new Point((wHint == SWT.DEFAULT) ? 300 : wHint, panel.getHeight());
  }

  @Override
  public void layout(boolean changed) {
    super.layout(changed);
    updateScrollbar();
  }

  private void updateScrollbar() {
    Rectangle size = getClientArea();
    int height = panel.getHeight();
    if (size.height < height) {
      if (bar.isVisible()) {
        int sel = Math.min(bar.getSelection(), height - size.height);
        bar.setValues(sel, 0, height, size.height, 10, 100);
      } else {
        bar.setVisible(true);
        bar.setValues(0, 0, height, size.height, 10, 100);
      }
    } else {
      bar.setVisible(false);
    }
  }

  private int getScrollOffset() {
    return bar.isVisible() ? bar.getSelection() : 0;
  }
}
