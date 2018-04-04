package com.mesilat.worklog;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Named;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Named("com.mesilat.log-denied-service")
@Scanned
public class LogDenyService implements InitializingBean, DisposableBean {
    @ComponentImport
    private final EventPublisher eventPublisher;
    @ComponentImport
    private final CustomFieldManager customFieldManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Long eventTypeId = issueEvent.getEventTypeId();

        if (eventTypeId.equals(EventType.ISSUE_WORKLOGGED_ID)
                || eventTypeId.equals(EventType.ISSUE_WORKLOG_UPDATED_ID)
                || eventTypeId.equals(EventType.ISSUE_WORKLOG_DELETED_ID)
        ){
            customFieldManager.getCustomFieldObjects(issueEvent.getIssue()).forEach((cf)->{
                if (cf.getCustomFieldType() instanceof LogDenyCustomField){
                    if ("true".equals(cf.getValue(issueEvent.getIssue()))){
                        throw new RuntimeException("Cannot log work to this issue");
                    }
                }
            });
        }
    }

    @Autowired
    public LogDenyService(final EventPublisher eventPublisher,
            final CustomFieldManager customFieldManager){
        this.eventPublisher = eventPublisher;
        this.customFieldManager = customFieldManager;
    }
}