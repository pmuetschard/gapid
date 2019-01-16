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

import static com.google.gapid.perfetto.base.Logging.assertExists;
import static com.google.gapid.widgets.Widgets.scheduleIfNotDisposed;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gapid.perfetto.base.Remote;
import com.google.gapid.perfetto.common.Actions.DeferredAction;
import com.google.gapid.perfetto.common.Actions.SetStateAction;
import com.google.gapid.perfetto.common.Actions.StateAction;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.QueryResponse;
import com.google.gapid.perfetto.common.State;
import com.google.gapid.perfetto.frontend.FrontEnd;
import com.google.gapid.perfetto.frontend.FrontEnd.Method;
import com.google.gapid.perfetto.frontend.ViewerPage;
import com.google.gapid.rpc.Rpc;
import com.google.gapid.rpc.Rpc.Result;
import com.google.gapid.rpc.RpcException;
import com.google.gapid.rpc.UiCallback;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ControllerGlobals {
  private static final Logger LOG = Logger.getLogger(ControllerGlobals.class.getName());

  public static final ControllerGlobals INSTANCE = new ControllerGlobals();
  public static ControllerGlobals cGlobals() { return INSTANCE; }

  private ViewerPage view;
  private Engine engine;
  private State state;
  private Controller<?> rootController;
  private Remote<FrontEnd.Method> frontend;
  private boolean runningControllers = false;
  private List<DeferredAction<?>> queuedActions = Lists.newArrayList();

  public ControllerGlobals() {
  }

  public <T> void thenOnUiThread(ListenableFuture<T> future, Consumer<T> callback) {
    Rpc.listen(future, new UiCallback<T, T>(view, LOG) {
      @Override
      protected T onRpcThread(Result<T> result) throws RpcException, ExecutionException {
        return result.get();
      }

      @Override
      protected void onUiThread(T result) {
        callback.accept(result);
      }
    });
  }

  public ListenableFuture<Void> onUiThread(Runnable run) {
    SettableFuture<Void> result = SettableFuture.create();
    scheduleIfNotDisposed(view, () -> {
      try {
        run.run();
        result.set(null);
      } catch (Exception e) {
        result.setException(e);
      }
    });
    return result;
  }

  public void initialize(ViewerPage page, Engine newEngine, Controller<?> newRootController,  Remote<FrontEnd.Method> frontendProxy) {
    view = page;
    engine = newEngine;
    rootController = newRootController;
    frontend = frontendProxy;
    state = State.createEmptyState();
  }

  public void dispatch(DeferredAction<?>... actions) {
    dispatch(Arrays.asList(actions));
  }

  public void dispatch(Collection<DeferredAction<?>> actions) {
    queuedActions.addAll(actions);
    if (runningControllers) {
      return;
    }
    runControllers();
  }

  private void runControllers() {
    if (runningControllers) {
      throw new RuntimeException("Re-entrant call detected");
    }

    boolean runAgain = false;
    /*
    String summary = queuedActions.stream()
      .map(a -> a.getClass().getName())
      .collect(Collectors.joining(", "));
    summary = "Controllers loop (" + summary + ")";
    */
    for (int iter = 0; runAgain || !queuedActions.isEmpty(); iter++) {
      if (iter > 100) {
        throw new RuntimeException("Controllers are stuck in a livelock");
      }
      List<DeferredAction<?>> actions = queuedActions;
      queuedActions = Lists.newArrayList();
      for (DeferredAction<?> action : actions) {
        applyAction(action);
      }
      runningControllers = true;
      try {
        runAgain = assertExists(rootController).invoke();
      } finally {
        runningControllers = false;
      }
    }
    assertExists(frontend).invoke(FrontEnd.Method.updateState, new Object[] { state });
  }

  public void publish(PublishType what, Object data) {
    assertExists(frontend).invoke(what.method, new Object[] { data });
  }

  public Engine getEngine() {
    return engine;
  }

  public State getState() {
    return assertExists(state);
  }

  public <T> void applyAction(DeferredAction<T> action) {
    assertExists(state);
    // We need a special case for when we want to replace the whole tree.
    if (action instanceof SetStateAction) {
      state = ((SetStateAction)action).newState;
      return;
    }
    ((StateAction)action).accept(state);
  }

  public static enum PublishType {
    OverviewData(FrontEnd.Method.publishOverviewData),
    TrackData(FrontEnd.Method.publishTrackData),
    Threads(FrontEnd.Method.publishThreads),
    QueryResult(FrontEnd.Method.publishQueryResult),
    LegacyTrace(FrontEnd.Method.publishLegacyTrace);

    public final FrontEnd.Method method;

    private PublishType(Method method) {
      this.method = method;
    }
  }

  public static class QueryResult {
    public final String id;
    public final QueryResponse data;

    public QueryResult(String id, QueryResponse data) {
      this.id = id;
      this.data = data;
    }
  }

  public static class TrackData<Data> {
    public final String id;
    public final Data data;

    public TrackData(String id, Data data) {
      this.id = id;
      this.data = data;
    }
  }
}
