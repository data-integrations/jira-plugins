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
import com.google.common.base.Preconditions;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.batch.Input;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.dataset.lib.KeyValue;
import io.cdap.cdap.etl.api.Emitter;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.batch.BatchRuntimeContext;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import io.cdap.plugin.common.LineageRecorder;
import io.cdap.plugin.jira.source.common.IssueToRecordTransformer;
import org.apache.hadoop.io.NullWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * Batch Source which reads issues from jira in a parallel fashion using {@link JiraInputFormatProvider}
 * Which are than transformed to {@link StructuredRecord}.
 */
@Plugin(type = BatchSource.PLUGIN_TYPE)
@Name(JiraBatchSource.NAME)
@Description("Reads issues from Jira")
public class JiraBatchSource extends BatchSource<NullWritable, Issue, StructuredRecord> {
  static final String NAME = "Jira";
  private static final Logger LOG = LoggerFactory.getLogger(JiraBatchSource.class);

  private final JiraBatchSourceConfig config;
  private IssueToRecordTransformer transformer;

  public JiraBatchSource(JiraBatchSourceConfig config) {
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    FailureCollector failureCollector = pipelineConfigurer.getStageConfigurer().getFailureCollector();
    config.validate(failureCollector);
    failureCollector.getOrThrowException();

    pipelineConfigurer.getStageConfigurer().setOutputSchema(config.getSchema());
  }

  @Override
  public void prepareRun(BatchSourceContext context) {
    FailureCollector failureCollector = context.getFailureCollector();
    config.validate(failureCollector); // validate when macros are already substituted
    failureCollector.getOrThrowException();

    Schema schema = config.getSchema();

    LineageRecorder lineageRecorder = new LineageRecorder(context, config.referenceName);
    lineageRecorder.createExternalDataset(schema);
    lineageRecorder.recordRead("Read", String.format("Read from Jira '%s'", config.getJiraUrl()),
                               Preconditions.checkNotNull(schema.getFields()).stream()
                                 .map(Schema.Field::getName)
                                 .collect(Collectors.toList()));

    context.setInput(Input.of(config.referenceName, new JiraInputFormatProvider(config)));
  }

  @Override
  public void initialize(BatchRuntimeContext context) throws Exception {
    transformer = new IssueToRecordTransformer(config.getSchema());
    super.initialize(context);
  }

  @Override
  public void transform(KeyValue<NullWritable, Issue> input,
                        Emitter<StructuredRecord> emitter) {
    emitter.emit(transformer.transform(input.getValue()));
  }
}
