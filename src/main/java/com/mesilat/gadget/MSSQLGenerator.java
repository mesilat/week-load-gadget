package com.mesilat.gadget;

import java.text.MessageFormat;

public class MSSQLGenerator implements SQLGenerator {
    private static final String SQL1 = 
"select j.id, CONCAT(p.pkey, ''-'', j.issuenum) issuekey, j.summary, j.resolution, a.day, a.author " +
"from {0}.jiraissue j join {0}.project p on p.id = j.project join (" +
"    select g.issueid, DATEPART(dw,g.created) day, g.author" +
"    from {0}.changegroup g join {0}.changeitem i on groupid=g.id and i.field not in (''timeesimate'',''timespent'',''WorklogId'',''RemoteIssueLink'')" +
"    where g.author = ? and g.created >= ? and g.created < ?" +
"  union" +
"    select distinct a.issueid, DATEPART(dw,a.created) day, a.author" +
"    from {0}.jiraaction a" +
"    where a.author = ? and a.created >= ? and a.created < ?" +
"  union" +
"    select distinct w.issueid, DATEPART(dw,w.startdate) day, w.author" +
"    from {0}.worklog w" +
"    where w.author = ? and w.startdate >= ? and w.startdate < ?" +
"  ) a on j.id = a.issueid";

    private static final String SQL2 =
"select issueid, DATEPART(dw,startdate), timeworked, worklogbody, author from {0}.worklog where author = ? and startdate >= ? and startdate < ?";

    private final String sql1;
    private final String sql2;

    @Override
    public String getQueryOne() {
        return sql1;
    }

    @Override
    public String getQueryTwo() {
        return sql2;
    }

    public MSSQLGenerator(String schemaName){
        sql1 = MessageFormat.format(SQL1, schemaName);
        sql2 = MessageFormat.format(SQL2, schemaName);
    }
}