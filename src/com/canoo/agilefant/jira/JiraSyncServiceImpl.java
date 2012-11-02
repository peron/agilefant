package com.canoo.agilefant.jira;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.rpc.ServiceException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rpc.exception.RemoteAuthenticationException;
import com.atlassian.jira.rpc.exception.RemotePermissionException;
import com.atlassian.jira.rpc.soap.beans.RemoteProject;
import com.canoo.jira.soap.client.JiraSoapService;
import com.canoo.jira.soap.client.JiraSoapServiceServiceLocator;

import fi.hut.soberit.agilefant.business.ProductBusiness;
import fi.hut.soberit.agilefant.business.ProjectBusiness;
import fi.hut.soberit.agilefant.model.Product;
import fi.hut.soberit.agilefant.model.Project;

@Service("jiraSyncService")
public class JiraSyncServiceImpl {
    private static final Logger LOG = Logger.getLogger(JiraSyncServiceImpl.class.getName());
    
    @Autowired
    private ProductBusiness productBusiness;

    @Autowired
    private ProjectBusiness projectBusiness;
    
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
        
        Project project = findAgilefantProject(jiraProject);
        if (project == null) {
            project = createAgilefantProject(jiraProject, aProductName);
        }
        
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

    private Project findAgilefantProject(RemoteProject jiraProject) {
        Collection<Project> projects = projectBusiness.retrieveAll();
        for (Project project : projects) {
            if (project.getName().equals(jiraProject.getName())) {
                LOG.info(String.format("Project '%s' found.", project.getName()));
                return project;
            }
        }
        LOG.warning(String.format("Project '%s' could not be found.", jiraProject.getName()));
        return null;
    }
    
    public static void main(String[] args) {
        JiraSyncServiceImpl service = new JiraSyncServiceImpl();
        
    }
}
