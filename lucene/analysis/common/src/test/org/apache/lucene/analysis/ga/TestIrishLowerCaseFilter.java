package org.apache.lucene.analysis.ga;

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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;

/**
 * Test the Irish lowercase filter.
 */
public class TestIrishLowerCaseFilter extends BaseTokenStreamTestCase {
  
  /**
   * Test lowercase
   */
  public void testIrishLowerCaseFilter() throws Exception {
    TokenStream stream = new MockTokenizer(new StringReader(
        "nAthair tUISCE hARD"), MockTokenizer.WHITESPACE, false);
    IrishLowerCaseFilter filter = new IrishLowerCaseFilter(stream);
    assertTokenStreamContents(filter, new String[] {"n-athair", "t-uisce",
        "hard",});
  }
  
  public void testEmptyTerm() throws IOException {
    Analyzer a = new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new KeywordTokenizer(reader);
        return new TokenStreamComponents(tokenizer, new IrishLowerCaseFilter(tokenizer));
      }
    };
    checkOneTermReuse(a, "", "");
  }
}
