package com.mesilat.worklog;

import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.worklog.TimeTrackingConfiguration;
import com.mesilat.gadget.*;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.DurationFormatterProvider;
import com.atlassian.jira.util.JiraDurationUtils;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.io.IOException;
import java.io.StringWriter;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

@Path("/report")
public class ReportResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.week-load");
    private static final long MSPERDAY = 24l * 60 * 60 * 1000;

    @ComponentImport
    private final TimeTrackingConfiguration timeTrackingConfiguration;
    @ComponentImport
    private final DurationFormatterProvider durationFormatterProvider;
    @ComponentImport
    private final EventPublisher eventPublisher;

    @Path("/")
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response getReport(@QueryParam("start") String start, @QueryParam("end") String end){
        ObjectMapper mapper = new ObjectMapper();
        JiraAuthenticationContext context = ComponentAccessor.getJiraAuthenticationContext();

        //SimpleDateFormat fmt = new SimpleDateFormat(ComponentAccessor.getApplicationProperties().getDefaultBackedString(APKeys.JIRA_DATE_PICKER_JAVA_FORMAT));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        if (start == null || end == null){
            throw new InvalidParameterException("Please specify period start date and end date");
        }
        Date _start = null, _end = null;
        try {
            _start = fmt.parse(start);
        } catch (ParseException ex) {
            throw new InvalidParameterException(String.format("Failed to parse \"%s\": %s", start, ex.getMessage()));
        }
        try {
            _end = fmt.parse(end);
        } catch (ParseException ex) {
            throw new InvalidParameterException(String.format("Failed to parse \"%s\": %s", end, ex.getMessage()));
        }

        JiraDurationUtils durationUtils = new JiraDurationUtils(timeTrackingConfiguration, durationFormatterProvider, eventPublisher);        
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode result = mapper.createObjectNode();
        long total = 0;
        try (Connection conn = getConnection()){
            String userKey = context.getLoggedInUser().getUsername();
            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryUserName())){
                ps.setString(1, context.getLoggedInUser().getUsername());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()){
                        userKey = rs.getString(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryReport())) {
                ps.setString(1, userKey);
                ps.setDate(2, new java.sql.Date(_start.getTime()));
                ps.setDate(3, new java.sql.Date(_end.getTime() + MSPERDAY));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("id",        rs.getLong(1));
                        node.put("issuekey",  rs.getString(2));
                        node.put("summary",   rs.getString(3));
                        node.put("timeWorked", durationUtils.getFormattedDuration(rs.getLong(6), context.getLocale()));
                        ObjectNode status = mapper.createObjectNode();
                        status.put("id",      rs.getLong(4));
                        status.put("name",    rs.getString(5));
                        node.put("status",    status);
                        arr.add(node);
                        total += rs.getLong(6);
                    }                    
                }
            }
            result.put("report", arr);
            result.put("totalTimeWorked", durationUtils.getFormattedDuration(total, context.getLocale()));
            StringWriter sw = new StringWriter();
            mapper.writerWithDefaultPrettyPrinter().writeValue(sw, result);
            return Response.ok(sw.toString()).build();        
        } catch (SQLException | IOException | GenericEntityException ex) {
            throw new RuntimeException(ex);
        }
    }
    private Connection getConnection() throws SQLException, GenericEntityException{
        return DefaultOfBizConnectionFactory.getInstance().getConnection();
    }

    public ReportResource(
        final TimeTrackingConfiguration timeTrackingConfiguration,
        final DurationFormatterProvider durationFormatterProvider,
        final EventPublisher eventPublisher
    ){
        this.timeTrackingConfiguration = timeTrackingConfiguration;
        this.durationFormatterProvider = durationFormatterProvider;
        this.eventPublisher = eventPublisher;
    }
}