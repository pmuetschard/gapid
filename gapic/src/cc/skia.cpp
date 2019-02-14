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

#include <jni.h>

#include "GrBackendSurface.h"
#include "SkCanvas.h"
#include "GrContext.h"
#include "GrGLInterface.h"
#include "SkSurface.h"
#include "GrGLInterface.h"
#include "GrGLUtil.h"
#include "SkFont.h"

class SkiaContext {
 private:
  sk_sp<const GrGLInterface> interface;
  sk_sp<GrContext> context;
  sk_sp<SkSurface> surface;
  SkCanvas* canvas;
  GrBackendRenderTarget target;
  GrGLFramebufferInfo info;
  SkPaint stroke, fill;
  SkFont font;
 public:
  SkiaContext() {
    interface = GrGLMakeNativeInterface();
    context = GrContext::MakeGL(interface);

    info.fFBOID = 0;
    info.fFormat = GR_GL_RGBA8;

    stroke.setAntiAlias(true);
    stroke.setColor(SK_ColorBLACK);
    stroke.setStyle(SkPaint::kStroke_Style);

    fill.setAntiAlias(true);
    fill.setColor(SK_ColorBLACK);
    fill.setStyle(SkPaint::kFill_Style);
  }

  bool resize(float zoom, int width, int height) {
    target = GrBackendRenderTarget(zoom * width, zoom * height, 4, 8, info);
    SkSurfaceProps props(SkSurfaceProps::kLegacyFontHost_InitType);
    SkColorType colorType = kRGBA_8888_SkColorType;
    surface = SkSurface::MakeFromBackendRenderTarget(
        context.get(), target, kBottomLeft_GrSurfaceOrigin, colorType, nullptr, &props);
    if (surface != nullptr) {
      canvas = surface->getCanvas();
      canvas->scale(zoom, zoom);
      return true;
    }
    return false;
  }

  void clear(SkColor color) {
    if (surface) {
      canvas->clear(color);
    }
  }

  void flush() {
    if (surface) {
      canvas->flush();
    }
  }

  void translate(SkScalar dx, SkScalar dy) {
    if (surface) {
      canvas->save();
      canvas->translate(dx, dy);
    }
  }

  void clip(SkScalar x, SkScalar y, SkScalar w, SkScalar h) {
    if (surface) {
      canvas->save();
      canvas->clipRect(SkRect::MakeXYWH(x, y, w, h), SkClipOp::kIntersect, true);
    }
  }

  bool quickReject(SkScalar x, SkScalar y, SkScalar w, SkScalar h) {
    return surface && canvas->quickReject(SkRect::MakeXYWH(x, y, w, h));
  }

  void restore() {
    if (surface) {
      canvas->restore();
    }
  }

  void drawLine(SkColor color, SkScalar lWidth, SkScalar x1, SkScalar y1, SkScalar x2, SkScalar y2) {
    if (surface) {
      stroke.setColor(color);
      stroke.setStrokeWidth(lWidth);
      canvas->drawLine({x1, y1}, {x2, y2}, stroke);
    }
  }

  void drawRect(SkColor s, SkColor f, SkScalar lWidth, int style, SkScalar x, SkScalar y, SkScalar w, SkScalar h) {
    if (surface) {
      SkRect rect = SkRect::MakeXYWH(x, y, w, h);
      if ((style & 1)) {
        fill.setColor(f);
        canvas->drawRect(rect, fill);
      }
      if ((style & 2)) {
        stroke.setColor(s);
        stroke.setStrokeWidth(lWidth);
        canvas->drawRect(rect, stroke);
      }
    }
  }

  void drawCircle(SkColor s, SkColor f, SkScalar lWidth, int style, SkScalar cx, SkScalar cy, SkScalar r) {
    if (surface) {
      if ((style & 1)) {
        fill.setColor(f);
        canvas->drawCircle(cx, cy, r, fill);
      }
      if ((style & 2)) {
        stroke.setColor(s);
        stroke.setStrokeWidth(lWidth);
        canvas->drawCircle(cx, cy, r, stroke);
      }
    }
  }

