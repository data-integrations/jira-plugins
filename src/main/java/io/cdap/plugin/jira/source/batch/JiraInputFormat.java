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
package io.cdap.plugin.jira.source.batch;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cdap.plugin.jira.source.common.JiraClient;
import io.cdap.plugin.jira.source.common.JiraSourceConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * InputFormat for mapreduce job, which provides a single split of data. Since in general pagination cannot
 * parallelized.
 */
public class JiraInputFormat extends InputFormat {
  private static final Gson GSON = new GsonBuilder().create();

  private static final Logger LOG = LoggerFactory.getLogger(JiraInputFormat.class);

  @Override
  public List<InputSplit> getSplits(JobContext jobContext) {
    Configuration configuration = jobContext.getConfiguration();
    String configJson = configuration.get(JiraInputFormatProvider.PROPERTY_CONFIG_JSON);
    JiraSourceConfig config = GSON.fromJson(configJson, JiraSourceConfig.class);

    Integer maxSplitSize = config.getMaxIssuesPerRequest();
    if (config.isUnlimitedMaxIssues()) {
      return Collections.singletonList(new JiraSplit(0));
    }

    int issuesCount;
    try (JiraClient jiraClient = new JiraClient(config)) {
      issuesCount = jiraClient.getResultsCount();
    }

    int splitsCount = issuesCount / maxSplitSize;
    if (issuesCount % maxSplitSize != 0) {
      splitsCount++;
    }

    List<InputSplit> splits = new ArrayList<>(splitsCount);
    for (int i = 0; i < splitsCount; i++) {
      splits.add(new JiraSplit(i * maxSplitSize));
    }
    return splits;
  }

  @Override
  public RecordReader<NullWritable, Issue> createRecordReader(
    InputSplit inputSplit, TaskAttemptContext taskAttemptContext) {

    Configuration configuration = taskAttemptContext.getConfiguration();
    String configJson = configuration.get(JiraInputFormatProvider.PROPERTY_CONFIG_JSON);
    JiraSourceConfig config = GSON.fromJson(configJson, JiraSourceConfig.class);

    return new JiraRecordReader(config, ((JiraSplit) inputSplit).getStartAt());
  }
}
