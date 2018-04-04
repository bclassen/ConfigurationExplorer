package com.pcmsgroup.system.config.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.NodeCollapseEvent;
import org.primefaces.event.NodeExpandEvent;
import org.primefaces.event.NodeSelectEvent;
import org.primefaces.event.NodeUnselectEvent;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.pcmsgroup.system.config.RunningProfileClient;
import com.pcmsgroup.system.config.VBSConfigurationClient;

@Controller("ttBasicView")
public class ComparisonView implements Serializable
{
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private TreeNode root, selectedNode;
  private DBCollection configPrefixes, profilePrefixes, configServers, profileServers, configClusters, profileClusters, configNodes, profileNodes, configApplications, profileApplications;
  @Autowired(required = true)
  public VBSConfigurationClient configClient;
  @Autowired(required = true)
  public RunningProfileClient profileClient;
  private DBObject configDoc, profileDoc;
  private String header, selectedNodeText;

  public TreeNode getRoot()
  {
    return root;
  }

  @PostConstruct
  public void init()
  {

    setHeader(configClient.getConfigFile().getFilename());
    root = new DefaultTreeNode("Root", null);

    if (configDoc != null)
    {
      profileDoc = getCorrespondingProfileDoc(configDoc);
      DBCollection propertyTypeCollection = configClient.getPropertyTypeCollection((String) configDoc.get("type"));

      //First Level
      Map firstLevelProfileMap = profileDoc.toMap();
      Map firstLevelConfigMap = configDoc.toMap();
      for (Object firstLevelKey : firstLevelConfigMap.keySet())
      {
        String nameToMatch = ((String)firstLevelKey).replaceAll("[\\d.]", "");
        BasicDBObject result = (BasicDBObject) propertyTypeCollection.findOne(new BasicDBObject("name", nameToMatch));
        if (result != null && result.getString("comparable").equals("true"))
        {
          Object firstLevelConfigValue = firstLevelConfigMap.get(firstLevelKey);
          Object firstLevelProfileValue = firstLevelProfileMap.get(firstLevelKey);
          ComparisonDocument firstLevelDoc = new ComparisonDocument();
          DefaultTreeNode firstLevelNode;
          if (firstLevelConfigValue instanceof Map)
          {
            firstLevelDoc.setName(firstLevelKey);
            firstLevelNode = new DefaultTreeNode("property",firstLevelDoc, root);
            //Second Level
            Map secondLevelConfigMap = (Map) firstLevelConfigValue;
            Map secondLevelProfileMap = (Map) firstLevelProfileValue;
            for (Object secondLevelKey : secondLevelConfigMap.keySet())
            {
              Object secondLevelConfigValue = secondLevelConfigMap.get(secondLevelKey);
              Object secondLevelProfileValue = null;
              if (secondLevelProfileMap != null)
                secondLevelProfileValue = secondLevelProfileMap.get(secondLevelKey);
              ComparisonDocument secondLevelDoc = new ComparisonDocument();
              DefaultTreeNode secondLevelNode = null;
              if (secondLevelConfigValue instanceof Map)
              {
                //Third Level
                Map thirdLevelConfigMap = (Map) secondLevelConfigValue;
                Map thirdLevelProfileMap = null;
                if (secondLevelProfileValue != null){
                  thirdLevelProfileMap = (Map) secondLevelProfileValue;
                  secondLevelDoc.setProfileValue(thirdLevelProfileMap.get("value"));
                }
                secondLevelDoc.setName(secondLevelKey);
                secondLevelDoc.setConfigValue(thirdLevelConfigMap.get("value"));
                secondLevelNode = new DefaultTreeNode("property",secondLevelDoc, firstLevelNode);
                for (Object thirdLevelKey : thirdLevelConfigMap.keySet())
                {
                  Object thirdLevelConfigValue = thirdLevelConfigMap.get(thirdLevelKey);
                  Object thirdLevelProfileValue = null;
                  if (thirdLevelProfileMap != null)
                    thirdLevelProfileValue = thirdLevelProfileMap.get(thirdLevelKey);
                  ComparisonDocument thirdLevelDoc = new ComparisonDocument();
                  if (thirdLevelConfigValue==null)//check if value is an id reference; how?
                  {
                    //create a link to the config doc
                    //create a link to the corresponding profile doc
                    //add them to the tree
                  }
                  else
                  {
                    thirdLevelDoc.setName(thirdLevelKey);
                    thirdLevelDoc.setConfigValue(thirdLevelConfigValue);
                    thirdLevelDoc.setProfileValue(thirdLevelProfileValue);
                    DefaultTreeNode thirdLevelNode = new DefaultTreeNode("property",thirdLevelDoc, secondLevelNode);
                  }
                }
              }
              else if (secondLevelConfigValue==null)//check if value is an id reference; how?
              {
                //create a link to the config doc
                //create a link to the corresponding profile doc
                //add them to the tree
              }
              else
              {
                secondLevelDoc.setName(secondLevelKey);
                secondLevelDoc.setConfigValue(secondLevelConfigValue);
                secondLevelDoc.setProfileValue(secondLevelProfileValue);
                secondLevelNode = new DefaultTreeNode("property",secondLevelDoc, firstLevelNode);
              }
            }
          }
          else if (result.getString("keyPrefix") != null)//check if value is an id reference; how?
          {
            String keyPrefix = result.getString("keyPrefix");
            String collection = result.getString("collection");
            //create a link to the config doc
            //create a link to the corresponding profile doc
            //add them to the tree
            firstLevelDoc.setName(firstLevelKey);
            firstLevelDoc.setConfigValue(firstLevelConfigValue);
            firstLevelDoc.setProfileValue(firstLevelProfileValue);
            firstLevelNode = new DefaultTreeNode("link",firstLevelDoc, root);
          }
          else
          {
            firstLevelDoc.setName(firstLevelKey);
            firstLevelDoc.setConfigValue(firstLevelConfigValue);
            firstLevelDoc.setProfileValue(firstLevelProfileValue);
            firstLevelNode = new DefaultTreeNode("property",firstLevelDoc, root);
          }
        }
      }
    }
  }

