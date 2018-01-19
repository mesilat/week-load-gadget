package com.mesilat.gadget;

public class PostgreSQLGenerator implements SQLGenerator {
    private static final String SQL1 =
"select j.id, CONCAT(p.pkey, '-', j.issuenum) issuekey, j.summary, j.resolution, a.day, a.author " +
"from jiraissue j join project p on p.id = j.project join (" +
"    select g.issueid, EXTRACT(DOW FROM g.created) + 1 AS day, g.author" +
"    from changegroup g join changeitem i on groupid=g.id and i.field not in ('timeesimate','timespent','WorklogId','RemoteIssueLink')" +
"    where g.author = ? and g.created >= ? and g.created < ?" +
"  union" +
"    select distinct a.issueid, EXTRACT(DOW FROM a.created) + 1, a.author" +
"    from jiraaction a" +
"    where a.author = ? and a.created >= ? and a.created < ?" +
"  union" +
"    select distinct w.issueid, EXTRACT(DOW FROM w.startdate) + 1, w.author" +
"    from worklog w" +
"    where w.author = ? and w.startdate >= ? and w.startdate < ?" +
"  ) a on j.id = a.issueid";

    private static final String SQL2 =
"select issueid, EXTRACT(DOW FROM startdate) + 1, timeworked, worklogbody, author from worklog where author = ? and startdate >= ? and startdate < ?";

    @Override
    public String getQueryOne() {
        return SQL1;
    }

    @Override
    public String getQueryTwo() {
        return SQL2;
    }
}