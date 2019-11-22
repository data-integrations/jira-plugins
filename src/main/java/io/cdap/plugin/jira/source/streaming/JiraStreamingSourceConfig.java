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
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.plugin.jira.source.common.JiraSourceConfig;

/**
 * Configurations for {@link JiraStreamingSource}
 */
public class JiraStreamingSourceConfig extends JiraSourceConfig {
  public static final String PROPERTY_TRACK_UPDATES = "trackUpdates";

  @Name(PROPERTY_TRACK_UPDATES)
  @Description("If enabled, source will track updates of issues, not only their creations.")
  @Macro
  private Boolean trackUpdates;

  public JiraStreamingSourceConfig(String referenceName) {
    super(referenceName);
  }

  public Boolean getTrackUpdates() {
    return trackUpdates;
  }
}