  struct element {
    uint32_t type;
    float x;
    float y;
  };

  void drawPath(SkColor s, SkColor f, SkScalar lWidth, int style, const element* elements, int count, bool closed) {
    if (surface) {
      SkPath path;
      for (int i = 0; i < count; i++, elements++) {
        switch (elements->type) {
          case 0:
            path.moveTo(elements->x, elements->y);
            break;
          case 1:
            path.lineTo(elements->x, elements->y);
            break;
        }
      }
      if (closed) {
        path.close();
      }

      if ((style & 1)) {
        fill.setColor(f);
        canvas->drawPath(path, fill);
      }
      if ((style & 2)) {
        stroke.setColor(s);
        stroke.setStrokeWidth(lWidth);
        canvas->drawPath(path, stroke);
      }
    }
  }

  void drawText(SkColor color, const void* utf8, int length, SkScalar x, SkScalar y) {
    if (surface) {
      fill.setColor(color);
      canvas->drawSimpleText(utf8, length, kUTF8_SkTextEncoding, x, y, font, fill);
    }
  }
};

#define CTX(ref) ((SkiaContext*)(ref))
#define JNI(ret, name, ...)                                        \
    extern "C" JNIEXPORT ret Java_com_google_gapid_skia_Skia_##name \
    (JNIEnv* env, jclass clazz, ##__VA_ARGS__)

JNI(jlong, newContext) {
  return (jlong)new SkiaContext();
}

JNI(jboolean, resize, jlong ref, jfloat zoom, jint width, jint height) {
  return CTX(ref)->resize(zoom, width, height);
}

JNI(void, clear, jlong ref, jint color) {
  CTX(ref)->clear(color);
}

JNI(void, flush, jlong ref) {
  CTX(ref)->flush();
}

JNI(void, translate, jlong ref, jfloat dx, jfloat dy) {
  CTX(ref)->translate(dx, dy);
}

JNI(void, clip, jlong ref, jfloat x, jfloat y, jfloat w, jfloat h) {
  CTX(ref)->clip(x, y, w, h);
}

JNI(jboolean, quickReject, jlong ref, jfloat x, jfloat y, jfloat w, jfloat h) {
  return CTX(ref)->quickReject(x, y, w, h);
}

JNI(void, restore, jlong ref) {
  CTX(ref)->restore();
}

JNI(void, drawLine, jlong ref, jint color, jfloat lWidth, jfloat x1, jfloat y1, jfloat x2, jfloat y2) {
  CTX(ref)->drawLine(color, lWidth, x1, y1, x2, y2);
}

JNI(void, drawRect, jlong ref, jint stroke, jint fill, jfloat lWidth, jint style, jfloat x, jfloat y, jfloat w, jfloat h) {
  CTX(ref)->drawRect(stroke, fill, lWidth, style, x, y, w, h);
}

JNI(void, drawCircle, jlong ref, jint stroke, jint fill, jfloat lWidth, jint style, jfloat cx, jfloat cy, jfloat r) {
  CTX(ref)->drawCircle(stroke, fill, lWidth, style, cx, cy, r);
}

JNI(void, drawPath, jlong ref, jint stroke, jint fill, jfloat lWidth, jint style, jintArray data, jint size, jboolean closed) {
  jint* bytes = env->GetIntArrayElements(data, 0);
  CTX(ref)->drawPath(stroke, fill, lWidth, style, (SkiaContext::element*)bytes, size / 3, closed);
  env->ReleaseIntArrayElements(data, bytes, 0);
}

JNI(void, drawText, jlong ref, jint color, jstring text, jfloat x, jfloat y) {
  const char* utf8 = env->GetStringUTFChars(text, nullptr);
  int len = env->GetStringUTFLength(text);
  CTX(ref)->drawText(color, (void*)utf8, len, x, y);
  env->ReleaseStringUTFChars(text, utf8);
}

JNI(void, dispose, jlong ref) {
  delete(CTX(ref));
}
