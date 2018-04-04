package com.mesilat.worklog;

import com.atlassian.jira.issue.customfields.impl.GenericTextCFType;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;

public class LogDenyCustomField extends GenericTextCFType {
    public LogDenyCustomField(
        CustomFieldValuePersister customFieldValuePersister,
        GenericConfigManager genericConfigManager
    ){
        super(customFieldValuePersister, genericConfigManager);
    }
}