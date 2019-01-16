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
package com.google.gapid.perfetto.controller;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class Controller<StateType> {
  private boolean stateChanged = false;
  private boolean inRunner = false;
  private StateType state;
  private Map<String, Controller<?>> children = Maps.newHashMap();

  public Controller(StateType initialState) {
    this.state = initialState;
  }

  public abstract List<ControllerInitializer<?>> run();

  public void onDestroy() {
    // Do nothing.
  }

  // Invokes the current controller subtree, recursing into children.
  // While doing so handles lifecycle of child controllers.
  // This method should be called only by the runControllers() method in
  // globals.ts. Exposed publicly for testing.
  public boolean invoke() {
    if (inRunner) {
      throw new RuntimeException("Reentrancy in Controller");
    }
    stateChanged = false;
    inRunner = true;
    List<ControllerInitializer<?>> resArray = run();
    boolean triggerAnotherRun = stateChanged;
    stateChanged = false;

    Map<String, ControllerInitializer<?>> nextChildren = Maps.newHashMap();
    if (resArray != null) {
      for (ControllerInitializer<?> childConfig : resArray) {
        if (nextChildren.containsKey(childConfig.id)) {
          throw new RuntimeException("Duplicate children controller: " + childConfig.id);
        }
        nextChildren.put(childConfig.id, childConfig);
      }
    }
    List<Runnable> dtors = Lists.newArrayList();
    List<Supplier<Boolean>> runners = Lists.newArrayList();
    for (String key : children.keySet()) {
      if (nextChildren.containsKey(key)) {
        continue;
      }
      Controller<?> instance = children.get(key);
      this.children.remove(key);
      dtors.add(() -> instance.onDestroy());
    }
    for (ControllerInitializer<?> nextChild : nextChildren.values()) {
      if (!children.containsKey(nextChild.id)) {
        final Controller<?> instance = nextChild.build();
        children.put(nextChild.id, instance);
      }
      final Controller<?> instance = children.get(nextChild.id);
      runners.add(() -> instance.invoke());
    }

    // Invoke all onDestroy()s.
    for (final Runnable dtor : dtors) {
      dtor.run();
    }

    // Invoke all runner()s.
    for (final Supplier<Boolean> runner : runners) {
      final boolean recursiveRes = runner.get();
      triggerAnotherRun = triggerAnotherRun || recursiveRes;
    }

    inRunner = false;
    return triggerAnotherRun;
  }

  public void setState(StateType state) {
    if (!inRunner) {
      throw new RuntimeException("Cannot setState() outside of the run() method");
    }
    this.stateChanged = state != this.state;
    this.state = state;
  }

  public StateType getState() {
    return state;
  }

  public static <C extends Controller<?>> ControllerInitializer<C> child(
      String id, ControllerFactory<C> factory) {
    return new ControllerInitializer<C>(id, factory);
  }

  public static interface ControllerFactory<C extends Controller<?>> {
    public C build();
  }

  public static class ControllerInitializer<C extends Controller<?>> {
    public final String id;
    public final ControllerFactory<C> factory;

    public ControllerInitializer(String id, ControllerFactory<C> factory) {
      this.id = id;
      this.factory = factory;
    }

    public C build() {
      return factory.build();
    }
  }
}
