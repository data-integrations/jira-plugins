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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.VersionRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.VersionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.ImmutableMap;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.mock.test.HydratorTestBase;
import io.cdap.plugin.jira.source.common.FilterMode;
import io.cdap.plugin.jira.source.common.JiraSourceConfig;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// TODO: add this to README
/**
 * Methods to run ETL with Jira plugin as source, and a mock plugin as a sink.
 *
 * By default all tests will be skipped, since jira instance running is needed.
 *
 * Instructions to enable the tests:
 * 1. Download and install a trial version of Jira to run the tests.
 * 2. Follow the steps UI takes you to, which involves initializing jira instance, creating account and project.
 * 3. Run the tests using the command below:
 *
 * mvn clean test
 * -Djira.test.url= -Djira.test.username= -Djira.test.password= -Djira.test.project=
 */
public abstract class BaseJiraSourceETLTest extends HydratorTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(BaseJiraSourceETLTest.class);

  private static final Long ISSUE_TYPE_BUG_ID = 10004L;

  private static final String JIRA_URL = System.getProperty("jira.test.url", "http://localhost:8080/");
  private static final String USERNAME = System.getProperty("jira.test.username");
  private static final String PASSWORD = System.getProperty("jira.test.password");
  private static final String PROJECT = System.getProperty("jira.test.project");

  private static IssueRestClient issueClient;
  private static VersionRestClient versionRestClient;
  private static Version version1;
  private static Version version2;
  private static Version version3;
  protected static String createDate;

  private static List<BasicIssue> createdIssues = new ArrayList<>();

  @Rule
  public TestName name = new TestName();

  @BeforeClass
  public static void initializeTests() throws Exception {
    if (JIRA_URL == null || USERNAME == null || PASSWORD == null || PROJECT == null) {
      throw new IllegalArgumentException("ETL tests are skipped. Please find the instructions on enabling it at" +
                                           "BaseJiraSourceETLTest javadoc");
    }

    AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
    URI jiraUrl = new URI(JIRA_URL);

    JiraRestClient jiraRestClient = factory.createWithBasicHttpAuthentication(jiraUrl, USERNAME, PASSWORD);
    issueClient = jiraRestClient.getIssueClient();
    versionRestClient = jiraRestClient.getVersionRestClient();

    version1 = createVersion("40.0.0");
    version2 = createVersion("50.0.0");
    version3 = createVersion("60.0.0");

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    createDate = simpleDateFormat.format(new Date());

    createIssue(Arrays.asList(version3), Arrays.asList(version2, version3),
                Arrays.asList("firstIssue", BaseJiraSourceETLTest.class.getSimpleName()));
    createIssue(Arrays.asList(version1, version2), Arrays.asList(version1, version2),
                Arrays.asList("secondIssue", BaseJiraSourceETLTest.class.getSimpleName()));
    createIssue(Arrays.asList(version1), Arrays.asList(version3),
                Arrays.asList("thirdIssue", BaseJiraSourceETLTest.class.getSimpleName()));
  }

  protected Map<String, String> getBaseProperties() {
    Schema schema =
      Schema.recordOf("etlSchemaBody",
                      Schema.Field.of("key", Schema.of(Schema.Type.STRING)),
                      Schema.Field.of("fixVersions", Schema.arrayOf(Schema.of(Schema.Type.STRING))),
                      Schema.Field.of("affectedVersions", Schema.arrayOf(Schema.of(Schema.Type.STRING))),
                      Schema.Field.of("labels", Schema.arrayOf(Schema.of(Schema.Type.STRING)))
    );

    return new ImmutableMap.Builder<String, String>()
      .put("referenceName", name.getMethodName())
      .put(JiraSourceConfig.PROPERTY_JIRA_URL, JIRA_URL)
      .put(JiraSourceConfig.PROPERTY_USERNAME, USERNAME)
      .put(JiraSourceConfig.PROPERTY_PASSWORD, PASSWORD)
      .put(JiraSourceConfig.PROPERTY_MAX_ISSUES_PER_REQUEST, "50")
      .put("schema", schema.toString())
      .build();
  }

  protected abstract List<StructuredRecord> getPipelineResults(Map<String, String> properties,
                                                               int exceptedNumberOfRecords) throws Exception;

  @AfterClass
  public static void cleanup() {
    removeVersion(version1);
    removeVersion(version2);
    removeVersion(version3);

    for (BasicIssue issue : createdIssues) {
      Awaitility.await()
        .pollDelay(0L, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> issueClient.deleteIssue(issue.getKey(), true).claim());
    }
  }

  protected static void removeVersion(final Version version) {
    Awaitility.await()
      .pollDelay(0L, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> versionRestClient.removeVersion(version.getSelf(), null, null)
        .claim());
  }

  protected static Version createVersion(String name) {
    VersionInput versionInput = new VersionInput(PROJECT, name, null, null,
                                                 false, false);
    return versionRestClient.createVersion(versionInput).claim();
  }

  protected static BasicIssue createIssue(List<Version> affectedVersions, List<Version> fixVersions,
                                          List<String> labels) {
    IssueInputBuilder issueBuilder = new IssueInputBuilder(PROJECT, ISSUE_TYPE_BUG_ID)
      .setSummary(String.format("Issue created from %s", BaseJiraSourceETLTest.class.getSimpleName()))
      .setAffectedVersions(affectedVersions)
      .setFixVersions(fixVersions)
      .setFieldValue("labels", labels);

    BasicIssue issue = issueClient.createIssue(issueBuilder.build()).claim();
    createdIssues.add(issue);

    return issue;
  }

  @Test
  public void testSearchByJQL() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.JQL.getValue())
      .put(JiraSourceConfig.PROPERTY_JQL_QUERY,
           "fixVersion = 60.0.0 OR labels = secondIssue OR affectedVersion = 60.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties, 3);
    Assert.assertEquals(3, records.size());

    for (StructuredRecord record : records) {
      List<String> labels = record.get("labels");
      List<String> fixVersions = record.get("fixVersions");
      List<String> affectedVersions = record.get("affectedVersions");

      Assert.assertTrue(labels.contains("secondIssue") || fixVersions.contains("60.0.0")
      || affectedVersions.contains("60.0.0"));
    }
  }

  @Test
  public void testSearchByLabels() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_LABELS, "thirdIssue,someOtherLabel")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties, 1);
    Assert.assertEquals(1, records.size());

    StructuredRecord record = records.get(0);
    List<String> labels = record.get("labels");
    Assert.assertTrue(labels.contains("thirdIssue"));

  }

  @Test
  public void testSearchByFixVersions() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_FIX_VERSIONS, "50.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties, 2);
    Assert.assertEquals(2, records.size());

    for (StructuredRecord record : records) {
      List<String> fixVersions = record.get("fixVersions");
      Assert.assertTrue(fixVersions.contains("50.0.0"));
    }
  }

  @Test
  public void testSearchByAffectedVersions() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_AFFECTED_VERSIONS, "40.0.0")
      .build();

    List<StructuredRecord> records = getPipelineResults(properties, 2);
    Assert.assertEquals(2, records.size());

    for (StructuredRecord record : records) {
      List<String> affectedVersions = record.get("affectedVersions");
      Assert.assertTrue(affectedVersions.contains("40.0.0"));
    }
  }

  @Test
  public void testSearchByUpdateDate() throws Exception {
    ImmutableMap<String, String> properties = ImmutableMap.<String, String>builder()
      .put(JiraSourceConfig.PROPERTY_FILTER_MODE, FilterMode.BASIC.getValue())
      .put(JiraSourceConfig.PROPERTY_START_UPDATE_DATE, createDate)
      .build();

    List<StructuredRecord> records = getPipelineResults(properties, 3);
    Assert.assertEquals(3, records.size());
  }
}
