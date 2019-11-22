# Jira Streaming Source

Description
-----------
The plugin fetches issues from JIRA.
JQL, existing JIRA filters, or custom filters can be used in order to read a subset of issues.


Properties
----------

### Basic

**Reference Name:** Name used to uniquely identify this source for lineage, annotating metadata, etc.

**Jira URL:** URL of JIRA instance.

### Authentication

**Username:** Username used to authenticate to JIRA instance via basic authentication.

**Password:** Password used to authenticate to JIRA instance via basic authentication.
If both username and password are not set, JIRA will be accessed via anonymous user.

### Advanced

**Track Updates:** If enabled, source will track updates of issues, not only their creations.

**Filter Mode:** Mode which specifies which issues to fetch.
Possible values are:
- Basic
- JQL
- Jira Filter Id

***Basic Filter Mode***

Enables to specify basic filters using plugin properties.
Configurations which are lists has 'OR' relationship implied, which means issue
should only conform to one of the values specified.

Related configurations:

**Projects**: List of jira projects.

**Issue types**: List of issue types. 
E.g.: Sub-Task, Bug, Epic, Improvement, New Feature, Story, Task, etc.

**Statuses**: List of issue statuses. 
E.g.: Open, In Progress, Reopened, Resolved, Closed, etc.

**Priorities**: List of priorities.
E.g.: Minor, Major, Critical, Blocker, etc.

**Reporters**: List of reporters.

**Assignees**: List of assignees.

**Fix Versions**: List of fix versions.

**Affected Versions**: List of affected versions.

**Labels**: List of issue labels.

**Last Update Start Date**: Earliest update date to include.
Valid formats include: 'yyyy/MM/dd HH:mm', 'yyyy-MM-dd HH:mm', 'yyyy/MM/dd', 
'yyyy-MM-dd', or a period format e.g. '-5d', '4w 2d'.

**Last Update End Date**: Latest update date to include.
Valid formats include: 'yyyy/MM/dd HH:mm', 'yyyy-MM-dd HH:mm', 'yyyy/MM/dd', 
'yyyy-MM-dd', or a period format e.g. '-5d', '4w 2d'.


***JQL Filter Mode***

**JQL Query:** Query in JQL syntax used to filter the issues.
E.g.: `project = NETTY AND priority >= Critical AND fixVersion IN (4.2, 4.3) AND updateDate > -5d`
If empty will get all the issues from JIRA instance.


***Jira Filter Id Mode***

**Jira Filter Id:** Numerical id of an existing JIRA filter.

**Max Issues Per Request:** Maximum number of issues to fetch in a single request. 
A value of zero means all.


**Schema:** Output schema. Fields can be removed from it, if particular information is not needed.