/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.opensearch.Version;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.indices.analysis.PreBuiltAnalyzers;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchSingleNodeTestCase;
import org.opensearch.test.InternalSettingsPlugin;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

import static org.opensearch.test.VersionUtils.randomVersion;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class PreBuiltAnalyzerTests extends OpenSearchSingleNodeTestCase {

    @Override
    protected boolean forbidPrivateIndexSettings() {
        return false;
    }

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return pluginList(InternalSettingsPlugin.class);
    }

    public void testThatDefaultAndStandardAnalyzerAreTheSameInstance() {
        Analyzer currentStandardAnalyzer = PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.CURRENT);
        Analyzer currentDefaultAnalyzer = PreBuiltAnalyzers.DEFAULT.getAnalyzer(Version.CURRENT);

        // special case, these two are the same instance
        assertThat(currentDefaultAnalyzer, is(currentStandardAnalyzer));
    }

    public void testThatInstancesAreTheSameAlwaysForKeywordAnalyzer() {
        assertThat(PreBuiltAnalyzers.KEYWORD.getAnalyzer(Version.CURRENT),
                is(PreBuiltAnalyzers.KEYWORD.getAnalyzer(Version.V_6_0_0)));
    }

    public void testThatInstancesAreCachedAndReused() {
        assertSame(PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.CURRENT),
                PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.CURRENT));
        // same es version should be cached
        assertSame(PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.V_6_2_1),
                PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.V_6_2_1));
        assertNotSame(PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.V_6_0_0),
                PreBuiltAnalyzers.STANDARD.getAnalyzer(Version.V_6_0_1));

        // Same Lucene version should be cached:
        assertSame(PreBuiltAnalyzers.STOP.getAnalyzer(Version.V_6_2_1),
            PreBuiltAnalyzers.STOP.getAnalyzer(Version.V_6_2_2));
    }

    public void testThatAnalyzersAreUsedInMapping() throws IOException {
        int randomInt = randomInt(PreBuiltAnalyzers.values().length-1);
        PreBuiltAnalyzers randomPreBuiltAnalyzer = PreBuiltAnalyzers.values()[randomInt];
        String analyzerName = randomPreBuiltAnalyzer.name().toLowerCase(Locale.ROOT);

        Version randomVersion = randomVersion(random());
        Settings indexSettings = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, randomVersion).build();

        NamedAnalyzer namedAnalyzer = new PreBuiltAnalyzerProvider(analyzerName, AnalyzerScope.INDEX,
            randomPreBuiltAnalyzer.getAnalyzer(randomVersion)).get();

        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "text")
                .field("analyzer", analyzerName).endObject().endObject().endObject().endObject();
        MapperService mapperService = createIndex("test", indexSettings, "type", mapping).mapperService();

        MappedFieldType fieldType = mapperService.fieldType("field");
        assertThat(fieldType.getTextSearchInfo().getSearchAnalyzer(), instanceOf(NamedAnalyzer.class));
        NamedAnalyzer fieldMapperNamedAnalyzer = fieldType.getTextSearchInfo().getSearchAnalyzer();

        assertThat(fieldMapperNamedAnalyzer.analyzer(), is(namedAnalyzer.analyzer()));
    }
}
