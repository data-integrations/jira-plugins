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
import io.cdap.cdap.api.artifact.ArtifactSummary;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.dataset.table.Table;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.datastreams.DataStreamsApp;
import io.cdap.cdap.datastreams.DataStreamsSparkLauncher;
import io.cdap.cdap.etl.api.streaming.StreamingSource;
import io.cdap.cdap.etl.mock.batch.MockSink;
import io.cdap.cdap.etl.proto.v2.DataStreamsConfig;
import io.cdap.cdap.etl.proto.v2.ETLPlugin;
import io.cdap.cdap.etl.proto.v2.ETLStage;
import io.cdap.cdap.etl.spark.Compat;
import io.cdap.cdap.proto.ProgramRunStatus;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.ArtifactId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.test.ApplicationManager;
import io.cdap.cdap.test.DataSetManager;
import io.cdap.cdap.test.ProgramManager;
import io.cdap.cdap.test.SparkManager;
import io.cdap.cdap.test.TestConfiguration;
import io.cdap.plugin.jira.source.streaming.JiraStreamingSource;
import io.cdap.plugin.jira.source.streaming.JiraStreamingSourceConfig;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JiraStreamingSourceETLTest extends BaseJiraSourceETLTest {
  private static final ArtifactId APP_ARTIFACT_ID = NamespaceId.DEFAULT.artifact("data-streams", "1.0.0");
  private static final ArtifactSummary APP_ARTIFACT = new ArtifactSummary("data-streams", "1.0.0");
  private static final int WAIT_FOR_RECORDS_TIMEOUT_SECONDS = 60;
  private static final long WAIT_FOR_RECORDS_POLLING_INTERVAL_MS = 100;

  @ClassRule
  public static final TestConfiguration CONFIG =
    new TestConfiguration(Constants.Explore.EXPLORE_ENABLED, false,
                          Constants.AppFabric.SPARK_COMPAT, Compat.SPARK_COMPAT);

  @BeforeClass
  public static void setupTest() throws Exception {
    setupStreamingArtifacts(APP_ARTIFACT_ID, DataStreamsApp.class);

    addPluginArtifact(NamespaceId.DEFAULT.artifact("jira-plugins", "1.0.0"),
                      APP_ARTIFACT_ID,
                      JiraStreamingSource.class
    );
  }

  @Override
  protected List<StructuredRecord> getPipelineResults(Map<String, String> sourceProperties, int expectedRecordsCount)
    throws Exception {

    ProgramManager programManager = startPipeline(sourceProperties);
    return waitForRecords(programManager, expectedRecordsCount);
  }

  private SparkManager deployETL(ETLPlugin sourcePlugin, ETLPlugin sinkPlugin, String appName) throws Exception {
    ETLStage source = new ETLStage("source", sourcePlugin);
    ETLStage sink = new ETLStage("sink", sinkPlugin);
    DataStreamsConfig etlConfig = DataStreamsConfig.builder()
      .addStage(source)
      .addStage(sink)
      .addConnection(source.getName(), sink.getName())
      .setBatchInterval("1s")
      .build();

    AppRequest<DataStreamsConfig> appRequest = new AppRequest<>(APP_ARTIFACT, etlConfig);
    ApplicationId appId = NamespaceId.DEFAULT.app(appName);
    ApplicationManager applicationManager = deployApplication(appId, appRequest);

    return applicationManager.getSparkManager(DataStreamsSparkLauncher.NAME);
  }

  private ProgramManager startPipeline(Map<String, String> properties) throws Exception {
    Map<String, String> sourceProps = new ImmutableMap.Builder<String, String>()
      .putAll(getBaseProperties())
      .put(JiraStreamingSourceConfig.PROPERTY_TRACK_UPDATES, "false")
      .putAll(properties)
      .build();

    ETLPlugin sourceConfig = new ETLPlugin("Jira", StreamingSource.PLUGIN_TYPE, sourceProps);
    ETLPlugin sinkConfig = MockSink.getPlugin(getOutputDatasetName());

    ProgramManager programManager =
      deployETL(sourceConfig, sinkConfig, "JiraStreaming_" + name.getMethodName());
    programManager.startAndWaitForRun(ProgramRunStatus.RUNNING, 30, TimeUnit.SECONDS);

    return programManager;
  }

  private List<StructuredRecord> waitForRecords(ProgramManager programManager,
                                                int exceptedNumberOfRecords) throws Exception {
    DataSetManager<Table> outputManager = getDataset(getOutputDatasetName());

    final List<StructuredRecord> result = new ArrayList<>();
    Awaitility.await()
      .atMost(WAIT_FOR_RECORDS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .pollInterval(WAIT_FOR_RECORDS_POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS)
      .untilAsserted((() -> {
        /*
        In tests checkpointing does not work. Hence we will get multiple same portions of results during every
        InputDStream run. We need to save only first portion returned by first InputDStream run.
         */
        result.clear();
        result.addAll(MockSink.readOutput(outputManager));
        int recordsCount = result.size();
        Assert.assertTrue(
          String.format("At least %d records expected, but %d found", exceptedNumberOfRecords, recordsCount),
          recordsCount >= exceptedNumberOfRecords);
      }));

    programManager.stop();
    programManager.waitForStopped(10, TimeUnit.SECONDS);
    programManager.waitForRun(ProgramRunStatus.KILLED, 10, TimeUnit.SECONDS);

    return result;
  }

  private String getOutputDatasetName() {
    return "output-realtimesourcetest_" + name.getMethodName();
  }
}
