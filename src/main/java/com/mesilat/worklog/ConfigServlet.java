package com.mesilat.worklog;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.JiraVelocityUtils;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.upm.api.license.PluginLicenseManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
public class ConfigServlet extends HttpServlet {
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;
    @ComponentImport
    private final JiraAuthenticationContext authenticationContext;
    @ComponentImport
    private final TemplateRenderer renderer;
    @ComponentImport
    private final TransactionTemplate trasnsactionTemplate;
    @ComponentImport
    private final PluginLicenseManager licenseManager;
    @ComponentImport
    private final GlobalPermissionManager permissionManager;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp, null);
    }
    private void doGet(HttpServletRequest req, HttpServletResponse resp, Object result) throws ServletException, IOException {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        I18nHelper i18n = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();

        resp.setContentType("text/html;charset=utf-8");
        Map<String, Object> initContext = new HashMap<>();
        initContext.put("maxBacklogPeriod", pluginSettings.get(Constants.PARAM_MAX_BACKLOG_PERIOD));
        initContext.put("maxLogPerDay", pluginSettings.get(Constants.PARAM_MAX_LOG_PER_DAY));
        initContext.put("isLicensed", isLicensed());
        if (result instanceof RuntimeException){
            initContext.put("error", ((RuntimeException)result).getMessage());
        } else if (Boolean.TRUE.equals(result)) {
            initContext.put("success", i18n.getText("com.mesilat.week-load-gadget.config.success"));
        }
        Map<String, Object> root = JiraVelocityUtils.getDefaultVelocityParams(initContext, authenticationContext);
        renderer.render("templates/config.vm", root, resp.getWriter());
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String maxBacklogPeriod = req.getParameter("max-backlog-period");
        String maxLogPerDay = req.getParameter("max-log-per-day");

        try {
            if (!permissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser())){
                throw new RuntimeException("User must be JIRA administrator to update the plugin settings");
            }
            trasnsactionTemplate.execute(()->{
                PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

                if (maxBacklogPeriod == null || maxBacklogPeriod.isEmpty()){
                    settings.remove(Constants.PARAM_MAX_BACKLOG_PERIOD);
                } else {
                    settings.put(Constants.PARAM_MAX_BACKLOG_PERIOD, Integer.toString( Integer.parseInt(maxBacklogPeriod) ));
                }

                if (maxLogPerDay == null || maxLogPerDay.isEmpty()){
                    settings.remove(Constants.PARAM_MAX_LOG_PER_DAY);
                } else {
                    settings.put(Constants.PARAM_MAX_LOG_PER_DAY, Integer.toString( Integer.parseInt(maxLogPerDay) ));
                }
                return null;
            });
        } catch(RuntimeException ex) {
            doGet(req, resp, ex);
        }
        doGet(req, resp, Boolean.TRUE);
    }
    @Override
    public String getServletInfo() {
        return "Weekly Activity Gadget configuration";
    }

    protected boolean isLicensed() {
        try {
            return licenseManager.getLicense().get().isValid();
        } catch(Throwable ignore) {
            return false;
        }
    }

    @Autowired
    public ConfigServlet(
        PluginSettingsFactory pluginSettingsFactory,
        JiraAuthenticationContext authenticationContext,
        TemplateRenderer renderer,
        TransactionTemplate trasnsactionTemplate,
        PluginLicenseManager licenseManager,
        GlobalPermissionManager permissionManager
    ){
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.authenticationContext = authenticationContext;
        this.renderer = renderer;
        this.trasnsactionTemplate = trasnsactionTemplate;
        this.licenseManager = licenseManager;
        this.permissionManager = permissionManager;
    }
}