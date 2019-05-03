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

#include <alloca.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>

namespace timing {

namespace {

#define LOG_DEBUG(fmt, ...) \
  __android_log_print(ANDROID_LOG_DEBUG, "GAPID", fmt, ##__VA_ARGS__)

void* getLibProducer() {
  static void* libproducer = nullptr;
  if (libproducer == nullptr) {
    Dl_info me;
    dladdr((void*)getLibProducer, &me);
    if (me.dli_fname != nullptr) {
      const char* base = strrchr(me.dli_fname, '/');
      if (base != nullptr) {
        int baseLen = base - me.dli_fname + 1;
        char* name = static_cast<char*>(alloca(baseLen + 15 /*"libgapii.so"*/));
        memcpy(name, me.dli_fname, baseLen);
        strncpy(name + baseLen, "libproducer.so", 15);
        LOG_DEBUG("Loading producer at %s", name);
        libproducer = dlopen(name, RTLD_NOW);
      }
    }
  }
  return libproducer;
}

__attribute__((constructor)) void _startup() { getLibProducer(); }

typedef void (*PFN_send_event)(uint32_t, uint64_t, uint32_t, int64_t, int64_t,
                               const char*);

}  // namespace

void send_event(uint32_t pid, uint64_t queue_id, uint32_t queue_idx,
                int64_t start_ts, int64_t end_ts, const char* label) {
  static PFN_send_event se =
      (PFN_send_event)dlsym(getLibProducer(), "send_event");
  if (se != nullptr) {
    se(pid, queue_id, queue_idx, start_ts, end_ts, label);
  }
}

}  // namespace timing
