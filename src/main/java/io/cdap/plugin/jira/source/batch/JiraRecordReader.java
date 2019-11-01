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
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import io.cdap.plugin.jira.source.common.JiraClient;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Reads chunk of issues of capacity maxSplitSize from jira starting at given position.
 */
public class JiraRecordReader extends RecordReader<NullWritable, Issue> {
  private static final Logger LOG = LoggerFactory.getLogger(JiraRecordReader.class);
  private final JiraBatchSourceConfig config;
  private final int startAt;

  private Issue value;
  private JiraClient jiraClient;
  private Iterator<Issue> issueIterator;
  private int issuesCount;
  private int issueIndex = 0;

  public JiraRecordReader(JiraBatchSourceConfig config, int startAt) {
    this.config = config;
    this.startAt = startAt;
  }

  /**
   * Initialize an iterator and config.
   *
   * @param inputSplit specifies batch details
   * @param taskAttemptContext task context
   */
  @Override
  public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext) {
    jiraClient = new JiraClient(config);
    SearchResult searchResult = jiraClient.getSearchResult(startAt);
    issueIterator = searchResult.getIssues().iterator();
    issuesCount = searchResult.getTotal();
  }

  @Override
  public boolean nextKeyValue() {
    if (!issueIterator.hasNext()) {
      return false;
    }

    issueIndex++;
    value = issueIterator.next();
    return true;
  }

  @Override
  public NullWritable getCurrentKey() {
    return null;
  }

  @Override
  public Issue getCurrentValue() {
    return value;
  }

  @Override
  public float getProgress() {
    return issueIndex / (float) issuesCount;
  }

  @Override
  public void close() {
    if (jiraClient != null) {
      jiraClient.close();
    }
  }
}
