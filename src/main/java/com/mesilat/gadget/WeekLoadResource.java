package com.mesilat.gadget;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.upm.api.license.PluginLicenseManager;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Path("/week")
public class WeekLoadResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.week-load");
    private static final long MSPERDAY = 24l * 60 * 60 * 1000;
    private static final int MAX_WEEKS = 30;

    @ComponentImport
    private final PluginLicenseManager licenseManager;

    @Path("/")
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response getWeek(@QueryParam("week") String week, @QueryParam("tabs") Long tabs) {
        ObjectMapper mapper = new ObjectMapper();
        JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();
        Locale locale = context.getLocale();
        
        if ("English (UK)".equals(locale.getDisplayName())){
            locale = Locale.UK;
        }

        DateTimeFormatter fmt = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.SHORT)
        .withLocale(locale);

        Calendar now = GregorianCalendar.getInstance();
        TimeZone tz = now.getTimeZone();
        ZoneId zid = tz == null ? ZoneId.systemDefault() : tz.toZoneId();

        Calendar start = Calendar.getInstance(locale);
        setStartOfWeek(start);
        if (week == null || week.isEmpty()){
            week = String.format("%04d%02d", getYear(start), start.get(Calendar.WEEK_OF_YEAR));
        } else {
            start.set(Calendar.YEAR, Integer.parseInt(week.substring(0,4)));
            start.set(Calendar.WEEK_OF_YEAR, Integer.parseInt(week.substring(4,6)));
        }
        String period = getPeriodFromWeek(start, locale);

        ApplicationUser user = context.getLoggedInUser();

        ArrayNode arr1 = mapper.createArrayNode();
        ArrayNode arr2 = mapper.createArrayNode();

        java.sql.Date t1 = new java.sql.Date(start.get(Calendar.YEAR) - 1900, start.get(Calendar.MONTH), start.get(Calendar.DATE)),
            t2 = new java.sql.Date(t1.getTime() + 7 * MSPERDAY);
        List<Map<String,Object>> days = generateDays(start, locale, fmt, zid);
        LOGGER.debug(String.format("Get week load for user %s and period %d to %d", user.getName(), t1.getTime(), t2.getTime()));

        try (Connection conn = getConnection()){
            String userKey = user.getName();
            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryUserName())){
                ps.setString(1, user.getName());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()){
                        userKey = rs.getString(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryOne())) {
                ps.setString(1, userKey);
                ps.setDate(2, t1);
                ps.setDate(3, t2);
                ps.setString(4, userKey);
                ps.setDate(5, t1);
                ps.setDate(6, t2);
                ps.setString(7, userKey);
                ps.setDate(8, t1);
                ps.setDate(9, t2);
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
                ps.setString(1, userKey);
                ps.setDate(2, t1);
                ps.setDate(3, t2);
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

            for (Calendar cal : generateWeeks(start, locale, tabs)){
                int year = getYear(cal);
                weeks.put(
                    String.format("%04d%02d", year, cal.get(Calendar.WEEK_OF_YEAR)),
                    String.format("%02d/%04d", cal.get(Calendar.WEEK_OF_YEAR), year)
                );
            }

            result.put("week", week);
            result.put("weeks", weeks);
            result.put("period", period);
            result.put("days", mapper.convertValue(days, ArrayNode.class));
            result.put("user", user.getName());
            result.put("display", user.getDisplayName());
            result.put("baseUrl", ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL));
            result.put("issues", arr1);
            result.put("worklog", arr2);
            result.put("isLicensed", isLicensed());

            ObjectNode timefmt = mapper.createObjectNode();
            timefmt.put("locale", locale.getDisplayName());
            timefmt.put("timezone", tz == null? (String)null: tz.getDisplayName());
            timefmt.put("zoneId", tz == null? (String)null: zid.getDisplayName(TextStyle.FULL, locale));
            timefmt.put("short", fmt.format(LocalDateTime.ofInstant(now.toInstant(), zid)));
            result.put("timefmt", timefmt);
            

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
        DateTimeFormatter fmt = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.SHORT)
        .withLocale(locale);

        TimeZone tz = start.getTimeZone();
        ZoneId zid = tz == null ? ZoneId.systemDefault() : tz.toZoneId();

        sb
            .append(fmt.format(LocalDateTime.ofInstant(start.toInstant(), zid)))
            .append(" - ")
            .append(fmt.format(LocalDateTime.ofInstant(end.toInstant(), zid)));
        return sb.toString();
    }
    private List<Calendar> generateWeeks(Calendar week, Locale locale, Long tabs){
        List<Calendar> weeks = new ArrayList<>();
        
        Calendar now = Calendar.getInstance(locale);
        setStartOfWeek(now);
        long diff = (now.getTimeInMillis() - week.getTimeInMillis()) / 7 / MSPERDAY;
        if (diff < 0)
            diff = 0;

        long w = now.getTimeInMillis() - MAX_WEEKS * 7 * MSPERDAY;
        if (tabs != null && diff > tabs / 2){
            w -= (diff - (tabs - 1)/2) * 7 * MSPERDAY;
        }

        for (int i = 0; i <= MAX_WEEKS; i++){
            Calendar cal = Calendar.getInstance(locale);
            cal.setTime(new Date(w + i * 7 * MSPERDAY));
            weeks.add(cal);
        }
        return weeks;
    }
    private int getYear(Calendar cal){
        /*
        Java Calendar is hell:
        
        Locale locale = Locale.getDefault();
        Calendar cal = Calendar.getInstance(locale);
        cal.set(Calendar.YEAR, 2019);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        System.out.println(String.format("%04d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR)));
        // Prints 201901

        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        System.out.println(String.format("%04d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR)));
        // Prints 201801 (though same week)
        */
        if (cal.get(Calendar.WEEK_OF_YEAR) == 1){
            Calendar _cal = (Calendar)cal.clone();
            _cal.add(Calendar.DAY_OF_MONTH, 7);
            return _cal.get(Calendar.YEAR);
        } else {
            return cal.get(Calendar.YEAR);
        }
    }
    private List<Map<String,Object>> generateDays(Calendar week, Locale locale, DateTimeFormatter fmt, ZoneId zid){
        List<Map<String,Object>> days = new ArrayList<>();
        String[] names = (new DateFormatSymbols(locale)).getShortWeekdays();

        for (int i = 0; i < 7; i++){
            Calendar cal = (Calendar)week.clone();
            cal.setTimeInMillis(week.getTimeInMillis() + i * MSPERDAY);
            Map<String,Object> day = new HashMap<>();
            day.put("day", cal.get(Calendar.DAY_OF_WEEK));
            day.put("dayName", names[cal.get(Calendar.DAY_OF_WEEK)]);
            day.put("date", fmt.format(LocalDateTime.ofInstant(cal.toInstant(), zid)));
            switch (cal.get(Calendar.DAY_OF_WEEK)){
                case Calendar.SATURDAY:
                case Calendar.SUNDAY:
                    day.put("weekend", true);
                    break;
                default:
                    day.put("weekend", false);
                    break;
            }
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
    protected boolean isLicensed() {
        try {
            return licenseManager.getLicense().get().isValid();
        } catch(Throwable ignore) {
            return false;
        }
    }

    @Autowired
    public WeekLoadResource(PluginLicenseManager licenseManager){
        this.licenseManager = licenseManager;
    }
}