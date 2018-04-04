package com.pcmsgroup.system.config.model;

import java.io.Serializable;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

import org.primefaces.event.NodeCollapseEvent;
import org.primefaces.event.NodeExpandEvent;
import org.primefaces.event.NodeSelectEvent;
import org.primefaces.event.NodeUnselectEvent;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.ibm.websphere.management.exception.ConnectorException;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.pcmsgroup.system.config.RunningProfileClient;

@Controller("treeProfileView")
public class ProfileView implements Serializable
{
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private TreeNode root, selectedNode;
  private DBCollection prefixes, nodes, clusters, servers, applications;
  @Autowired
  private RunningProfileClient profileClient;
  private String header, URL = "172.17.0.136:10003";

  @PostConstruct
  public TreeNode getRoot()
  {
    return root;
  }

  @PostConstruct
  public void init()
  {
    header = "Deployment Manager SOAP URL: " + profileClient.getURL();
    root = new DefaultTreeNode("Root", null);

    getPrefixes();
    if (prefixes != null && prefixes.count() > 0)
    {
      TreeNode node = new DefaultTreeNode("prefixes", root);
      for (DBObject prefix : prefixes.find())
      {
        TreeNode prefixNode = new DefaultTreeNode(prefix.get("name"), node);
      }
    }

    getNodes();
    if (nodes != null && nodes.count() > 0)
    {
      TreeNode parentTreeNode = new DefaultTreeNode("nodes", root);
      for (DBObject node : nodes.find())
      {
        TreeNode childTreeNode = new DefaultTreeNode(node.get("name"), parentTreeNode);
      }
    }

    getClusters();
    if (clusters != null && clusters.count() > 0)
    {
      TreeNode clusterParent = new DefaultTreeNode("clusters", root);
      for (DBObject cluster : clusters.find())
      {
        TreeNode clusterChild = new DefaultTreeNode(cluster.get("name"), clusterParent);
      }
    }

    getServers();
    if (servers != null && servers.count() > 0)
    {
      TreeNode serverParent = new DefaultTreeNode("servers", root);
      for (DBObject server : servers.find())
      {
        TreeNode serverChild = new DefaultTreeNode(server.get("name"), serverParent);
      }
    }

    getApplications();
    if (applications != null && applications.count() > 0)
    {
      TreeNode applicationParent = new DefaultTreeNode("applications", root);
      for (DBObject application : applications.find())
      {
        TreeNode applicationChild = new DefaultTreeNode(application.get("name"), applicationParent);
      }
    }

  }

  public void open(ActionEvent event)
  {
    try
    {
      refreshProfileDatabase();
      init();
      FacesMessage message = new FacesMessage("Successful", URL + " accessed and configuration tree has been refreshed.");
      FacesContext.getCurrentInstance().addMessage(null, message);
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
      FacesMessage message = new FacesMessage("Unsuccessful", URL + " could not be accessed profile tree has NOT been refreshed.");
      FacesContext.getCurrentInstance().addMessage(null, message);
    }
  }

  private void refreshProfileDatabase() throws ConnectorException
  {
    profileClient.pullFromRunningProfile();
  }

  private void getApplications()
  {
    applications = profileClient.getApplications();
  }

  private void getServers()
  {
    servers = profileClient.getServers();
  }

  private void getClusters()
  {
    clusters = profileClient.getClusters();
  }

  private void getNodes()
  {
    nodes = profileClient.getNodes();
  }

  private void getPrefixes()
  {
    prefixes = profileClient.getPrefixes();
  }

  public String getHeader()
  {
    return header;
  }

  public void setHeader(String header)
  {
    this.header = header;
  }

  public String getURL()
  {
    return URL;
  }

  public void setURL(String uRL)
  {
    URL = uRL;
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
}
