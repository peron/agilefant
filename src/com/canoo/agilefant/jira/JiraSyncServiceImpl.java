package com.canoo.agilefant.jira;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.atlassian.jira.rpc.soap.beans.RemoteProject;
import com.canoo.jira.soap.client.JiraSoapService;
import com.canoo.jira.soap.client.JiraSoapServiceServiceLocator;

import fi.hut.soberit.agilefant.business.ProductBusiness;
import fi.hut.soberit.agilefant.model.Product;

@Service("jiraSyncService")
public class JiraSyncServiceImpl {
    private static final Logger LOG = Logger.getLogger(JiraSyncServiceImpl.class.getName());
    
    @Autowired
    private ProductBusiness productBusiness;

    public void getAllJiraProducts() {
        LOG.info("Adding JIRA projects.");
        List<Product> products = new ArrayList<Product>(productBusiness.retrieveAll());
        try {
            JiraSoapServiceServiceLocator jiraSoapServiceServiceLocator = new JiraSoapServiceServiceLocator();
            JiraSoapService jiraSoapService = jiraSoapServiceServiceLocator.getJirasoapserviceV2(new URL("http://10.0.1.2:8080/jira/rpc/soap/jirasoapservice-v2"));
            
            String token = jiraSoapService.login("agilefant", "pariolo");
            RemoteProject[] projects = jiraSoapService.getProjectsNoSchemes(token);
            jiraSoapService.logout(token);
            
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
    
    public void synchronizeProject(String aProductName, String aProjectName, String jProjectName, String jiraURL, String jiraUser, String jiraPassword) {
        
    }
    
    public static void main(String[] args) {
        JiraSyncServiceImpl service = new JiraSyncServiceImpl();
        
    }
}
