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

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.ImmutableSet;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.StringJoiner;

/**
 * A class which is used to communicate to Jira API based on the plugin configurations.
 */
public class JiraClient implements Closeable {
  /**
   * You have to always query these fields or else the searchJql method will fail.
   */
  private static final ImmutableSet<String> MINIMAL_FIELDS_SET = ImmutableSet.of("project", "summary", "issuetype",
                                                                                 "created", "updated", "status");

  private final JiraRestClient restClient;
  private final BaseJiraSourceConfig config;

  public JiraClient(BaseJiraSourceConfig config) {
    this.config = config;

    AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
    try {
      URI jiraUrl = new URI(config.getJiraUrl());

      if (config.useBasicAuthentication()) {
        restClient = factory.createWithBasicHttpAuthentication(jiraUrl, config.getUsername(), config.getPassword());
      } else {
        restClient = factory.create(jiraUrl, new AnonymousAuthenticationHandler());
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(String.format("Invalid URI '%s'", config.getJiraUrl()), e);
    }
  }

  public SearchResult getSearchResult(int startAt) {
    return restClient.getSearchClient().searchJql(getJQLQuery(), config.getMaxIssuesPerRequest(),
                                                startAt, null).claim();
  }

  public int getResultsCount() {
    return restClient.getSearchClient().searchJql(getJQLQuery(), null, null,
                                                  MINIMAL_FIELDS_SET).claim().getTotal();
  }

  public String getJQLQuery() {
    switch (config.getFilterMode()) {
      case JQL:
        return config.getJqlQuery();
      case JIRA_FILTER_ID:
        return restClient.getSearchClient().getFilter(config.getJiraFilterId()).claim().getJql();
      case BASIC:
        return getBasicQuery();
      default:
        throw new IllegalArgumentException(String.format("Unsupported filter mode: '%s'", config.getFilterMode()));
    }
  }

  private String getBasicQuery() {
    StringJoiner joiner = new StringJoiner(" AND ");

    List<String> projects = config.getProjects();
    if (projects != null) {
      joiner.add("project IN " + listToString(projects));
    }

    List<String> issueTypes = config.getIssueTypes();
    if (issueTypes != null) {
      joiner.add("issuetype IN " + listToString(issueTypes));
    }

    List<String> statuses = config.getStatuses();
    if (statuses != null) {
      joiner.add("status IN " + listToString(statuses));
    }

    List<String> priorities = config.getPriorities();
    if (priorities != null) {
      joiner.add("priority IN " + listToString(priorities));
    }

    List<String> reporters = config.getReporters();
    if (reporters != null) {
      joiner.add("reporter IN " + listToString(reporters));
    }

    List<String> assignees = config.getAssignees();
    if (assignees != null) {
      joiner.add("assignee IN " + listToString(assignees));
    }

    List<String> fixVersions = config.getFixVersions();
    if (fixVersions != null) {
      joiner.add("fixVersion IN " + listToString(fixVersions));
    }

    List<String> affectedVersions = config.getAffectedVersions();
    if (affectedVersions != null) {
      joiner.add("affectedVersion IN " + listToString(affectedVersions));
    }

    List<String> labels = config.getLabels();
    if (labels != null) {
      joiner.add("labels IN " + listToString(labels));
    }

    String lastUpdateStartDate = config.getLastUpdateStartDate();
    if (lastUpdateStartDate != null) {
      joiner.add("updated >= '" + lastUpdateStartDate + "'");
    }

    String lastUpdateEndDate = config.getLastUpdateEndDate();
    if (lastUpdateEndDate != null) {
      joiner.add("updated <= '" + lastUpdateEndDate + "'");
    }

    return joiner.toString();
  }

  private String listToString(List<String> list) {
    StringJoiner joiner = new StringJoiner(",", "(", ")");

    for (String item : list) {
      joiner.add("'" + item + "'");
    }

    return joiner.toString();
  }

  @Override
  public void close() {
    try {
      restClient.close();
    } catch (IOException e) {
      throw new RuntimeException("Cannot close jira client", e);
    }
  }
}
