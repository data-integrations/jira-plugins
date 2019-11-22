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
package io.cdap.plugin.jira.source.common;

import com.google.common.base.Strings;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Macro;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.plugin.common.IdUtils;
import io.cdap.plugin.common.ReferencePluginConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Provides common configurations required for configuring batch and realtime jira source plugin.
 */
public class JiraSourceConfig extends ReferencePluginConfig {
  public static final String PROPERTY_JIRA_URL = "jiraUrl";
  public static final String PROPERTY_FILTER_MODE = "filterMode";
  public static final String PROPERTY_JIRA_FILTER_ID = "jiraFilterId";
  public static final String PROPERTY_JQL_QUERY = "jqlQuery";
  public static final String PROPERTY_USERNAME = "username";
  public static final String PROPERTY_PASSWORD = "password";
  public static final String PROPERTY_MAX_ISSUES_PER_REQUEST = "maxIssuesPerRequest";
  public static final String PROPERTY_FIX_VERSIONS = "fixVersions";
  public static final String PROPERTY_AFFECTED_VERSIONS = "affectedVersions";
  public static final String PROPERTY_LABELS = "labels";
  public static final String PROPERTY_START_UPDATE_DATE = "lastUpdateStartDate";
  public static final String PROPERTY_END_UPDATE_DATE = "lastUpdateEndDate";

  @Name(PROPERTY_JIRA_URL)
  @Description("URL of JIRA instance.")
  @Macro
  protected String jiraUrl;

  @Name(PROPERTY_FILTER_MODE)
  @Description("Mode which specifies which issues to fetch.\n" +
    "Possible values are:\n" +
    "- Basic\n" +
    "- JQL\n" +
    "- Jira Filter Id")
  @Macro
  private String filterMode;

  @Nullable
  @Description("List of jira projects used for filtering.")
  @Macro
  private String projects;

  @Nullable
  @Description("List of issue types used for filtering. \n" +
    "E.g.: Sub-Task, Bug, Epic, Improvement, New Feature, Story, Task, etc.")
  @Macro
  private String issueTypes;

  @Nullable
  @Description("List of issue statuses used for filtering. \n" +
    "E.g.: Open, In Progress, Reopened, Resolved, Closed, etc.")
  @Macro
  private String statuses;

  @Nullable
  @Description("List of priorities used for filtering.\n" +
    "E.g.: Minor, Major, Critical, Blocker, etc.")
  @Macro
  private String priorities;

  @Nullable
  @Description("List of reporters used for filtering.")
  @Macro
  private String reporters;

  @Nullable
  @Description("List of assignees used for filtering.")
  @Macro
  private String assignees;

  @Name(PROPERTY_FIX_VERSIONS)
  @Nullable
  @Description("List of fix versions used for filtering.")
  @Macro
  private String fixVersions;

  @Name(PROPERTY_AFFECTED_VERSIONS)
  @Nullable
  @Description("List of affected versions used for filtering.")
  @Macro
  private String affectedVersions;

  @Name(PROPERTY_LABELS)
  @Nullable
  @Description("List of issue labels used for filtering.")
  @Macro
  private String labels;

  @Name(PROPERTY_START_UPDATE_DATE)
  @Nullable
  @Description("Minimal update date for the issues fetched.\n" +
    "Valid formats include: 'yyyy/MM/dd HH:mm', 'yyyy-MM-dd HH:mm', 'yyyy/MM/dd', \n" +
    "'yyyy-MM-dd', or a period format e.g. '-5d', '4w 2d'.")
  @Macro
  private String lastUpdateStartDate;

  @Name(PROPERTY_END_UPDATE_DATE)
  @Nullable
  @Description("Maximum update date for the issues fetched.\n" +
    "Valid formats include: 'yyyy/MM/dd HH:mm', 'yyyy-MM-dd HH:mm', 'yyyy/MM/dd', \n" +
    "'yyyy-MM-dd', or a period format e.g. '-5d', '4w 2d'.")
  @Macro
  private String lastUpdateEndDate;

  @Name(PROPERTY_JQL_QUERY)
  @Nullable
  @Description("Query in JQL syntax used to filter the issues.\n" +
    "E.g.: project = NETTY AND priority >= Critical AND fixVersion IN (4.2, 4.3) AND updateDate > -5d")
  @Macro
  private String jqlQuery;

  @Name(PROPERTY_JIRA_FILTER_ID)
  @Nullable
  @Description("Numerical id of an existing JIRA filter.")
  @Macro
  private Integer jiraFilterId;

  @Name(PROPERTY_MAX_ISSUES_PER_REQUEST)
  @Description("Maximum number of issue which are fetched using a single request . Value of zero means unlimited. \n" +
    "Please keep in mind that high values can result in timeouts due to large response sizes.")
  @Macro
  private Integer maxIssuesPerRequest;

