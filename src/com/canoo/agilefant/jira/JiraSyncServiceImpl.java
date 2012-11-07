package com.canoo.agilefant.jira;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.rpc.ServiceException;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rpc.exception.RemoteAuthenticationException;
import com.atlassian.jira.rpc.exception.RemotePermissionException;
import com.atlassian.jira.rpc.soap.beans.RemoteIssue;
import com.atlassian.jira.rpc.soap.beans.RemoteProject;
import com.atlassian.jira.rpc.soap.beans.RemoteVersion;
import com.canoo.jira.soap.client.JiraSoapService;
import com.canoo.jira.soap.client.JiraSoapServiceServiceLocator;

import fi.hut.soberit.agilefant.business.IterationBusiness;
import fi.hut.soberit.agilefant.business.ProductBusiness;
import fi.hut.soberit.agilefant.business.ProjectBusiness;
import fi.hut.soberit.agilefant.business.StoryBusiness;
import fi.hut.soberit.agilefant.model.Backlog;
import fi.hut.soberit.agilefant.model.Iteration;
import fi.hut.soberit.agilefant.model.Product;
import fi.hut.soberit.agilefant.model.Project;
import fi.hut.soberit.agilefant.model.Story;
import fi.hut.soberit.agilefant.util.Pair;

@Service("jiraSyncService")
public class JiraSyncServiceImpl {
    private static final Logger LOG = Logger.getLogger(JiraSyncServiceImpl.class.getName());
    
    private enum IssueType {
        BUG("Bug"),
        FEATURE("Feature"),
        FETURE_REQUEST("Feature Request"),
        IMPROVEMENT("Improvement"),
        QUESTION("Question"),
        STORY("Story"),
        SUPPORT_ACCOUNT("Support Account"),
        SUPPORT_INCIDENT("Support Incident"),
        TASK("Task"),
        SUB_TASK("Sub-task");
        
        private String typeName;

        private IssueType(String typeName) {
            this.typeName = typeName;
        }
        
        public static IssueType fromTypeName(String typeName) {
            for (IssueType issueType : IssueType.values()) {
                if (issueType.typeName.equals(typeName)) {
                    return issueType;
                }
            }
            return null;
        }
    }
    
    @Autowired
    private ProductBusiness productBusiness;

    @Autowired
    private ProjectBusiness projectBusiness;
    
    @Autowired
    private IterationBusiness iterationBusiness;
    
    @Autowired
    private StoryBusiness storyBusiness;
    
