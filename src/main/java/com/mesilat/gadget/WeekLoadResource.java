package com.mesilat.gadget;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
public class WeekLoadResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeekLoadResource.class);
    private static final long MSPERDAY = 24l * 60 * 60 * 1000;

    @Path("week")
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response getWeek(@QueryParam("week") String week) {
        ObjectMapper mapper = new ObjectMapper();
        JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        Locale locale = context.getLocale();

        Calendar start = Calendar.getInstance(locale);
        setStartOfWeek(start);
        if (week == null || week.isEmpty()){
            week = String.format("%04d%02d", start.get(Calendar.YEAR), start.get(Calendar.WEEK_OF_YEAR));
        } else {
            start.set(Calendar.YEAR, Integer.parseInt(week.substring(0,4)));
            start.set(Calendar.WEEK_OF_YEAR, Integer.parseInt(week.substring(4,6)));
        }
        String period = getPeriodFromWeek(start, locale);

        JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();
        ApplicationUser user = authContext.getLoggedInUser();

        ArrayNode arr1 = mapper.createArrayNode();
        ArrayNode arr2 = mapper.createArrayNode();

        try (Connection conn = getConnection()){
            Timestamp t1 = new Timestamp(start.getTimeInMillis()),
                t2 = new Timestamp(start.getTimeInMillis() + 7 * MSPERDAY);

            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryOne())) {
                ps.setString(1, user.getName());
                ps.setTimestamp(2, t1);
                ps.setTimestamp(3, t2);
                ps.setString(4, user.getName());
                ps.setTimestamp(5, t1);
                ps.setTimestamp(6, t2);
                ps.setString(7, user.getName());
                ps.setTimestamp(8, t1);
                ps.setTimestamp(9, t2);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("id",        rs.getLong(1));
                        node.put("issuekey",  rs.getString(2));
                        node.put("summary",   rs.getString(3));
                        node.put("resolved",  rs.getObject(4) != null);
                        node.put("day",       rs.getInt(5));
                        node.put("author",    rs.getString(6));
                        arr1.add(node);
                    }                    
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryTwo())) {
                ps.setString(1, user.getName());
                ps.setTimestamp(2, t1);
                ps.setTimestamp(3, t2);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("id",        rs.getLong(1));
                        node.put("day",       rs.getInt(2));
                        node.put("time",      rs.getLong(3));
                        node.put("comment",   rs.getString(4));
                        node.put("author",    rs.getString(5));
                        arr2.add(node);
                    }                    
                }
            }

            ObjectNode result = mapper.createObjectNode(),
                weeks = mapper.createObjectNode();

            for (Calendar cal : generateWeeks(start, locale)){
                weeks.put(
                    String.format("%04d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR)),
                    String.format("%02d/%04d", cal.get(Calendar.WEEK_OF_YEAR), cal.get(Calendar.YEAR))
                );
            }

            result.put("week", week);
            result.put("weeks", weeks);
            result.put("period", period);
            result.put("days", mapper.convertValue(generateDays(start, locale), ArrayNode.class));
            result.put("user", user.getName());
            result.put("display", user.getDisplayName());
            result.put("baseUrl", ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL));
            result.put("issues", arr1);
            result.put("worklog", arr2);
            StringWriter sw = new StringWriter();
            mapper.writerWithDefaultPrettyPrinter().writeValue(sw, result);
            return Response.ok(sw.toString()).build();        
        } catch (SQLException | IOException | GenericEntityException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();        
        }
    }
    private Connection getConnection() throws SQLException, GenericEntityException{
        //DelegatorInterface delegator = (DelegatorInterface)ComponentAccessor.getComponent(DelegatorInterface.class);
        //String helperName = delegator.getGroupHelperName("default");
        //return ConnectionFactory.getConnection(helperName);
        return DefaultOfBizConnectionFactory.getInstance().getConnection();
    }
    private String getPeriodFromWeek(Calendar start, Locale locale){
        Calendar end = (Calendar)start.clone();
        end.setTime(new Date(start.getTimeInMillis() + 6 * MSPERDAY));

        StringBuilder sb = new StringBuilder();
        String pattern = DateTimeFormat.patternForStyle("M-", locale);
        sb
            .append(DateTimeFormat.forPattern(pattern).print(start.getTimeInMillis()))
            .append(" - ")
            .append(DateTimeFormat.forPattern(pattern).print(end.getTimeInMillis()));
        return sb.toString();
    }
    private List<Calendar> generateWeeks(Calendar week, Locale locale){
        List<Calendar> weeks = new ArrayList<>();
        
        Calendar now = Calendar.getInstance(locale);
        setStartOfWeek(now);
        long diff = (now.getTimeInMillis() - week.getTimeInMillis()) / 7 / MSPERDAY;
        if (diff < 0)
            diff = 0;

        long w = diff > 5? week.getTimeInMillis() - 5 * 7 * MSPERDAY: now.getTimeInMillis() - 10 * 7 * MSPERDAY;

        for (int i = 0; i <= 10; i++){
            Calendar cal = Calendar.getInstance(locale);
            cal.setTime(new Date(w + i * 7 * MSPERDAY));
            weeks.add(cal);
        }
        return weeks;
    }
    private List<Map<String,Object>> generateDays(Calendar week, Locale locale){
        List<Map<String,Object>> days = new ArrayList<>();
        String[] names = (new DateFormatSymbols(locale)).getShortWeekdays();
        String pattern = DateTimeFormat.patternForStyle("S-", locale);
        DateTimeFormatter formatter = DateTimeFormat.forPattern(pattern);
        for (int i = 0; i < 7; i++){
            Calendar cal = (Calendar)week.clone();
            cal.setTimeInMillis(week.getTimeInMillis() + i * MSPERDAY);
            Map<String,Object> day = new HashMap<>();
            day.put("day", cal.get(Calendar.DAY_OF_WEEK));
            day.put("dayName", names[cal.get(Calendar.DAY_OF_WEEK)]);
            day.put("date", formatter.print(cal.getTimeInMillis()));
            days.add(day);
        }
        return days;
    }
    private void setStartOfWeek(Calendar cal){
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}