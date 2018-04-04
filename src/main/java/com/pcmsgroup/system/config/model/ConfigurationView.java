package com.pcmsgroup.system.config.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

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

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.pcmsgroup.system.config.VBSConfigurationClient;

@Controller("treeConfigView")
public class ConfigurationView implements Serializable
{
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private TreeNode root, selectedNode;
  private DBCollection prefixes, servers, clusters, nodes, applications;
  @Autowired(required = true)
  public VBSConfigurationClient configClient;
  @Autowired(required = true)
  private ComparisonView comparisonView;
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
    applications = configClient.getApplications();
  }

  private void getServers()
  {
    servers = configClient.getServers();
  }

  private void getClusters()
  {
    clusters = configClient.getClusters();
  }

  private void getNodes()
  {
    nodes = configClient.getNodes();
  }

  private void getPrefixes()
  {
    prefixes = configClient.getPrefixes();
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
    comparisonView.setConfigDoc(configDoc);
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
}