    public void getAllJiraProducts() {
        LOG.info("Adding JIRA projects.");
        List<Product> products = new ArrayList<Product>(productBusiness.retrieveAll());
        try {
            RemoteProject[] projects = getAllJiraProjects();
            
            for (RemoteProject project : projects) {
                LOG.info(String.format("[TestServiceMethods.getJiraProjects] Project: %s, Name: %s, Lead: %s, Description: %s",
                        project.getKey(), project.getName(), project.getLead(), project.getDescription()));

                boolean bFound = false;
                for (Product product : products) {
                    if (product.getName().equals(project.getName())) {
                        LOG.info(String.format("Project <%s> is already in Agilefant.", project.getName()));
                        bFound = true;
                        break;
                    }
                }
                
                if (!bFound) {
                    Product product = new Product();
                    product.setName(project.getName());
                    product.setDescription(project.getDescription());
                    LOG.info("New product instance: " + product.getName());
                    
                    int prodId = productBusiness.create(product);
                    LOG.info("New product created: " + product.getId());
                    products.add(productBusiness.retrieve(prodId));
                    LOG.info("Product added to menu: " + products.size());
                }
            }
            
        } catch (Exception e) {
            LOG.severe("Rock bottom: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
    
    private RemoteProject[] getAllJiraProjects() throws ServiceException,
            MalformedURLException, RemoteException,
            RemoteAuthenticationException,
            com.atlassian.jira.rpc.exception.RemoteException,
            RemotePermissionException {
        JiraSoapService jiraSoapService = getJiraService("http://10.0.1.2:8080/jira/rpc/soap/jirasoapservice-v2");
        String token = jiraSoapService.login("agilefant", "pariolo");

        RemoteProject[] projects = jiraSoapService.getProjectsNoSchemes(token);
        
        jiraSoapService.logout(token);
        
        return projects;
    }

    private JiraSoapService getJiraService(String url) throws ServiceException,
            MalformedURLException {
        JiraSoapServiceServiceLocator jiraSoapServiceServiceLocator = new JiraSoapServiceServiceLocator();
        JiraSoapService jiraSoapService = jiraSoapServiceServiceLocator.getJirasoapserviceV2(new URL(url));
        return jiraSoapService;
    }
    
    public void synchronizeProject(String aProductName, String aProjectName, String jProjectKey, String jiraURL, String jiraUser, String jiraPassword) throws MalformedURLException, ServiceException, RemoteAuthenticationException, com.atlassian.jira.rpc.exception.RemoteException, RemoteException {
        Map<String, Pair<Iteration, List<Story>>> versionStories = new HashMap<String, Pair<Iteration, List<Story>>>();
        JiraSoapService jiraService = getJiraService(jiraURL);
        String token = jiraService.login(jiraUser, jiraPassword);

        // Sync JIRA project -> Agilefant project
        RemoteProject jiraProject = jiraService.getProjectByKey(token, jProjectKey);
        Project project = findAgilefantProject(jiraProject.getName());
        if (project == null) {
            project = createAgilefantProject(jiraProject, aProductName);
        }
        versionStories.put(null, new Pair<Iteration, List<Story>>(null, getProjectStories(project)));
        
        // Sync JIRA versions -> Agilefant iterations
        RemoteVersion[] versions = jiraService.getVersions(token, jiraProject.getKey());
        Collection<Iteration> iterationsForProject = findIterationsForProject(project);
        for (RemoteVersion version : versions) {
            Iteration iteration = findIteration(version.getName(), iterationsForProject);
            if (iteration == null) {
                iteration = createIteration(version, project);
                LOG.info(String.format("Iteration %s created. (%d)", iteration.getName(), iteration.getId()));
                List<Story> emptyStoryList = Collections.emptyList();
                versionStories.put(iteration.getName(), new Pair<Iteration, List<Story>>(iteration, emptyStoryList));
            }
            else {
                versionStories.put(iteration.getName(), new Pair<Iteration, List<Story>>(iteration,storyBusiness.retrieveStoriesInIteration(iteration)));
            }
        }
        
        // Sync JIRA issues -> Agilefant stories or tasks, depending on the issue type
        String[] projectKeys = {jProjectKey};
        RemoteIssue[] issues = jiraService.getIssuesFromTextSearchWithProject(token, projectKeys, "", 4000);
//        RemoteIssue[] issues = {};
        LOG.info(String.format("%d issues returned.", issues.length));
        for (RemoteIssue issue : issues) {
            String type = issue.getType();
            
            IssueType issueType = IssueType.fromTypeName(type);
            switch (issueType) {
            case BUG:
            case TASK:
            case SUB_TASK:
                updateOrCreateTask(issue, project, versionStories);
                break;
            case STORY:
            case IMPROVEMENT:
            case FEATURE:
            case FETURE_REQUEST:
                updateOrCreateStory(issue, project, versionStories);
                break;
            case SUPPORT_ACCOUNT:
            case SUPPORT_INCIDENT:
            case QUESTION:
                // Don't add these to Agilefant.
                break;
            default:
                LOG.severe(String.format("JIRA issue type '%s' not covered by the JIRA sync service.", issueType.typeName));
                break;
            }
        }
    }

    private List<Story> getProjectStories(Project project) {
        List<Story> projectStories = new ArrayList<Story>();
        
        Collection<Story> allStories = storyBusiness.retrieveAll();
        for (Story story : allStories) {
            if (story.getBacklog().getId() == project.getId()) {
                if (story.getIteration() == null) {
                    projectStories.add(story);
                }
            }
        }
        
        return projectStories;
    }

    private void updateOrCreateTask(RemoteIssue issue, Project project,
            Map<String, Pair<Iteration, List<Story>>> versionStories) {
        // TODO Auto-generated method stub
        
    }

    private void updateOrCreateStory(RemoteIssue issue, Project project, Map<String, Pair<Iteration, List<Story>>> versionStories) {
        RemoteVersion[] fixVersions = issue.getFixVersions();

        if ((fixVersions == null) || (fixVersions.length == 0)) {
            Story story = getCachedStory(versionStories, null, issue);
            // TODO per Nov 3, 2012: Implement updating.
            return;
        }

        Arrays.sort(fixVersions, new Comparator<RemoteVersion>() {

            @Override
            public int compare(RemoteVersion o1, RemoteVersion o2) {
                return o2.getSequence().compareTo(o1.getSequence());
            }
        });
        
        RemoteVersion remoteVersion = fixVersions[0];
        
        
//        Story story = versionStories.get(issue.getSummary());
//        story = findStory(issue);
    }

    private Story getCachedStory(Map<String, Pair<Iteration, List<Story>>> versionStories,
            String versionName, RemoteIssue issue) {
        
        // First try to get the story from our loaded cache.
        List<Story> stories = versionStories.get(versionName).getSecond();
        for (Story story : stories) {
            if (story.getName().equals(issue.getSummary())) {
                return story;
            }
        }
        
        // If not in cache, it's a new story.
        Story story = new Story();
        story.setIteration(versionStories.get(versionName).getFirst());
        story.setDescription(issue.getDescription());
        story.setName(issue.getSummary());
        
        // Persist the new story, both in Agilefant DB and our cache.
        int id = storyBusiness.create(story);
        story = storyBusiness.retrieve(id);
        stories.add(story);

        return story;
    }

    private Story findStory(RemoteIssue issue) {
        // TODO Auto-generated method stub
        return null;
    }

    private Collection<Iteration> findIterationsForProject(Project project) {
        Collection<Iteration> allIterations = iterationBusiness.retrieveAll();
        List<Iteration> projectIterations = new ArrayList<Iteration>();
        
        for (Iteration iteration : allIterations) {
            Backlog iterationProject = iteration.getParent();
            if (iterationProject.getId() != project.getId()) {
                break;
            }
            projectIterations.add(iteration);
        }
        return projectIterations;
    }

    private Iteration createIteration(RemoteVersion version, Project project) {
        LOG.info(String.format("Creating iteration '%s' in project '%s'.", version.getName(), project.getName()));
        Iteration iteration = new Iteration();
        
        iteration.setName(version.getName());
        Calendar releaseDate = version.getReleaseDate();
        DateTime endDate;
        if (releaseDate != null) {
            endDate = new DateTime(releaseDate.getTimeInMillis());
        }
        else {
            DateTime today = new DateTime();
            endDate = today.plusDays(90);
        }
        iteration.setStartDate(endDate.minusDays(90));  // TODO: Find better way to set start date.
        iteration.setEndDate(endDate);
        iteration.setParent(project);
        
        int iterationId = iterationBusiness.create(iteration);
        return iterationBusiness.retrieve(iterationId);
    }

    private Iteration findIteration(String name, Collection<Iteration> iterations) {
        for (Iteration iteration : iterations) {
            if (iteration.getName().equals(name)) {
                LOG.info(String.format("Iteration '%s' found.", iteration.getName()));
                return iteration;
            }
        }
        
        LOG.info(String.format("Iteration '%s' could not be found.", name));
        return null;
    }

    private Project createAgilefantProject(RemoteProject jiraProject, String productName) {
        LOG.info(String.format("Creating project '%s' in product '%s'.", jiraProject.getName(), productName));
        Project project = new Project();
        project.setDescription(jiraProject.getDescription());
        project.setName(jiraProject.getName());

        Product product = findAgilefantProduct(productName);
        project.setParent(product);
        
        int projectId = projectBusiness.create(project);
        
        LOG.info(String.format("Project '%s' created. (Project ID: %d, Returned ID: %d)", project.getName(), project.getId(), projectId));
        
        return projectBusiness.retrieve(projectId);
    }

    private Product findAgilefantProduct(String productName) {
        Collection<Product> products = productBusiness.retrieveAll();
        for (Product product : products) {
            if (product.getName().equals(productName)) {
                LOG.info(String.format("Product '%s' found.", product.getName()));
                return product;
            }
        }

        LOG.warning(String.format("Product '%s' could not be found.", productName));
        return null;
    }

    private Project findAgilefantProject(String projectName) {
        Collection<Project> projects = projectBusiness.retrieveAll();
        for (Project project : projects) {
            if (project.getName().equals(projectName)) {
                LOG.info(String.format("Project '%s' found.", project.getName()));
                return project;
            }
        }
        LOG.warning(String.format("Project '%s' could not be found.", projectName));
        return null;
    }
    
    public static void main(String[] args) {
//        JiraSyncServiceImpl service = new JiraSyncServiceImpl();
        
    }
}
