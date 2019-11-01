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

import com.atlassian.jira.rest.client.api.ExpandableProperty;
import com.atlassian.jira.rest.client.api.domain.Attachment;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.BasicUser;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.Subtask;
import com.atlassian.jira.rest.client.api.domain.TimeTracking;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.api.domain.Worklog;
import io.cdap.cdap.api.data.format.StructuredRecord;
import io.cdap.cdap.api.data.schema.Schema;
import org.joda.time.DateTime;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Converts {@link Issue} to {@link StructuredRecord} using the schema provided in the widget.
 */
public class IssueToRecordTransformer {
  private final Schema schema;

  public IssueToRecordTransformer(Schema schema) {
    this.schema = schema;
  }

  public StructuredRecord transform(Issue issue) {
    String resolution = (issue.getResolution() == null) ? null : issue.getResolution().getName();
    Integer watchers = (issue.getWatchers() == null) ? null : issue.getWatchers().getNumWatchers();
    Boolean isWatching = (issue.getWatchers() == null) ? null : issue.getWatchers().isWatching();
    String priority = (issue.getPriority() == null) ? null : issue.getPriority().getName();

    StructuredRecord.Builder builder = StructuredRecord.builder(schema);
    setIfPresent(builder, "key", issue.getKey());
    setIfPresent(builder, "summary", issue.getSummary());
    setIfPresent(builder, "id", issue.getId());
    setIfPresent(builder, "project", issue.getProject().getName());
    setIfPresent(builder, "status", issue.getStatus().getName());
    setIfPresent(builder, "description", issue.getDescription());
    setIfPresent(builder, "resolution", resolution);

    if (schema.getField("reporter") != null) {
      setIfPresent(builder, "reporter", createUserRecord(issue.getReporter(),
                                                         schema.getField("reporter")
                                                           .getSchema()));
    }

    if (schema.getField("assignee") != null) {
      setIfPresent(builder, "assignee", createUserRecord(issue.getAssignee(),
                                                         schema.getField("assignee")
                                                           .getSchema()));
    }

    if (schema.getField("fields") != null) {
      setIfPresent(builder, "fields", createFieldRecords(issue.getFields(), schema.getField("fields")
        .getSchema()));
    }
    setIfPresent(builder, "affectedVersions", getVersionStrings(issue.getAffectedVersions()));
    setIfPresent(builder, "fixVersions", getVersionStrings(issue.getFixVersions()));
    setIfPresent(builder, "components", getComponentStrings(issue.getComponents()));
    setIfPresent(builder, "priority", priority);
    setIfPresent(builder, "issueType", issue.getIssueType().getName());
    setIfPresent(builder, "isSubtask", issue.getIssueType().isSubtask());

    if (schema.getField("creationDate") != null) {
      builder.setTimestamp("creationDate", toZonedDateTime(issue.getCreationDate()));
    }

    if (schema.getField("updateDate") != null) {
      builder.setTimestamp("updateDate", toZonedDateTime(issue.getUpdateDate()));
    }

    if (schema.getField("dueDate") != null) {
      builder.setTimestamp("dueDate", toZonedDateTime(issue.getDueDate()));
    }

    if (schema.getField("attachments") != null) {
      setIfPresent(builder, "attachments",
                   createAttachmentRecords(issue.getAttachments(), schema.getField("attachments").getSchema()));
    }

    if (schema.getField("comments") != null) {
      setIfPresent(builder, "comments",
                   createCommentRecords(issue.getComments(), schema.getField("comments").getSchema()));
    }

    if (schema.getField("issueLinks") != null) {
      setIfPresent(builder, "issueLinks",
                   createIssueLinkRecords(issue.getIssueLinks(), schema.getField("issueLinks").getSchema()));
    }
    setIfPresent(builder, "votes", issue.getVotes().getVotes());
    if (schema.getField("worklog") != null) {
      setIfPresent(builder, "worklog",
                   createWorklogRecords(issue.getWorklogs(), schema.getField("worklog").getSchema()));
    }
    setIfPresent(builder, "watchers", watchers);
    setIfPresent(builder, "isWatching", isWatching);
    if (schema.getField("timeTracking") != null) {
      setIfPresent(builder, "timeTracking",
                   createTimeTrackingRecord(issue.getTimeTracking(), schema.getField("timeTracking")
                     .getSchema()));
    }
    if (schema.getField("subtasks") != null) {
      setIfPresent(builder, "subtasks",
                   createSubtaskRecords(issue.getSubtasks(), schema.getField("subtasks").getSchema()));
    }
    setIfPresent(builder, "labels", issue.getLabels());

    return builder.build();
  }

  private void setIfPresent(StructuredRecord.Builder builder, String fieldName,
                            @Nullable Object value) {
    if (schema.getField(fieldName) != null) {
      builder.set(fieldName, value);
    }
  }

  private static List<String> getVersionStrings(Iterable<Version> versions) {
    if (versions == null) {
      return null;
    }

    List<String> versionsList = new ArrayList<String>();
    for (Version version : versions) {
      versionsList.add(version.getName());
    }
    return versionsList;
  }

  private static List<String> getComponentStrings(Iterable<BasicComponent> components) {
    if (components == null) {
      return null;
    }

    List<String> componentsList = new ArrayList<String>();
    for (BasicComponent component : components) {
      componentsList.add(component.getName());
    }
    return componentsList;
  }

