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
import static com.google.gapid.perfetto.controller.ControllerGlobals.cGlobals;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.perfetto.common.Actions;
import com.google.gapid.perfetto.common.Engine;
import com.google.gapid.perfetto.common.Protos;
import com.google.gapid.perfetto.common.QueryResponse;
import com.google.gapid.perfetto.common.State.QueryConfig;
import com.google.gapid.util.MoreFutures;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryController extends Controller<QueryController.State> {
  private static final Logger LOG = Logger.getLogger(QueryController.class.getName());

  private final QueryControllerArgs args;

  public QueryController(QueryControllerArgs args) {
    super(State.init);
    this.args = args;
  }

  @Override
  public List<ControllerInitializer<?>> run() {
    switch (getState()) {
      case init:
        QueryConfig config = assertExists(cGlobals().getState().queries.get(args.queryId));
        cGlobals().thenOnUiThread(runQuery(config.query), result -> {
          LOG.log(Level.INFO, "Query {} took {} ms", new Object[] { config.query, result.durationMs });
          cGlobals().publish(
              ControllerGlobals.PublishType.QueryResult, new ControllerGlobals.QueryResult(args.queryId, result));
          cGlobals().dispatch(Actions.deleteQuery(args.queryId));
        });
        this.setState(State.querying);
        break;

      case querying:
        // Nothing to do here, as soon as the deleteQuery is dispatched this
        // controller will be destroyed (by the TraceController).
        break;

      default:
        throw new RuntimeException("Unexpected state: " + getState());
    }
    return null;
  }

  private ListenableFuture<QueryResponse> runQuery(String sqlQuery) {
    long startMs = System.currentTimeMillis();
    return MoreFutures.transform(args.engine.query(sqlQuery), rawResult -> {
      long durationMs = System.currentTimeMillis() - startMs;
      String[] columns = Protos.rawQueryResultColumns(rawResult);
      Protos.Row[] rows = firstN(1000, Protos.rawQueryResultIter(rawResult));
      return new QueryResponse(args.queryId, sqlQuery, rawResult.getError(),
          rawResult.getNumRecords(), durationMs, columns, rows);
    });
  }

  private static Protos.Row[] firstN(int n, Iterator<Supplier<Protos.Row>> iter) {
    List<Protos.Row> list = Lists.newArrayList();
    for (int i = 0; i < n && iter.hasNext(); i++) {
      list.add(iter.next().get());
    }
    return list.toArray(new Protos.Row[list.size()]);
  }

  public static enum State {
    init, querying;
  }

  public static class QueryControllerArgs implements ControllerFactory<QueryController> {
    public final String queryId;
    public final Engine engine;

    public QueryControllerArgs(String queryId, Engine engine) {
      this.queryId = queryId;
      this.engine = engine;
    }

    @Override
    public QueryController build() {
      return new QueryController(this);
    }
  }
}
