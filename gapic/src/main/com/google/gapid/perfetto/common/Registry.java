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
package com.google.gapid.perfetto.common;

import com.google.common.collect.Maps;

import java.util.Map;

public class Registry<T extends Registry.HasKind> {
  private final Map<String, T> registry;

  public Registry() {
    this.registry = Maps.newHashMap();
  }

  public void register(T registrant) {
    String kind = registrant.getKind();
    if (registry.putIfAbsent(kind, registrant) != null) {
      throw new RuntimeException("Registrant " + kind + " already exists in the registry");
    }
  }

  public boolean has(String kind) {
    return registry.containsKey(kind);
  }

  public T get(String kind) {
    T registrant = registry.get(kind);
    if (registrant == null) {
      throw new RuntimeException(kind + " has not been registered.");
    }
    return registrant;
  }

  public static interface HasKind {
    public String getKind();
  }
}