  private DBObject getCorrespondingProfileDoc(DBObject configDoc2)
  {
    String name = (String) configDoc2.get("name");
    String type = (String) configDoc2.get("type");
    return profileClient.getProfileDocByNameAndType(name, type);
  }

  public void open(FileUploadEvent event)
  {
    try
    {
      InputStream in = event.getFile().getInputstream();
      refreshConfigurationDatabase(in);
      configClient.setConfigFile(new InputStreamResource(in));
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    init();
    FacesMessage message = new FacesMessage("Successful", event.getFile().getFileName() + " is uploaded, and configuration tree has been refreshed.");
    FacesContext.getCurrentInstance().addMessage(null, message);
  }

  private void refreshConfigurationDatabase(InputStream in) throws IOException
  {
    Resource file = new InputStreamResource(in);

    configClient.readConfigDotProperties(file);

    configClient.clearConfigAndPopulateFromFile();
  }

  private void getApplications()
  {
    configApplications = configClient.getApplications();
  }

  private void getServers()
  {
    configServers = configClient.getServers();
  }

  private void getClusters()
  {
    configClusters = configClient.getClusters();
  }

  private void getNodes()
  {
    configNodes = configClient.getNodes();
  }

  private void getPrefixes()
  {
    configPrefixes = configClient.getPrefixes();
  }

  public String getHeader()
  {
    return header;
  }

  public void setHeader(String header)
  {
    this.header = header;
  }

  public void onNodeExpand(NodeExpandEvent event)
  {
    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Expanded", event.getTreeNode().toString());
    FacesContext.getCurrentInstance().addMessage(null, message);
  }

  public void onNodeCollapse(NodeCollapseEvent event)
  {
    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Collapsed", event.getTreeNode().toString());
    FacesContext.getCurrentInstance().addMessage(null, message);
  }

  public void onNodeSelect(NodeSelectEvent event)
  {
    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Selected", event.getTreeNode().toString());
    FacesContext.getCurrentInstance().addMessage(null, message);

    DBObject configDoc = configClient.getConfigDocument(event.getTreeNode().toString(), event.getTreeNode().getParent().toString());
    setSelectedNodeText(configDoc.toString());
  }

  public void onNodeUnselect(NodeUnselectEvent event)
  {
    FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Unselected", event.getTreeNode().toString());
    FacesContext.getCurrentInstance().addMessage(null, message);
  }

  public TreeNode getSelectedNode()
  {
    return selectedNode;
  }

  public void setSelectedNode(TreeNode selectedNode)
  {
    this.selectedNode = selectedNode;
  }

  public String getSelectedNodeText()
  {
    return selectedNodeText;
  }

  public void setSelectedNodeText(String selectedNodeText)
  {
    this.selectedNodeText = selectedNodeText;
  }

  public VBSConfigurationClient getConfigClient()
  {
    return configClient;
  }

  public void setConfigClient(VBSConfigurationClient configClient)
  {
    this.configClient = configClient;
  }

  public DBCollection getProfilePrefixes()
  {
    return profilePrefixes;
  }

  public void setProfilePrefixes(DBCollection profilePrefixes)
  {
    this.profilePrefixes = profilePrefixes;
  }

  public DBCollection getProfileServers()
  {
    return profileServers;
  }

  public void setProfileServers(DBCollection profileServers)
  {
    this.profileServers = profileServers;
  }

  public DBCollection getProfileClusters()
  {
    return profileClusters;
  }

  public void setProfileClusters(DBCollection profileClusters)
  {
    this.profileClusters = profileClusters;
  }

  public DBCollection getProfileNodes()
  {
    return profileNodes;
  }

  public void setProfileNodes(DBCollection profileNodes)
  {
    this.profileNodes = profileNodes;
  }

  public DBCollection getProfileApplications()
  {
    return profileApplications;
  }

  public void setProfileApplications(DBCollection profileApplications)
  {
    this.profileApplications = profileApplications;
  }

  public RunningProfileClient getProfileClient()
  {
    return profileClient;
  }

  public void setProfileClient(RunningProfileClient profileClient)
  {
    this.profileClient = profileClient;
  }

  public DBObject getConfigDoc()
  {
    return configDoc;
  }

  public void setConfigDoc(DBObject configDoc)
  {
    this.configDoc = configDoc;
    this.init();
    RequestContext.getCurrentInstance().update("form:centerStage");
  }

  public DBObject getProfileDoc()
  {
    return profileDoc;
  }

  public void setProfileDoc(DBObject profileDoc)
  {
    this.profileDoc = profileDoc;
  }
}
