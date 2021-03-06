package org.apache.solr.schema;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.spatial.vector.TwoDoublesStrategy;

import java.util.Map;


public class SpatialTwoDoublesFieldType extends AbstractSpatialFieldType<TwoDoublesStrategy> {

  private Integer precisionStep;

  @Override
  protected void init(IndexSchema schema, Map<String, String> args) {
    super.init(schema, args);

    String v = args.remove("precisionStep");
    if (v != null) {
      precisionStep = Integer.valueOf(v);
    }
  }

  @Override
  protected TwoDoublesStrategy newSpatialStrategy(String fieldName) {
    TwoDoublesStrategy strat = new TwoDoublesStrategy(ctx, fieldName);
    if (precisionStep != null)
      strat.setPrecisionStep(precisionStep);
    return strat;
  }

}

