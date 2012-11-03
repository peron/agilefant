package com.canoo.agilefant.jira;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;

public class JiraSyncBackgroundWorker {
    private static final Logger LOG = Logger.getLogger(JiraSyncBackgroundWorker.class.getName());
    
    @Autowired
    private JiraSyncServiceImpl jiraSyncService;

    private String jiraProjectKey;
    private String agilefantProductName;
    private String jiraURL;
    private String jiraUser;
    private String jiraPassword;

    public void synchronizeJira() {
        LOG.info("Running JIRA synchronization.");
        try {
            String serviceURL = jiraURL + "/rpc/soap/jirasoapservice-v2";
            jiraSyncService.synchronizeProject(agilefantProductName, null, jiraProjectKey, serviceURL, jiraUser, jiraPassword);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.severe("Synchronization with JIRA faield! Cause: " + e.getLocalizedMessage());
        }
        LOG.info("JIRA synchronization finished.");
    }

    public void setJiraSyncService(JiraSyncServiceImpl jiraSyncService) {
        this.jiraSyncService = jiraSyncService;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    public void setAgilefantProductName(String agilefantProductName) {
        this.agilefantProductName = agilefantProductName;
    }

    public void setJiraURL(String jiraURL) {
        this.jiraURL = jiraURL;
    }

    public void setJiraUser(String jiraUser) {
        this.jiraUser = jiraUser;
    }

    public void setJiraPassword(String jiraPassword) {
        this.jiraPassword = jiraPassword;
    }
}
