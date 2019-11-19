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

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.streaming.StreamingContext;
import io.cdap.cdap.etl.api.streaming.StreamingSource;
import org.apache.spark.streaming.api.java.JavaDStream;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;

/**
 * Reads all issues filtered than only newly created issues.
 */
@Plugin(type = StreamingSource.PLUGIN_TYPE)
@Name(JiraStreamingSource.NAME)
@Description(JiraStreamingSource.DESCRIPTION)
public class JiraStreamingSource  extends StreamingSource<StructuredRecord> {
  static final String NAME = "Jira";
  static final String DESCRIPTION = "Read new issues from Jira periodically";
  private JiraStreamingSourceConfig config;

  public JiraStreamingSource(JiraStreamingSourceConfig config) {
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
  public JavaDStream<StructuredRecord> getStream(StreamingContext context) {
    config.validate(context.getFailureCollector()); // validate when macros are substituted

    ClassTag<StructuredRecord> tag = ClassTag$.MODULE$.apply(StructuredRecord.class);
    JiraInputDStream dstream = new JiraInputDStream(context.getSparkStreamingContext().ssc(), tag, config);
    return JavaDStream.fromDStream(dstream, tag);
  }
}
