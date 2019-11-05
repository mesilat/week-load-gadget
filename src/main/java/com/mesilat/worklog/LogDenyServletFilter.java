package com.mesilat.worklog;

import com.atlassian.core.util.InvalidDurationException;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.worklog.TimeTrackingConfiguration;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.util.DurationFormatterProvider;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.JiraDurationUtils;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.mesilat.gadget.SQLFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
public class LogDenyServletFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.week-load-gadget");

    @ComponentImport
    private final IssueManager issueManager;
    @ComponentImport
    private final CustomFieldManager customFieldManager;
    @ComponentImport
    private final UserManager userManager;
    //private final SoyTemplateRenderer renderer;
    @ComponentImport
    private final TimeTrackingConfiguration timeTrackingConfiguration;
    @ComponentImport
    private final DurationFormatterProvider durationFormatterProvider;
    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        do {
            HttpServletRequest req = (HttpServletRequest)request;
            UserProfile userProfile = userManager.getRemoteUser(req);
            
            try {
                validateWorklog(request.getParameter("id"), req.getParameter("startDate"), req.getParameter("timeLogged"), userProfile, req.getLocale(), req.getParameter("worklogId"));
            } catch (ParseException | InvalidDurationException | SQLException ignore) {
                //LOGGER.debug("Worklog failed", ex);
            } catch(IllegalArgumentException ex){
                LOGGER.debug("Log work denied", ex);

                I18nHelper i18n = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();
                StringBuilder dlg = new StringBuilder();
                dlg.append("<div class=\"form-body\" id=\"logwork-error\">")
                    .append("<header>")
                    .append("<h1>")
                    .append(i18n.getText("com.mesilat.week-load-gadget.deny-msg-title"))
                    .append("</h1>")
                    .append("</header>")
                    .append("<div class=\"aui-message warning\">")
                    .append("<span class=\"aui-icon icon-warning\"></span>")
                    .append("<p>")
                    .append(ex.getMessage())
                    .append("</p>")
                    .append("</div>")
                    .append("</div>");
                //byte data[] = renderFromSoy("Mesilat.TimeSheet.Templates.denyLogMessage", Collections.emptyMap()).getBytes();
                byte data[] = dlg.toString().getBytes();

                HttpServletResponse resp = (HttpServletResponse)response;
                resp.setContentLength(data.length);
                resp.setContentType("text/html;charset=UTF-8");
                resp.setStatus(HttpServletResponse.SC_OK);
                try (OutputStream out = resp.getOutputStream()){
                    out.write(data);
                }
                return;
            }
        } while(false);
        
        chain.doFilter(request, response);
    }
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
    @Override
    public void destroy() {
    }

    private void validateWorklog(
        String issueId, String startDate, String duration, UserProfile userProfile, Locale locale, String worklogId
    ) throws ParseException, InvalidDurationException, SQLException {
        // Check issue is no-logging
        I18nHelper i18n = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();
        Issue issue = issueManager.getIssueObject(Long.parseLong(issueId));
        if (issue != null) {
            for (CustomField cf : customFieldManager.getCustomFieldObjects(issue)){
                if (cf.getCustomFieldType() instanceof LogDenyCustomField){
                    if ("true".equals(cf.getValue(issue))){
                        throw new IllegalArgumentException(i18n.getText("com.mesilat.week-load-gadget.err.issue-nologging"));
                    }
                }
            }
        }        

        if (userManager.isUserInGroup(userProfile.getUserKey(), "worklog_overdue_allow")){
            LOGGER.debug("User has 'worklog_overdue_allow', validation considered successful");
            return;
        }

        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        Object maxBacklogPeriod = pluginSettings.get(Constants.PARAM_MAX_BACKLOG_PERIOD);
        Object maxLogPerDay = pluginSettings.get(Constants.PARAM_MAX_LOG_PER_DAY);
        LOGGER.debug(String.format("Max backlog period %s; max log per day %s", maxBacklogPeriod, maxLogPerDay));

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat(
            ComponentAccessor.getApplicationProperties().getDefaultBackedString(APKeys.JIRA_DATE_TIME_PICKER_JAVA_FORMAT),
            ComponentAccessor.getLocaleManager().getLocaleFor(ComponentAccessor.getUserManager().getUserByKey(userProfile.getUserKey().getStringValue()))
        );
        LOGGER.debug(String.format("Format %s, startDate %s, locale %s", ComponentAccessor.getApplicationProperties().getDefaultBackedString(APKeys.JIRA_DATE_TIME_PICKER_JAVA_FORMAT), startDate, locale.toLanguageTag()));
        cal.setTime(fmt.parse(startDate));
        LOGGER.debug("CP #2");
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        LOGGER.debug("CP #3");

        // Check log date
        if (maxBacklogPeriod != null){
            LOGGER.debug("CP #4");
            Calendar treshold = Calendar.getInstance();
            treshold.setTimeInMillis(System.currentTimeMillis());
            treshold.set(Calendar.HOUR, 0);
            treshold.set(Calendar.MINUTE, 0);
            treshold.set(Calendar.SECOND, 0);
            treshold.set(Calendar.MILLISECOND, 0);
            treshold.add(Calendar.DATE, -Integer.parseInt(maxBacklogPeriod.toString()));

            if (cal.before(treshold)){
                throw new IllegalArgumentException(i18n.getText("com.mesilat.week-load-gadget.err.invalid-date"));
            } else {
                LOGGER.debug(String.format("Log date %s is after treshold %s", cal.toString(), treshold.toString()));
            }
        }
        LOGGER.debug("CP #5");

        // Check total log for date
        if (maxLogPerDay == null){
            LOGGER.debug("Max log per day is null, not checking log per day");
        } else {
            LOGGER.debug(String.format("Max log per day: %s", maxLogPerDay));

            JiraDurationUtils durationUtils = new JiraDurationUtils(timeTrackingConfiguration, durationFormatterProvider, eventPublisher);
            Long period = durationUtils.parseDuration(duration, locale);
            //LOGGER.debug(String.format("Query log (SQL): %s", SQLFactory.getQueryTotalWorklog()));

            String userKey = userProfile.getUsername();
            try (Connection conn = DefaultOfBizConnectionFactory.getInstance().getConnection()){
                try (PreparedStatement ps = conn.prepareStatement(SQLFactory.getQueryUserName())){
                    ps.setString(1, userProfile.getUsername());
                    try (ResultSet rs = ps.executeQuery()){
                        if (rs.next()){
                            userKey = rs.getString(1);
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareCall(SQLFactory.getQueryTotalWorklog())){
                    ps.setString(1, userKey);
                    ps.setDate(2, new java.sql.Date(cal.getTimeInMillis()));
                    ps.setString(3, worklogId == null? "-1": worklogId);
                    Long total = null;
                    try (ResultSet rs = ps.executeQuery()){
                        if (rs.next()){
                            total = rs.getLong(1);
                        }
                    }
                    if (total == null){
                        total = 0L;
                    }
                    LOGGER.debug(String.format("Log (max/total+period): %d: %d", Long.parseLong(maxLogPerDay.toString()) * 60 * 60, total + period));

                    if (Long.parseLong(maxLogPerDay.toString()) * 60 * 60 < total + period){
                        throw new IllegalArgumentException(i18n.getText("com.mesilat.week-load-gadget.err.too-much"));
                    }
                }
            }
        }
    }

    @Autowired
    public LogDenyServletFilter(final IssueManager issueManager,
            final CustomFieldManager customFieldManager, final UserManager userManager,
            final TimeTrackingConfiguration timeTrackingConfiguration,
            final DurationFormatterProvider durationFormatterProvider,
            final EventPublisher eventPublisher,
            final PluginSettingsFactory pluginSettingsFactory
    ){
        this.issueManager = issueManager;
        this.customFieldManager = customFieldManager;
        this.userManager = userManager;
        this.timeTrackingConfiguration = timeTrackingConfiguration;
        this.durationFormatterProvider = durationFormatterProvider;
        this.eventPublisher = eventPublisher;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }    

    //private String renderFromSoy(String soyTemplate, Map soyContext) {
    //    return renderer.render("com.mesilat.week-load-gadget:resources", soyTemplate, soyContext);
    //}

    private static void toString(StringBuilder sb, String[] s){
        sb.append("{");
        for (int i = 0; i < s.length; i++){
            if (i > 0){
                sb.append(",");
            }
            sb.append(s[i]);
        }
        sb.append("}");
    }
}