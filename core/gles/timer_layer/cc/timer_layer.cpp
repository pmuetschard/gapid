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

#include <sstream>
#include <unordered_map>

#define GL_GLES_PROTOTYPES 0
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "core/cc/log.h"

namespace {

typedef void* (*PFNEGLGETNEXTLAYERPROCADDRESS)(void*, const char*);
typedef EGLContext (*PFNEGLGETCURRENTCONTEXT)();
typedef EGLBoolean (*PFNEGLSWAPBUFFERS)(EGLDisplay, EGLSurface);

PFNEGLGETCURRENTCONTEXT eglGetCurrentContext_next;
PFNEGLSWAPBUFFERS eglSwapBuffers_next;

PFNGLGETBOOLEANVPROC glGetBooleanv;
PFNGLDRAWARRAYSPROC glDrawArrays;
PFNGLDRAWELEMENTSPROC glDrawElements;
PFNGLGENQUERIESEXTPROC glGenQueriesEXT;
PFNGLBEGINQUERYEXTPROC glBeginQueryEXT;
PFNGLENDQUERYEXTPROC glEndQueryEXT;
// PFNGLQUERYCOUNTEREXTPROC glQueryCounterEXT;
PFNGLGETQUERYOBJECTIVEXTPROC glGetQueryObjectivEXT;
PFNGLGETQUERYOBJECTUI64VEXTPROC glGetQueryObjectui64vEXT;

#define MAX_QUERIES 1000

class Tracker {
 private:
  int last_query;
  GLuint queries[MAX_QUERIES];

 public:
  Tracker() : last_query(-1) {}

  void beforeSwap() {
    if (last_query < 0) {
      glGenQueriesEXT(MAX_QUERIES, queries);
      last_query = 0;
    } else if (last_query > 0) {
      glEndQueryEXT(GL_TIME_ELAPSED_EXT);
    }
  }

  void afterDraw() {
    if (last_query > 0 && last_query < MAX_QUERIES) {
      glEndQueryEXT(GL_TIME_ELAPSED_EXT);
      glBeginQueryEXT(GL_TIME_ELAPSED_EXT, queries[last_query++]);
    }
  }

  void afterSwap() {
    logData();

    last_query = 1;
    glBeginQueryEXT(GL_TIME_ELAPSED_EXT, queries[0]);
  }

 private:
  void logData() {
    if (last_query < 1) {
      return;
    }

    GLint available = 0;
    for (int i = 0; !available && i < 10000; i++) {
      glGetQueryObjectivEXT(queries[last_query - 1],
                            GL_QUERY_RESULT_AVAILABLE_EXT, &available);
    }
    if (!available) {
      GAPID_WARNING("Query data didn't become available");
      return;
    }

    GLboolean wasDisjoint = GL_FALSE;
    glGetBooleanv(GL_GPU_DISJOINT_EXT, &wasDisjoint);
    if (wasDisjoint) {
      GAPID_WARNING("GPU had disjoint work");
      return;
    }

    std::stringstream out;
    out << "=-=-=-=-=-=-=-=-=-=-=";
    for (int i = 0; i < last_query; i++) {
      GLuint64 value;
      glGetQueryObjectui64vEXT(queries[i], GL_QUERY_RESULT_EXT, &value);
      out << "," << value;
    }
    GAPID_INFO("%s", out.str().c_str());
  }
};

std::unordered_map<EGLContext, Tracker> trackers;

EGLAPI EGLBoolean EGLAPIENTRY eglSwapBuffers_layer(EGLDisplay display,
                                                   EGLSurface surface) {
  EGLContext ctx = eglGetCurrentContext_next();
  trackers[ctx].beforeSwap();
  EGLBoolean result = eglSwapBuffers_next(display, surface);
  trackers[ctx].afterSwap();
  return result;
}

GL_APICALL void GL_APIENTRY glDrawArrays_layer(GLenum mode, GLint first,
                                               GLsizei count) {
  EGLContext ctx = eglGetCurrentContext_next();
  glDrawArrays(mode, first, count);
  trackers[ctx].afterDraw();
}

GL_APICALL void GL_APIENTRY glDrawElements_layer(GLenum mode, GLsizei count,
                                                 GLenum type,
                                                 const void* indices) {
  EGLContext ctx = eglGetCurrentContext_next();
  glDrawElements(mode, count, type, indices);
  trackers[ctx].afterDraw();
}

}  // anonymous namespace

extern "C" {

void AndroidGLESLayer_Initialize(void* layer_id, PFNEGLGETNEXTLAYERPROCADDRESS glpa) {
  GAPID_INFO("InitializeLayer(%p, %p)", layer_id, glpa);
  eglGetCurrentContext_next = reinterpret_cast<PFNEGLGETCURRENTCONTEXT>(
      glpa(layer_id, "eglGetCurrentContext"));
  eglSwapBuffers_next =
      reinterpret_cast<PFNEGLSWAPBUFFERS>(glpa(layer_id, "eglSwapBuffers"));

  glGetBooleanv =
      reinterpret_cast<PFNGLGETBOOLEANVPROC>(glpa(layer_id, "glGetBooleanv"));
  glDrawArrays =
      reinterpret_cast<PFNGLDRAWARRAYSPROC>(glpa(layer_id, "glDrawArrays"));
  glDrawElements =
      reinterpret_cast<PFNGLDRAWELEMENTSPROC>(glpa(layer_id, "glDrawElements"));
  glGenQueriesEXT = reinterpret_cast<PFNGLGENQUERIESEXTPROC>(
      glpa(layer_id, "glGenQueriesEXT"));
  glBeginQueryEXT = reinterpret_cast<PFNGLBEGINQUERYEXTPROC>(
      glpa(layer_id, "glBeginQueryEXT"));
  glEndQueryEXT =
      reinterpret_cast<PFNGLENDQUERYEXTPROC>(glpa(layer_id, "glEndQueryEXT"));
  glGetQueryObjectivEXT = reinterpret_cast<PFNGLGETQUERYOBJECTIVEXTPROC>(
      glpa(layer_id, "glGetQueryObjectivEXT"));
  glGetQueryObjectui64vEXT = reinterpret_cast<PFNGLGETQUERYOBJECTUI64VEXTPROC>(
      glpa(layer_id, "glGetQueryObjectui64vEXT"));
}

void* AndroidGLESLayer_GetProcAddress(const char* name, void* next) {
  if (strcmp(name, "eglSwapBuffers") == 0) {
    return reinterpret_cast<void*>(&eglSwapBuffers_layer);
  } else if (strcmp(name, "glDrawArrays") == 0) {
    return reinterpret_cast<void*>(glDrawArrays_layer);
  } else if (strcmp(name, "glDrawElements") == 0) {
    return reinterpret_cast<void*>(glDrawElements_layer);
  }
  return next;
}
}