  private static List<StructuredRecord> createAttachmentRecords(Iterable<Attachment> attachments, Schema schema) {
    if (attachments == null) {
      return null;
    }

    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (Attachment attachment : attachments) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);
      builder.set("filename", attachment.getFilename());
      builder.set("author", attachment.getAuthor().getName());
      builder.setTimestamp("creationDate", toZonedDateTime(attachment.getCreationDate()));
      builder.set("size", attachment.getSize());
      builder.set("mimeType", attachment.getMimeType());
      builder.set("contentUri", attachment.getContentUri().toString());

      records.add(builder.build());
    }

    return records;
  }

  private static List<StructuredRecord> createCommentRecords(Iterable<Comment> comments, Schema schema) {
    if (comments == null) {
      return null;
    }

    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (Comment comment : comments) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);

      BasicUser author = comment.getAuthor();
      if (author != null) {
        builder.set("author", author.getName());
      }

      BasicUser updateAuthor = comment.getUpdateAuthor();
      if (updateAuthor != null) {
        builder.set("updateAuthor", updateAuthor.getName());
      }

      builder.setTimestamp("creationDate", toZonedDateTime(comment.getCreationDate()));
      builder.setTimestamp("updateDate", toZonedDateTime(comment.getUpdateDate()));
      builder.set("body", comment.getBody());

      records.add(builder.build());
    }

    return records;
  }

  private static List<StructuredRecord> createIssueLinkRecords(Iterable<IssueLink> issueLinks, Schema schema) {
    if (issueLinks == null) {
      return null;
    }

    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (IssueLink issueLink : issueLinks) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);
      builder.set("type", issueLink.getIssueLinkType().getName());
      builder.set("link", issueLink.getTargetIssueUri().toString());

      records.add(builder.build());
    }

    return records;
  }

  private static List<StructuredRecord> createWorklogRecords(Iterable<Worklog> worklogs, Schema schema) {
    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (Worklog worklog : worklogs) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);
      builder.set("author", worklog.getAuthor().getName());
      builder.set("updateAuthor", worklog.getUpdateAuthor().getName());
      builder.setTimestamp("startDate", toZonedDateTime(worklog.getStartDate()));
      builder.setTimestamp("creationDate", toZonedDateTime(worklog.getCreationDate()));
      builder.setTimestamp("updateDate", toZonedDateTime(worklog.getUpdateDate()));
      builder.set("comment", worklog.getComment());
      builder.set("minutesSpent", worklog.getMinutesSpent());

      records.add(builder.build());
    }

    return records;
  }

  private static List<StructuredRecord> createFieldRecords(Iterable<IssueField> fields, Schema schema) {
    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (IssueField field : fields) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);
      builder.set("id", field.getId());
      builder.set("name", field.getName());
      builder.set("type", field.getType());

      Object value = field.getValue();
      if (value != null) {
        builder.set("value", value.toString());
      }

      records.add(builder.build());
    }

    return records;
  }

  private static List<StructuredRecord> createSubtaskRecords(Iterable<Subtask> subtasks, Schema schema) {
    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    Schema componentSchema = schema.getComponentSchema();
    List<StructuredRecord> records = new ArrayList<>();

    for (Subtask subtask : subtasks) {
      StructuredRecord.Builder builder = StructuredRecord.builder(componentSchema);
      builder.set("key", subtask.getIssueKey());
      builder.set("summary", subtask.getSummary());
      builder.set("issueType", subtask.getIssueType().getName());
      builder.set("status", subtask.getStatus().getName());

      records.add(builder.build());
    }

    return records;
  }

  private static StructuredRecord createTimeTrackingRecord(TimeTracking timeTracking, Schema schema) {
    if (timeTracking == null) {
      return null;
    }

    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    StructuredRecord.Builder builder = StructuredRecord.builder(schema);

    builder.set("originalEstimateMinutes", timeTracking.getOriginalEstimateMinutes());
    builder.set("remainingEstimateMinutes", timeTracking.getRemainingEstimateMinutes());
    builder.set("timeSpentMinutes", timeTracking.getTimeSpentMinutes());

    return builder.build();
  }

  private static StructuredRecord createUserRecord(User user, Schema schema) {
    if (user == null) {
      return null;
    }

    if (schema.isNullable()) {
      schema = schema.getNonNullable();
    }

    StructuredRecord.Builder builder = StructuredRecord.builder(schema);

    ExpandableProperty<String> groups = user.getGroups();

    builder.set("name", user.getName());
    builder.set("displayName", user.getDisplayName());
    builder.set("emailAddress", user.getEmailAddress());
    builder.set("active", user.isActive());
    builder.set("timezone", user.getTimezone());

    if (groups != null) {
      builder.set("groups", groups.getItems());
    }

    builder.set("avatarUri", user.getAvatarUri().toString());

    return builder.build();
  }

  private static ZonedDateTime toZonedDateTime(DateTime dateTime) {
    if (dateTime == null) {
      return null;
    }

    return ZonedDateTime.ofLocal(
      LocalDateTime.of(
        dateTime.getYear(),
        dateTime.getMonthOfYear(),
        dateTime.getDayOfMonth(),
        dateTime.getHourOfDay(),
        dateTime.getMinuteOfHour(),
        dateTime.getSecondOfMinute(),
        dateTime.getMillisOfSecond() * 1_000_000),
      ZoneId.of(dateTime.getZone().getID(), ZoneId.SHORT_IDS),
      ZoneOffset.ofTotalSeconds(dateTime.getZone().getOffset(dateTime) / 1000));
  }
}
