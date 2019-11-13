/*
 * Copyright © 2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.plugin.jira.etl;

import com.google.common.collect.ImmutableMap;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.plugin.jira.source.common.FilterMode;
import io.cdap.plugin.jira.source.common.JiraSourceConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class JiraSourceETLTest extends BaseJiraSourceETLTest {
  @Test
  public void testSearchByJQL() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.JQL.getValue())
      .put(JiraSourceConfig.PROPERTY_JQL_QUERY,
           "fixVersion = 60.0.0 OR labels = secondIssue OR affectedVersion = 60.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties);
    Assert.assertEquals(3, records.size());
  }

  @Test
  public void testSearchByLabels() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_LABELS, "thirdIssue,someOtherLabel")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties);
    Assert.assertEquals(1, records.size());
  }

  @Test
  public void testSearchByFixVersions() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_FIX_VERSIONS, "50.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties);
    Assert.assertEquals(2, records.size());
  }

  @Test
  public void testSearchByAffectedVersions() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_AFFECTED_VERSIONS, "40.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties);
    Assert.assertEquals(2, records.size());
  }

  @Test
  public void testSearchByUpdateDate() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_START_UPDATE_DATE, createDate)
      .build();

    List<StructuredRecord> records = getPipelineResults(properties);
    Assert.assertEquals(3, records.size());
  }
}
