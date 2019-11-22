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

package io.cdap.plugin.jira.source.streaming;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.google.common.base.Throwables;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.plugin.jira.source.common.IssueToRecordTransformer;
import io.cdap.plugin.jira.source.common.JiraClient;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.rdd.RDD;
import org.apache.spark.streaming.StreamingContext;
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.dstream.InputDStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.reflect.ClassTag;

import java.time.ZonedDateTime;
import java.util.LinkedList;

/**
 * Iterates over the jira issues and fills Spark RDD with structured records from them.
 *
 * Note: Fields of this class get checkpointed, which means that their value is restored if pipeline is re-run
 * after a stop.
 */
public class JiraInputDStream extends InputDStream<StructuredRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(JiraInputDStream.class);
  private final JiraStreamingSourceConfig config;

  // saves state between pipeline runs, due to checkpointing.
  private ZonedDateTime lastRunLastCreationDate;

  public JiraInputDStream(StreamingContext ssc, ClassTag<StructuredRecord> evidence1,
                          JiraStreamingSourceConfig config) {
    super(ssc, evidence1);
    this.config = config;
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }

  /**
   * Adds records to Spark RDD. This method is run every batchInterval seconds.
   * We need to give out records in portions here (instead of all at once).
   * So that the process gets more parallel.
   */
  @Override
  public Option<RDD<StructuredRecord>> compute(Time time) {
    try {
      return doCompute();
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  private Option<RDD<StructuredRecord>> doCompute() {
    IssueToRecordTransformer transformer = new IssueToRecordTransformer(config.getSchema());
    LinkedList<StructuredRecord> records = new LinkedList<>();
    ZonedDateTime creationDate = lastRunLastCreationDate;
    long batchInterval = getBatchIntervalMilliseconds();
    long startTime = System.currentTimeMillis();

    try (JiraClient jiraClient = new JiraClient(config, lastRunLastCreationDate, config.getTrackUpdates())) {
      for (int i = 0;;) {
        SearchResult searchResult = jiraClient.getSearchResult(i);

        boolean hasAtLeastOneIssue = false;
        for (Issue issue : searchResult.getIssues()) {
          i++;
          hasAtLeastOneIssue = true;
          StructuredRecord record = transformer.transform(issue);
          creationDate = record.getTimestamp((config.getTrackUpdates()) ? "updateDate" : "creationDate");

          // Since Jira only allows filtering by minutes not seconds in JQL we still have to skip
          // the issue which were created in the last minute, but already processed.
          if (lastRunLastCreationDate == null || creationDate.isAfter(lastRunLastCreationDate)) {
            records.add(record);
          }
        }

        if (config.isUnlimitedMaxIssues() || !hasAtLeastOneIssue
          // Computing has been running for too long. Give out the current portion of issues to Spark.
          // And than come back for more.
          || System.currentTimeMillis() - startTime > batchInterval) {
          break;
        }
      }
    }
    lastRunLastCreationDate = creationDate;

    RDD<StructuredRecord> rdds = getJavaSparkContext().parallelize(records).rdd();
    return Option.apply(rdds);
  }

  private JavaSparkContext getJavaSparkContext() {
    return JavaSparkContext.fromSparkContext(ssc().sc());
  }

  private long getBatchIntervalMilliseconds() {
    return ssc().graph().batchDuration().milliseconds();
  }
}