  @Name(PROPERTY_USERNAME)
  @Nullable
  @Description("Username used to authenticate to JIRA instance via basic authentication.")
  @Macro
  private String username;

  @Name(PROPERTY_PASSWORD)
  @Nullable
  @Description("Password used to authenticate to JIRA instance via basic authentication.\n" +
    "If both username and password are not set, JIRA will be accessed via anonymous user.")
  @Macro
  private String password;

  @Description("Defines the output schema")
  private String schema;

  public JiraSourceConfig(String referenceName) {
    super(referenceName);
  }

  public void validate(FailureCollector failureCollector) {
    IdUtils.validateReferenceName(referenceName, failureCollector);

    if (!containsMacro(PROPERTY_FILTER_MODE)) {
      try {
        if (getFilterMode() == FilterMode.JIRA_FILTER_ID) {
          validatePropertySet(PROPERTY_JIRA_FILTER_ID, "Jira Filter Id", jiraFilterId,
                              failureCollector);
        }
      } catch (IllegalArgumentException ex) {
        failureCollector.addFailure(ex.getMessage(), null)
          .withConfigProperty(PROPERTY_FILTER_MODE);
      }
    }

    if (!containsMacro(PROPERTY_JIRA_URL)) {
      try {
        new URI(getJiraUrl());
      } catch (URISyntaxException e) {
        failureCollector.addFailure(String.format("Invalid URI '%s'", getJiraUrl()), null)
          .withConfigProperty(PROPERTY_JIRA_URL)
          .withStacktrace(e.getStackTrace());
      }
    }

    if (!containsMacro(PROPERTY_USERNAME) && !containsMacro(PROPERTY_PASSWORD)) {
      if (Strings.isNullOrEmpty(username) != Strings.isNullOrEmpty(password)) {
        failureCollector.addFailure("Either set or unset both login and password", null)
          .withConfigProperty(PROPERTY_USERNAME)
          .withConfigProperty(PROPERTY_PASSWORD);
      }
    }
  }

  public String getJiraUrl() {
    return jiraUrl;
  }

  public String getJqlQuery() {
    return jqlQuery;
  }

  public FilterMode getFilterMode() {
    return FilterMode.fromValue(filterMode);
  }

  @Nullable
  public List<String> getProjects() {
    return getListFromString(projects);
  }

  @Nullable
  public List<String> getIssueTypes() {
    return getListFromString(issueTypes);
  }

  @Nullable
  public List<String> getStatuses() {
    return getListFromString(statuses);
  }

  @Nullable
  public List<String> getPriorities() {
    return getListFromString(priorities);
  }

  @Nullable
  public List<String> getReporters() {
    return getListFromString(reporters);
  }

  @Nullable
  public List<String> getAssignees() {
    return getListFromString(assignees);
  }

  @Nullable
  public List<String> getFixVersions() {
    return getListFromString(fixVersions);
  }

  @Nullable
  public List<String> getAffectedVersions() {
    return getListFromString(affectedVersions);
  }

  @Nullable
  public List<String> getLabels() {
    return getListFromString(labels);
  }

  @Nullable
  public String getLastUpdateStartDate() {
    return lastUpdateStartDate;
  }

  @Nullable
  public String getLastUpdateEndDate() {
    return lastUpdateEndDate;
  }

  @Nullable
  public Integer getJiraFilterId() {
    return jiraFilterId;
  }

  @Nullable
  public Integer getMaxIssuesPerRequest() {
    return isUnlimitedMaxIssues() ? Integer.MAX_VALUE : maxIssuesPerRequest;
  }

  public boolean isUnlimitedMaxIssues() {
    return (maxIssuesPerRequest == 0);
  }

  @Nullable
  public String getUsername() {
    return username;
  }

  @Nullable
  public String getPassword() {
    return password;
  }

  public boolean useBasicAuthentication() {
    return username != null && password != null;
  }

  @Nullable
  public Schema getSchema() {
    try {
      return Strings.isNullOrEmpty(schema) ? null : Schema.parseJson(schema);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to parse output schema: " + schema, e);
    }
  }

  public void validatePropertySet(String propertyName, String propertyDisplayName,
                                         Object propertyValue, FailureCollector failureCollector) {
    if (!containsMacro(propertyName) && (propertyValue == null || propertyValue.toString().isEmpty())) {
      failureCollector.addFailure(String.format("'%s' must be set to a value", propertyDisplayName), null)
        .withConfigProperty(propertyName);
    }
  }

  public static List<String> getListFromString(String value) {
    if (Strings.isNullOrEmpty(value)) {
      return null;
    }
    return Arrays.asList(value.split(","));
  }
}
