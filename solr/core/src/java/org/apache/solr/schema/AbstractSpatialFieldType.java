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

import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.context.SpatialContextFactory;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.Shape;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialArgsParser;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.search.QParser;
import org.apache.solr.util.MapListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSpatialFieldType<T extends SpatialStrategy> extends FieldType
{
  protected final Logger log = LoggerFactory.getLogger( getClass() );

  protected SpatialContext ctx;
  protected SpatialArgsParser argsParser;

  protected boolean ignoreIncompatibleGeometry = false;

  private final ConcurrentHashMap<String, T> fieldStrategyMap = new ConcurrentHashMap<String,T>();

  @Override
  protected void init(IndexSchema schema, Map<String, String> args) {
    super.init(schema, args);
    String v = args.remove( "ignoreIncompatibleGeometry" );
    if( v != null ) {
      ignoreIncompatibleGeometry = Boolean.valueOf( v );
    }

    //Solr expects us to remove the parameters we've used.
    MapListener<String, String> argsWrap = new MapListener<String, String>(args);
    ctx = SpatialContextFactory.makeSpatialContext(argsWrap, schema.getResourceLoader().getClassLoader());
    args.keySet().removeAll(argsWrap.getSeenKeys());

    argsParser = new SpatialArgsParser();//might make pluggable some day?
  }

  //--------------------------------------------------------------
  // Indexing
  //--------------------------------------------------------------

  @Override
  public final IndexableField createField(SchemaField field, Object val, float boost) {
    throw new IllegalStateException("should be calling createFields because isPolyField() is true");
  }

  @Override
  public final IndexableField[] createFields(SchemaField field, Object val, float boost) {
    Shape shape = (val instanceof Shape)?((Shape)val): ctx.readShape( val.toString() );
    if( shape == null ) {
      log.warn( "Field {}: null shape for input: {}", field, val );
      return null;
    }

    IndexableField[] indexableFields = null;
    if (field.indexed()) {
      T strategy = getStrategy(field.getName(), fieldStrategyMap);

      indexableFields = strategy.createIndexableFields(shape);
    }

    StoredField storedField = null;
    if (field.stored()) {
      storedField = new StoredField(field.getName(),ctx.toString(shape));//normalizes the shape
    }

    if (indexableFields == null) {
      if (storedField == null)
        return null;
      return new IndexableField[]{storedField};
    } else {
      if (storedField == null)
        return indexableFields;
      IndexableField[] result = new IndexableField[indexableFields.length+1];
      System.arraycopy(indexableFields,0,result,0,indexableFields.length);
      result[result.length-1] = storedField;
      return result;
    }
  }

  /** Called from {@link #createFields(SchemaField, Object, float)} upon first use by fieldName. } */
  protected abstract T newSpatialStrategy(String fieldName);

  @Override
  public final boolean isPolyField() {
    return true;
  }

  //--------------------------------------------------------------
  // Query Support
  //--------------------------------------------------------------

  @Override
  public Query getRangeQuery(QParser parser, SchemaField field, String part1, String part2, boolean minInclusive, boolean maxInclusive) {
    if (!minInclusive || !maxInclusive)
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Both sides of range query must be inclusive: " + field.getName());
    Shape shape1 = ctx.readShape(part1);
    Shape shape2 = ctx.readShape(part2);
    if (!(shape1 instanceof Point) || !(shape2 instanceof Point))
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Both sides of range query must be points: " + field.getName());
    Point p1 = (Point) shape1;
    Point p2 = (Point) shape2;
    Rectangle bbox = ctx.makeRect(p1.getX(),p2.getX(),p1.getY(),p2.getY());
    SpatialArgs spatialArgs = new SpatialArgs(SpatialOperation.Intersects,bbox);
    return getQueryFromSpatialArgs(parser, field, spatialArgs);
  }

  @Override
  public ValueSource getValueSource(SchemaField field, QParser parser) {
    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "ValueSource not supported on SpatialField: " + field.getName());
  }

  @Override
  public Query getFieldQuery(QParser parser, SchemaField field, String externalVal) {
    return getQueryFromSpatialArgs(parser, field, argsParser.parse(externalVal, ctx));
  }

  private Query getQueryFromSpatialArgs(QParser parser, SchemaField field, SpatialArgs spatialArgs) {
    T spatialStrategy = getStrategy(field.getName(), fieldStrategyMap);

    //see SOLR-2883 needScore
    SolrParams localParams = parser.getLocalParams();
    if (localParams == null || localParams.getBool("needScore", true)) {
      return spatialStrategy.makeQuery(spatialArgs);
    } else {
      Filter filter = spatialStrategy.makeFilter(spatialArgs);
      if (filter instanceof QueryWrapperFilter) {
        QueryWrapperFilter queryWrapperFilter = (QueryWrapperFilter) filter;
        return queryWrapperFilter.getQuery();
      }
      return new ConstantScoreQuery(filter);
    }
  }

  private final T getStrategy(final String name, final ConcurrentHashMap<String, T> strategyMap) {
    T strategy = strategyMap.get(name);
    //double-checked locking idiom
    if (strategy == null) {
      synchronized (strategyMap) {
        strategy = strategyMap.get(name);
        if (strategy == null) {
          strategy = newSpatialStrategy(name);
          strategyMap.put(name,strategy);
        }
      }
    }
    return strategy;
  }

  @Override
  public void write(TextResponseWriter writer, String name, IndexableField f) throws IOException {
    writer.writeStr(name, f.stringValue(), true);
  }

  @Override
  public SortField getSortField(SchemaField field, boolean top) {
    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Sorting not supported on SpatialField: " + field.getName());
  }
}


