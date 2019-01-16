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

import com.google.gapid.perfetto.common.Protos.Row;

public class QueryResponse {
  public String id;
  public String query;
  public String error;
  public long totalRowCount;
  public long durationMs;
  public String[] columns;
  public Protos.Row[] rows;

  public QueryResponse(String id, String query, String error, long totalRowCount, long durationMs,
      String[] columns, Row[] rows) {
    this.id = id;
    this.query = query;
    this.error = error;
    this.totalRowCount = totalRowCount;
    this.durationMs = durationMs;
    this.columns = columns;
    this.rows = rows;
  }
}
