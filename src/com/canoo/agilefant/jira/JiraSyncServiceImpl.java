package com.canoo.agilefant.jira;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
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
import fi.hut.soberit.agilefant.model.Iteration;
import fi.hut.soberit.agilefant.model.Product;
import fi.hut.soberit.agilefant.model.Project;

@Service("jiraSyncService")
public class JiraSyncServiceImpl {
    private static final Logger LOG = Logger.getLogger(JiraSyncServiceImpl.class.getName());
    
    @Autowired
    private ProductBusiness productBusiness;

    @Autowired
    private ProjectBusiness projectBusiness;
    
    @Autowired
    private IterationBusiness iterationBusiness;
    
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
        JiraSoapService jiraService = getJiraService(jiraURL);
        String token = jiraService.login(jiraUser, jiraPassword);

        RemoteProject jiraProject = jiraService.getProjectByKey(token, jProjectKey);
        
        Project project = findAgilefantProject(jiraProject.getName());
        if (project == null) {
            project = createAgilefantProject(jiraProject, aProductName);
        }
        
        RemoteVersion[] versions = jiraService.getVersions(token, jiraProject.getKey());
        for (RemoteVersion version : versions) {
            Iteration iteration = findIteration(version.getName());
            if (iteration == null) {
                iteration = createIteration(version, project);
                LOG.info(String.format("Iteration %s created. (%d)", iteration.getName(), iteration.getId()));
            }
        }
        
        String[] projectKeys = {jProjectKey};
//        RemoteIssue[] issues = jiraService.getIssuesFromTextSearchWithProject(token, projectKeys, "", 4000);
//        LOG.info(String.format("%d issues returned.", issues.length));
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

    private Iteration findIteration(String name) {
        Collection<Iteration> iterations = iterationBusiness.retrieveAll();
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
