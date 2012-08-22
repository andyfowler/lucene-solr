package org.apache.solr.search;

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

import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

public class TestNewSpatialSort extends SolrTestCaseJ4
{

  private String fieldName;

  public TestNewSpatialSort(String fieldName) {
    this.fieldName = fieldName;
  }

  @ParametersFactory
  public static Iterable<Object[]> parameters() {
    return Arrays.asList(new Object[][]{
        {"srpt_geohash"}, {"srpt_quad"}, {"stqpt_geohash"}
    });
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-basic.xml", "schema-spatial.xml");
  }

  /** Test that queries against a spatial field return the distance as the score. */
  @Test
  public void directQuery() throws Exception {
    assertU(adoc("id", "100", fieldName, "1,2"));
    assertU(adoc("id", "101", fieldName, "4,-1"));
    assertU(commit());

    assertJQ(req(
          "q", fieldName +":\"Intersects(Circle(3,4 d=1000))\"",
          "fl","id,score",
          "sort","score asc")//want ascending due to increasing distance
        , 1e-3
        , "/response/docs/[0]/id=='100'"
        , "/response/docs/[0]/score==314.4033"
        , "/response/docs/[1]/id=='101'"
        , "/response/docs/[1]/score==565.9615"
    );
    //query again with the query point closer to #101, and check the new ordering
    assertJQ(req(
          "q", fieldName +":\"Intersects(Circle(4,0 d=1000))\"",
          "fl","id,score",
          "sort","score asc")//want ascending due to increasing distance
        , 1e-4
        , "/response/docs/[0]/id=='101'"
        , "/response/docs/[1]/id=='100'"
    );

    //use sort=query(...)
    assertJQ(req(
          "q","*:*",
          "fl","id,score",
          "sort","query($sortQuery) asc", //want ascending due to increasing distance
          "sortQuery", fieldName +":\"Intersects(Circle(3,4 d=1000))\"" )
        , 1e-4
        , "/response/docs/[0]/id=='100'"
        , "/response/docs/[1]/id=='101'"  );

    //check reversed direction with query point closer to #101
    assertJQ(req(
          "q","*:*",
          "fl","id,score",
          "sort","query($sortQuery) asc", //want ascending due to increasing distance
          "sortQuery", fieldName +":\"Intersects(Circle(4,0 d=1000))\"" )
        , 1e-4
        , "/response/docs/[0]/id=='101'"
        , "/response/docs/[1]/id=='100'"  );
  }

  @Test
  public void multiVal() throws Exception {
    //RandomizedTest.assumeFalse("Multivalue not supported for this field",fieldName.startsWith("stqpt"));

    assertU(adoc("id", "100", fieldName, "1,2"));//1 point
    assertU(adoc("id", "101", fieldName, "4,-1", fieldName, "3,5"));//2 points, 2nd is pretty close to query point
    assertU(commit());

    assertJQ(req(
          "q", fieldName +":\"Intersects(Circle(3,4 d=1000))\"",
          "fl","id,score",
          "sort","score asc")//want ascending due to increasing distance
        , 1e-4
        , "/response/docs/[0]/id=='101'"
        , "/response/docs/[0]/score==111.042725"//dist to 3,5
    );
  }

}
