/*
 * Copyright (c) PCMS Group plc 2017. All Rights Reserved.
 * This source code is copyright of PCMS Group plc. The information
 * contained herein is proprietary and confidential to PCMS Group plc.
 * This proprietary and confidential information, either in whole or in
 * part, shall not be used for any purpose unless permitted by the terms
 * of a valid license agreement.
 */
package com.pcmsgroup.system.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import org.bson.types.ObjectId;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.Session;
import com.ibm.websphere.management.async.client.AsyncCommandClient;
import com.ibm.websphere.management.async.client.AsyncCommandHandlerIF;
import com.ibm.websphere.management.cmdframework.AdminCommand;
import com.ibm.websphere.management.cmdframework.CommandException;
import com.ibm.websphere.management.cmdframework.CommandMgr;
import com.ibm.websphere.management.cmdframework.CommandMgrInitException;
import com.ibm.websphere.management.cmdframework.CommandNotFoundException;
import com.ibm.websphere.management.cmdframework.CommandResult;
import com.ibm.websphere.management.cmdframework.DownloadFile;
import com.ibm.websphere.management.cmdframework.provider.CommandNotification;
import com.ibm.websphere.management.configservice.ConfigServiceProxy;
import com.ibm.websphere.management.exception.AdminException;
import com.ibm.websphere.management.exception.ConfigServiceException;
import com.ibm.websphere.management.exception.ConnectorException;
import com.ibm.websphere.management.wlm.ClusterMemberData;
import com.ibm.ws.scripting.AbstractShell;
import com.ibm.ws.scripting.AdminAppClient;
import com.ibm.ws.scripting.WasxShell;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
 * @author Administrator
 *
 */
@Component
public class RunningProfileClient extends ManagementClient implements NotificationListener
{
  private CommandMgr cmdMgr;
  static final String DMGR_PORT = "10003";
  //private static final String DMGR_HOST = "DLA-AWUDCPND10.walgreens.com";
  static final String DMGR_HOST = "172.17.0.136";
  private AdminClient adminClient;
  private Session session;
  private ConfigServiceProxy cfgService;
  private ObjectName[] nodeAgents;
  private long ntfyCount = 0;
  private String URL = DMGR_HOST + ":" + DMGR_PORT;
  @Autowired
  protected MongoClient mongoClient;
  protected MongoTemplate mongoProfileTemplate;

  public class AsyncCmdHandler implements AsyncCommandHandlerIF
  {

    public void handleNotification(CommandNotification commandnotification)
    {
      System.out.println("Notification received: " + commandnotification);
    }

  }

  public RunningProfileClient()
  {
  }

  @PostConstruct
  public void init() throws ConnectorException
  {
    createAdminClient(this.URL);
    createCommandMgr();
    mongoConnect("vbs_profile");
  }

  public RunningProfileClient(String URL) throws ConnectorException
  {
    this.URL = URL;
    init();
  }

  protected void mongoConnect(String databaseName)
  {
    mongoProfileTemplate = new MongoTemplate(mongoClient, databaseName);
    database = mongoProfileTemplate.getDb();
    System.out.println("Connected to mongo...");
  }

  public void pullFromRunningProfile()
  {
    database.dropDatabase();

    try
    {
      prepopulatePrefixTypes();
      prepopulateApplicationPropertyTypes();
      prepopulateClusterPropertyTypes();
      prepopulateNodePropertyTypes();
      prepopulatePrefixPropertyTypes();
      prepopulateServerPropertyTypes();
    }
    catch (IOException e)
    {
      e.printStackTrace();
      System.exit(1);
    }

    pullClusters();

    pullServers();

    pullApplications();

    populatePrefixCollection();

    //listAllCommand();
  }

  private void pullApplications()
  {
    ObjectName serverQueryName;

    Set<?> applications = null;
    try
    {
      serverQueryName = new ObjectName("WebSphere:*,type=Application");
      applications = adminClient.queryNames(serverQueryName, null);

      populateApplicationCollection(applications);
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (MalformedObjectNameException e)
    {
      e.printStackTrace();
    }
  }

  private void pullServers()
  {
    ObjectName serverQueryName;

    Set<?> servers = null;
    try
    {
      serverQueryName = new ObjectName("WebSphere:*,type=Server,processType=ManagedProcess");
      servers = adminClient.queryNames(serverQueryName, null);

      populateNodeAndServerCollections(servers);
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (MalformedObjectNameException e)
    {
      e.printStackTrace();
    }
  }

  private void pullServersUsingAPI()
  {
    AdminCommand cmd;
    try
    {
      cmd = cmdMgr.createCommand("listServers");
      cmd.setConfigSession(session);
      //cmd.setParameter("nodeName", "VBSTEST_NODE01");
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, null);
      //asyncCmdClientHelper.processCommandParameters(cmd);
      asyncCmdClientHelper.execute(cmd);

      CommandResult result = cmd.getCommandResult();
      if (result.isSuccessful())
      {
        System.out.println("Successfully executed the command");
        System.out.println("Result: ");
        Object resultData = result.getResult();
        if (resultData instanceof Object[])
        {
          Object[] resDataArr = (Object[]) resultData;
          for (Object resData : resDataArr)
          {
            System.out.println(resData);
          }
        }
        else if (resultData instanceof Set<?>)
        {
          populateNodeAndServerCollections((Set<ObjectName>) resultData);
        }
        else
        {
          System.out.println(resultData);
        }
      }
      else
      {
        System.out.println("Failed to execute the command");
        result.getException().printStackTrace();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }
  }

  private void pullClusters()
  {
    DBCollection clusterCollection = database.getCollection("cluster");
    ObjectName clusterQueryName;

    Set<?> clusters = null;
    try
    {
      clusterQueryName = new ObjectName("WebSphere:*,type=Cluster");
      clusters = adminClient.queryNames(clusterQueryName, null);

      for (Object objCluster : clusters)
      { // We iterate through the set of clusters
        if (objCluster instanceof ObjectName)
        {
          BasicDBObject clusterDoc = new BasicDBObject();
          clusterDoc.append("name", ((ObjectName) objCluster).getKeyProperty("name"));
          clusterDoc.append("type", "cluster");
          clusterCollection.insert(clusterDoc);

          ClusterMemberData[] obj = (ClusterMemberData[]) adminClient.invoke((ObjectName) objCluster, "getClusterMembers", null, null);
          populateNodeAndServerCollections(obj);
        }
      }
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (MalformedObjectNameException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (InstanceNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (MBeanException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ReflectionException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  private void populatePrefixCollection()
  {
    //Loop through clusters and create prefixes
    DBCollection clusterCollection = database.getCollection("cluster");
    for (DBObject nextCluster : clusterCollection.find())
    {
      String clusterPrefixPrefix = (String) nextCluster.get("name");
      int clusterSuffixIndex = clusterPrefixPrefix.indexOf("_CLUSTER");
      if (clusterSuffixIndex > -1)
        clusterPrefixPrefix = clusterPrefixPrefix.substring(0, clusterSuffixIndex);

      createNewPrefix(clusterPrefixPrefix);
    }

    //Loop through servers and create prefixes
    DBCollection serverCollection = database.getCollection("server");
    for (DBObject nextServer : serverCollection.find())
    {
      String serverPrefixPrefix = (String) nextServer.get("name");
      int index = serverPrefixPrefix.indexOf("_APPSRV");
      if (index != -1)
      {
        serverPrefixPrefix = serverPrefixPrefix.substring(0, index);
        createNewPrefix(serverPrefixPrefix);
      }
    }
  }

  private void createNewPrefix(String prefixPrefix)
  {
    DBCollection prefixCollection = database.getCollection("prefix");
    DBCollection prefixTypeCollection = database.getCollection("prefixType");

    BasicDBObject prefixDocument = new BasicDBObject();
    BasicDBObject prefixTypeDocument = (BasicDBObject) prefixTypeCollection.findOne(new BasicDBObject("symbol", prefixPrefix));
    if (prefixTypeDocument != null)
    {
      String typeName = (String) prefixTypeDocument.get("name");

      if (!prefixCollection.find(new BasicDBObject("prefixName", typeName)).hasNext())
      {
        prefixDocument.append("name", typeName);
        prefixDocument.append("type", "prefix");
      }
      else
        return;

      prefixCollection.insert(prefixDocument);
    }
  }

  private void populateApplicationCollection(Set<?> applications)
  {
    String name;
    DBCollection applicationCollection = database.getCollection("application");

    for (Object nextObj : applications)
    {
      if (nextObj instanceof ObjectName)
      {
        name = ((ObjectName) nextObj).getKeyProperty("name");
        if (!applicationCollection.find(new BasicDBObject("name", name)).hasNext() && (serverInList(((ObjectName) nextObj).getKeyProperty("Server"))))
        {
          BasicDBObject applicationDoc = createApplicationDocument((ObjectName) nextObj);
          applicationDoc.append("name", name);
          applicationCollection.insert(applicationDoc);
        }
      }
    }
  }

  private void populateNodeAndServerCollections(Set<?> servers)
  {
    String nodeName, nodeId = "";
    DBCollection nodeCollection = database.getCollection("node");
    DBCollection serverCollection = database.getCollection("server");

    for (Object nextObj : servers)
    {
      if (nextObj instanceof ObjectName)
      {
        if (!serverCollection.find(new BasicDBObject("name", ((ObjectName) nextObj).getKeyProperty("name"))).hasNext())
        {
          nodeName = ((ObjectName) nextObj).getKeyProperty("node");

          BasicDBObject nodeDoc = (BasicDBObject) nodeCollection.findOne(new BasicDBObject("name", nodeName));
          if (nodeDoc == null)
          {
            BasicDBObject newNodeDoc = new BasicDBObject();
            newNodeDoc.append("name", nodeName);
            newNodeDoc.append("type", "node");
            nodeCollection.insert(newNodeDoc);
            nodeId = newNodeDoc.get("_id").toString();
          }
          else
          {
            nodeId = nodeDoc.get("_id").toString();
          }

          BasicDBObject serverDoc = createServerDocument((ObjectName) nextObj);
          serverDoc.append("nodeId", nodeId);
          serverDoc.append("type", "server");
          serverDoc = populateServerDocument(serverDoc);

          serverCollection.insert(serverDoc);
        }
      }
    }
  }

  private void populateNodeAndServerCollections(ClusterMemberData[] obj)
  {
    String nodeName, nodeId = "";
    DBCollection nodeCollection = database.getCollection("node");
    DBCollection serverCollection = database.getCollection("server");

    for (int i = 0; i < obj.length; i++)
    {
      nodeName = obj[i].nodeName;

      BasicDBObject nodeDoc = (BasicDBObject) nodeCollection.findOne(new BasicDBObject("name", nodeName));
      if (nodeDoc == null)
      {
        BasicDBObject newNodeDoc = new BasicDBObject();
        newNodeDoc.append("name", nodeName);
        newNodeDoc.append("type", "node");
        nodeCollection.insert(newNodeDoc);
        nodeId = newNodeDoc.get("_id").toString();
      }
      else
      {
        nodeId = nodeDoc.get("_id").toString();
      }
      BasicDBObject serverDoc = createServerDocument(obj[i]);
      serverDoc.append("nodeId", nodeId);
      serverDoc = populateServerDocument(serverDoc);
      serverCollection.insert(serverDoc);
    }
  }

  private boolean serverInList(String serverName)
  {
    DBCollection serverCollection = database.getCollection("server");
    return serverCollection.find(new BasicDBObject("name", serverName)).hasNext();
  }

  private BasicDBObject createServerDocument(ObjectName objServer)
  {
    BasicDBObject serverDoc = new BasicDBObject();
    serverDoc.append("name", objServer.getKeyProperty("name"));
    serverDoc.append("type", "server");
    return serverDoc;
  }

  private BasicDBObject createServerDocument(ClusterMemberData objServer)
  {
    BasicDBObject serverDoc = new BasicDBObject();
    serverDoc.append("name", objServer.memberName);
    serverDoc.append("type", "server");
    return serverDoc;
  }

  private BasicDBObject createApplicationDocument(ObjectName objApplication)
  {
    String appName = objApplication.getKeyProperty("name");
    BasicDBObject applicationDoc = new BasicDBObject();
    applicationDoc.append("name", appName);
    applicationDoc.append("type", "application");
    /*try
    {
      AbstractShell shell = WasxShell.getShell();
      AdminAppClient client = new AdminAppClient(shell);
      String exportOutput = client.export("BeanstoreServer");
    }
    catch (AdminException | ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }*/
    //WebSphere:service=com.ibm.websphere.application.ApplicationMBean,name=*
/*    ObjectName applicationQueryName = null;
    Set<?> applications = null;
    try
    {
      applicationQueryName = new ObjectName("WebSphere:*,WebModule=*,Application=" + appName);

      applications = adminClient.queryNames(applicationQueryName, null);

    }
    catch (MalformedObjectNameException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
*/
    return applicationDoc;
  }

  private CommandResult extractConfigProperties()
  {
    AdminCommand extractCmd;
    CommandResult extractConfigResult = null;

    try
    {
      extractCmd = cmdMgr.createCommand("extractConfigProperties");
      extractCmd.setParameter("propertiesFileName", new DownloadFile("c:/Temp/extract.properties"));
      //Properties options = new Properties();
      //options.put("GENERATETEMPLATE", "true");
      //extractCmd.setParameter("options", options);

      extractCmd.setConfigSession(session);

      //ObjectName targetObj = getTargetObject(targetObjStr);
      ObjectName targetObj = new ObjectName("WebSphere:Node=VBSTEST_NODE01");
      extractCmd.setTargetObject(targetObj);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.processCommandParameters(extractCmd);
      asyncCmdClientHelper.execute(extractCmd);

      extractConfigResult = extractCmd.getCommandResult();
      if (!extractConfigResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw extractConfigResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return extractConfigResult;
  }

  private BasicDBObject populateServerDocument(BasicDBObject serverDoc)
  {
    DBCollection nodeCollection = database.getCollection("node");
    BasicDBObject nodeDoc = (BasicDBObject) nodeCollection.findOne(new BasicDBObject("_id", new ObjectId(serverDoc.get("nodeId").toString())));
    String nodeName = nodeDoc.get("name").toString();
    String serverName = serverDoc.get("name").toString();

    serverDoc.append("jvmProperties", populateJvmProperties(nodeName, serverName));

    serverDoc.append("qcfs", populateQCFs(nodeName, serverName));

    serverDoc.append("tcfs", populateTCFs(nodeName, serverName));

    serverDoc.append("ases", populateActivationSpecs(nodeName, serverName));

    populateServerPorts(serverDoc, nodeName, serverName);

    return serverDoc;
  }

  private BasicDBObject populateJvmProperties(String nodeName, String serverName)
  {
    BasicDBObject jvmPropertiesDoc = new BasicDBObject("name", "jvmProperties");

    CommandResult result = showJvmProperties(nodeName, serverName, true);

    if (result.isSuccessful())
    {
      Object resultData = result.getResult();
      if (resultData instanceof Object[])
      {
        Object[] resDataArr = (Object[]) resultData;
        for (Object resData : resDataArr)
        {
          System.out.println(resData);
        }
      }
      else
      {
        AttributeList resultList = (AttributeList)resultData;
        for (int i = 0; i < resultList.size(); i++)
        {
          Object nextAttr = resultList.get(i);
          if (nextAttr instanceof Attribute)
          {
            Attribute nextAttribute = (Attribute)nextAttr;
            BasicDBObject jvmProperty = new BasicDBObject("name", nextAttribute.getName()).append("value", nextAttribute.getValue());
            jvmPropertiesDoc.append(convertFromDotToCamel(nextAttribute.getName()), jvmProperty);
          }
        }
      }
    }
    else
    {
      result.getException().printStackTrace();
    }

    result = showJvmProperties(nodeName, serverName, false);

    if (result.isSuccessful())
    {
      Object resultData = result.getResult();
      if (resultData instanceof Object[])
      {
        Object[] resDataArr = (Object[]) resultData;
        for (Object resData : resDataArr)
        {
          System.out.println(resData);
        }
      }
      else
      {
        AttributeList resultList = (AttributeList)resultData;
        for (int i = 0; i < resultList.size(); i++)
        {
          Object nextAttr = resultList.get(i);
          if (nextAttr instanceof Attribute)
          {
            Attribute nextAttribute = (Attribute)nextAttr;
            BasicDBObject jvmProperty = new BasicDBObject("name", nextAttribute.getName()).append("value", nextAttribute.getValue());
            jvmPropertiesDoc.append(convertFromDotToCamel(nextAttribute.getName()), jvmProperty);
          }
        }
      }
    }
    else
    {
      System.out.println("Failed to execute the command");
      result.getException().printStackTrace();
    }
    return jvmPropertiesDoc;
  }

  private BasicDBObject populateQCFs(String nodeName, String serverName)
  {
    CommandResult listFactoryResult;
    CommandResult showFactoryResult;
    BasicDBObject qcfs = new BasicDBObject("name", "qcfs");

    listFactoryResult = listWMQConnectionFactories(nodeName, serverName, "QCF");

    Object listFactoryResultData = listFactoryResult.getResult();
    if (listFactoryResultData instanceof Object[])
    {
      Object[] resDataArr = (Object[]) listFactoryResultData;
      for (Object resData : resDataArr)
      {
        System.out.println(resData);
      }
    }
    else
    {
      ArrayList<ObjectName> factoryList = (ArrayList<ObjectName>)listFactoryResultData;
      for (int i = 0; i < factoryList.size(); i++)
      {
        ObjectName nextConnFactory = (ObjectName)factoryList.get(i);
        BasicDBObject qcf = new BasicDBObject();
        showFactoryResult = showWMQConnectionFactory(nextConnFactory);
        HashMap<String,String> factoryProps = (HashMap<String,String>)showFactoryResult.getResult();
        for (String nextKey : factoryProps.keySet()){
          Object propValue = factoryProps.get(nextKey);
          qcf.append(convertFromDotToCamel(mapProfileNameToConfigName(nextKey,"qcfs",serverPropertyTypeCollection)), propValue);
        }
        qcfs.append(nextConnFactory.getKeyProperty("_Websphere_Config_Data_Display_Name"), qcf);
      }
    }
    return qcfs;
  }

  private BasicDBObject populateTCFs(String nodeName, String serverName)
  {
    CommandResult listFactoryResult;
    CommandResult showFactoryResult;
    Object listFactoryResultData;
    BasicDBObject tcfs = new BasicDBObject("name", "tcfs");
    listFactoryResult = listWMQConnectionFactories(nodeName, serverName, "TCF");

    listFactoryResultData = listFactoryResult.getResult();
    if (listFactoryResultData instanceof Object[])
    {
      Object[] resDataArr = (Object[]) listFactoryResultData;
      for (Object resData : resDataArr)
      {
        System.out.println(resData);
      }
    }
    else
    {
      ArrayList<ObjectName> factoryList = (ArrayList<ObjectName>)listFactoryResultData;
      for (int i = 0; i < factoryList.size(); i++)
      {
        ObjectName nextConnFactory = (ObjectName)factoryList.get(i);
        BasicDBObject tcf = new BasicDBObject();
        showFactoryResult = showWMQConnectionFactory(nextConnFactory);
        HashMap<String,String> factoryProps = (HashMap<String,String>)showFactoryResult.getResult();
        for (String nextKey : factoryProps.keySet()){
          Object propValue = factoryProps.get(nextKey);
          tcf.append(convertFromDotToCamel(mapProfileNameToConfigName(nextKey,"tcfs",serverPropertyTypeCollection)), propValue);
        }
        tcfs.append(nextConnFactory.getKeyProperty("_Websphere_Config_Data_Display_Name"), tcf);
      }
    }
    return tcfs;
  }

  private BasicDBObject populateActivationSpecs(String nodeName, String serverName)
  {
    BasicDBObject ases = new BasicDBObject("name", "ases");
    CommandResult listActivationSpecResult = listWMQActivationSpecs(nodeName, serverName);

    Object listActivationSpecResultData = listActivationSpecResult.getResult();
    ObjectName[] specList = (ObjectName[])listActivationSpecResultData;
    for (int i = 0; i < specList.length; i++)
    {
      ObjectName nextSpec = (ObjectName)specList[i];
      BasicDBObject as = new BasicDBObject();
      CommandResult showSpecResult = showWMQActivationSpec(nextSpec);
      HashMap<String,String> specProps = (HashMap<String,String>)showSpecResult.getResult();
      for (String nextKey : specProps.keySet()){
        Object propValue = specProps.get(nextKey);
        as.append(convertFromDotToCamel(mapProfileNameToConfigName(nextKey,"ases",serverPropertyTypeCollection)), propValue);
      }
      ases.append(nextSpec.getKeyProperty("_Websphere_Config_Data_Display_Name"), as);
    }
    return ases;
  }

  private void populateServerPorts(BasicDBObject serverDoc, String nodeName, String serverName)
  {
    CommandResult listServerPortResult = listServerPorts(nodeName, serverName);
    Object listServerPortResultData = listServerPortResult.getResult();

    ArrayList<AttributeList> portList = (ArrayList<AttributeList>)listServerPortResultData;
    for (int i = 0; i < portList.size(); i++)
    {
      AttributeList nextServerPort = (AttributeList)portList.get(i);
      for (int j = 0; j < nextServerPort.size(); j++)
      {
        Object nextAttr = nextServerPort.get(j);
        if (nextAttr instanceof Attribute)
        {
          Attribute nextAttribute = (Attribute)nextAttr;
          AttributeList portAttrList = (AttributeList) nextAttribute.getValue();
          for (int k = 0; k < portAttrList.size(); k++)
          {
            Attribute nextPortAttr = (Attribute)portAttrList.get(k);
            if (nextPortAttr.getName().equals("port")){
              serverDoc.append(convertFromDotToCamel(mapProfileNameToConfigName(nextAttribute.getName(), "root", serverPropertyTypeCollection)), nextPortAttr.getValue());
              break;
            }
          }
        }
      }
    }
  }

  private CommandResult showJvmProperties(String nodeName, String serverName, boolean systemLevel)
  {
    AdminCommand cmd;
    CommandResult result = null;

    try
    {
      if (systemLevel)
        cmd = cmdMgr.createCommand("showJVMSystemProperties");
      else
        cmd = cmdMgr.createCommand("showJVMProperties");

      cmd.setConfigSession(session);

      cmd.setParameter("serverName", serverName);
      cmd.setParameter("nodeName", nodeName);
      //ObjectName targetObj = getTargetObject(targetObjStr);
      //cmd.setTargetObject(targetObj);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.processCommandParameters(cmd);
      asyncCmdClientHelper.execute(cmd);

      result = cmd.getCommandResult();
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return result;
  }

  private void createAdminClient(String URL) throws ConnectorException
  {
    // Set up a Properties object for the JMX connector attributes
    Properties connectProps = new Properties();
    String IP = URL.substring(0, URL.indexOf(":"));
    String port = URL.substring(URL.indexOf(":") + 1);
    connectProps.setProperty(AdminClient.CONNECTOR_TYPE, AdminClient.CONNECTOR_TYPE_SOAP);
    connectProps.setProperty(AdminClient.CONNECTOR_HOST, (IP == null || IP.equals("")) ? DMGR_HOST : IP);
    connectProps.setProperty(AdminClient.CONNECTOR_PORT, (port == null || port.equals("")) ? DMGR_PORT : port);

    // Get an AdminClient based on the connector properties
    try
    {
      adminClient = AdminClientFactory.createAdminClient(connectProps);
    }
    catch (ConnectorException e)
    {
      System.out.println("Exception creating admin client: " + e);
      throw e;
    }
    URL = IP + ":" + port;
    System.out.println("Connected to DeploymentManager");
  }

  private void createCommandMgr()
  {
    this.session = new Session();
    try
    {
      this.cfgService = new ConfigServiceProxy(adminClient);
      this.cmdMgr = CommandMgr.getCommandMgr(adminClient);
    }
    catch (InstanceNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (CommandMgrInitException e)
    {
      e.printStackTrace();
    }
  }

  private void listNodesInCell()
  {
    AdminCommand cmd;
    try
    {
      cmd = cmdMgr.createCommand("listNodes");
      cmd.setConfigSession(session);

      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, null);
      asyncCmdClientHelper.execute(cmd);

      CommandResult result = cmd.getCommandResult();
      if (result.isSuccessful())
      {
        System.out.println("Successfully executed the command");
        System.out.println("Result: ");
        Object resultData = result.getResult();
        if (resultData instanceof Object[])
        {
          Object[] resDataArr = (Object[]) resultData;
          for (Object resData : resDataArr)
          {
            System.out.println(resData);
          }
        }
        else
        {
          System.out.println(resultData);
        }
      }
      else
      {
        System.out.println("Failed to execute the command");
        result.getException().printStackTrace();
      }
    }
    catch (CommandNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void listServersInCell()
  {
    AdminCommand cmd;
    try
    {
      cmd = cmdMgr.createCommand("listServers");
      cmd.setConfigSession(session);
      cmd.setParameter("nodeName", "VBSTEST_NODE01");
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, null);
      asyncCmdClientHelper.processCommandParameters(cmd);
      asyncCmdClientHelper.execute(cmd);

      CommandResult result = cmd.getCommandResult();
      if (result.isSuccessful())
      {
        System.out.println("Successfully executed the command");
        System.out.println("Result: ");
        Object resultData = result.getResult();
        if (resultData instanceof Object[])
        {
          Object[] resDataArr = (Object[]) resultData;
          for (Object resData : resDataArr)
          {
            System.out.println(resData);
          }
        }
        else
        {
          System.out.println(resultData);
        }
      }
      else
      {
        System.out.println("Failed to execute the command");
        result.getException().printStackTrace();
      }
    }
    catch (CommandNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private CommandResult listWMQConnectionFactories(String nodeName, String serverName, String typeName)
  {
/*    PythonInterpreter interpreter = new PythonInterpreter();
    interpreter.exec("from MQFunctions import MQFunctions");
    PyObject mqFunctionsClass = interpreter.get("MQFunctions");
    PyObject mqFunctionsObject = mqFunctionsClass.__call__();
    MQFunctionsType mqFunctionsType = (MQFunctionsType) mqFunctionsObject.__tojava__(MQFunctionsType.class);
    mqFunctionsType.initialise();
    System.out.println(mqFunctionsType.listMQQueueConnectionFactories("BS_Cell", "", "VBSTEST_NODE01", "BS_APPSRV_01", 1));
*/
    String targetObjStr = "Cell=BS_Cell:Node=" + nodeName + ":Server=" + serverName + ":JMSProvider=\"WebSphere MQ JMS Provider\"";
    AdminCommand listFactoriesCmd, showFactoryCmd;
    CommandResult listFactoriesResult = null, showFactoryResult = null;

    try
    {
      listFactoriesCmd = cmdMgr.createCommand("listWMQConnectionFactories");

      listFactoriesCmd.setConfigSession(session);

      listFactoriesCmd.setParameter("type", typeName);
      ObjectName targetObj = getTargetObject(targetObjStr);
      listFactoriesCmd.setTargetObject(targetObj);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.processCommandParameters(listFactoriesCmd);
      asyncCmdClientHelper.execute(listFactoriesCmd);

      listFactoriesResult = listFactoriesCmd.getCommandResult();
      if (!listFactoriesResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw listFactoriesResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return listFactoriesResult;

  }
  private CommandResult listWMQActivationSpecs(String nodeName, String serverName)
  {
    String targetObjStr = "Cell=BS_Cell:Node=" + nodeName + ":Server=" + serverName + ":JMSProvider=\"WebSphere MQ JMS Provider\"";
    AdminCommand listActivationSpecsCmd;
    CommandResult listActivationSpecsResult = null;

    try
    {
      listActivationSpecsCmd = cmdMgr.createCommand("listWMQActivationSpecs");

      listActivationSpecsCmd.setConfigSession(session);

      ObjectName targetObj = getTargetObject(targetObjStr);
      listActivationSpecsCmd.setTargetObject(targetObj);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.execute(listActivationSpecsCmd);

      listActivationSpecsResult = listActivationSpecsCmd.getCommandResult();
      if (!listActivationSpecsResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw listActivationSpecsResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return listActivationSpecsResult;
  }

  private CommandResult listServerPorts(String nodeName, String serverName)
  {
    String targetObjStr = serverName;
    AdminCommand listServerPortsCmd;
    CommandResult listServerPortsResult = null;

    try
    {
      listServerPortsCmd = cmdMgr.createCommand("listServerPorts");

      listServerPortsCmd.setConfigSession(session);

      listServerPortsCmd.setTargetObject(targetObjStr);
      listServerPortsCmd.setParameter("nodeName", nodeName);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.processCommandParameters(listServerPortsCmd);
      asyncCmdClientHelper.execute(listServerPortsCmd);

      listServerPortsResult = listServerPortsCmd.getCommandResult();
      if (!listServerPortsResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw listServerPortsResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return listServerPortsResult;
  }

  private CommandResult showWMQConnectionFactory(ObjectName factoryObject)
  {
/*    PythonInterpreter interpreter = new PythonInterpreter();
    interpreter.exec("from MQFunctions import MQFunctions");
    PyObject mqFunctionsClass = interpreter.get("MQFunctions");
    PyObject mqFunctionsObject = mqFunctionsClass.__call__();
    MQFunctionsType mqFunctionsType = (MQFunctionsType) mqFunctionsObject.__tojava__(MQFunctionsType.class);
    mqFunctionsType.initialise();
    System.out.println(mqFunctionsType.listMQQueueConnectionFactories("BS_Cell", "", "VBSTEST_NODE01", "BS_APPSRV_01", 1));
*/
          //System.out.println(resultData);
    AdminCommand showFactoryCmd;
    CommandResult showFactoryResult = null;

    try
    {
      showFactoryCmd = cmdMgr.createCommand("showWMQConnectionFactory");
      showFactoryCmd.setTargetObject(factoryObject);
      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);

      asyncCmdClientHelper.execute(showFactoryCmd);

      showFactoryResult = showFactoryCmd.getCommandResult();
      if (!showFactoryResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw showFactoryResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return showFactoryResult;

  }
  private CommandResult showWMQActivationSpec(ObjectName activationSpecObject)
  {
    AdminCommand showSpecCmd;
    CommandResult showSpecResult = null;

    try
    {
      showSpecCmd = cmdMgr.createCommand("showWMQActivationSpec");
      showSpecCmd.setTargetObject(activationSpecObject);
      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);

      asyncCmdClientHelper.execute(showSpecCmd);

      showSpecResult = showSpecCmd.getCommandResult();
      if (!showSpecResult.isSuccessful())
      {
        System.out.println("Failed to execute the command");
        throw showSpecResult.getException();
      }
    }
    catch (CommandNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      e.printStackTrace();
    }

    return showSpecResult;
  }

  private void showServerInfo(String targetObjStr)
  {
    AdminCommand cmd;

    try
    {
      cmd = cmdMgr.createCommand("getJMSQueueConnectionFactories");
      //cmd = cmdMgr.createCommand("showServerInfo");

      cmd.setConfigSession(session);

      ObjectName targetObj = getTargetObject(targetObjStr);
      cmd.setTargetObject(targetObj);

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.execute(cmd);

      CommandResult result = cmd.getCommandResult();
      if (result.isSuccessful())
      {
        System.out.println("Successfully executed the command");
        System.out.println("Result: ");
        Object resultData = result.getResult();
        if (resultData instanceof Object[])
        {
          Object[] resDataArr = (Object[]) resultData;
          for (Object resData : resDataArr)
          {
            System.out.println(resData);
          }
        }
        else
        {
          System.out.println(resultData);
        }
      }
      else
      {
        System.out.println("Failed to execute the command");
        result.getException().printStackTrace();
      }

    }
    catch (CommandNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void listAllCommand()
  {
    Collection<?> cmds;
    try
    {
      //cmds = cmdMgr.listCommandGroups();
      cmds = cmdMgr.listAllCommands();
      for (Object cmd : cmds)
        System.out.println(cmd);
    }
    catch (CommandException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private ObjectName getTargetObject(String query)
  {
    try
    {
      return cfgService.resolve(session, query)[0];
    }
    catch (ConfigServiceException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return null;
  }

  private void listBLAsInCell(String[] params)
  {
    AdminCommand cmd;
    try
    {
      cmd = cmdMgr.createCommand("listBLAs");
      cmd.setConfigSession(session);

      for (int i = 0; i < params.length; i++)
      {
        String paramName = params[i];
        String paramValue = null;
        if (i + 1 < params.length)
        {
          i++;
          paramValue = params[i];
        }
        cmd.setParameter(paramName, paramValue);
      }

      AsyncCmdHandler handler = new AsyncCmdHandler();
      AsyncCommandClient asyncCmdClientHelper = new AsyncCommandClient(session, handler);
      asyncCmdClientHelper.processCommandParameters(cmd);
      asyncCmdClientHelper.execute(cmd);

      CommandResult result = cmd.getCommandResult();
      if (result.isSuccessful())
      {
        System.out.println("Successfully executed the command");
        System.out.println("Result: ");
        Object resultData = result.getResult();
        if (resultData instanceof Object[])
        {
          Object[] resDataArr = (Object[]) resultData;
          for (Object resData : resDataArr)
          {
            System.out.println(resData);
          }
        }
        else
        {
          System.out.println(resultData);
        }
      }
      else
      {
        System.out.println("Failed to execute the command");
        result.getException().printStackTrace();
      }
    }
    catch (CommandNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (CommandException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (ConnectorException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (Throwable e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void getNodeAgentMBeans()
  {
    // Query for the ObjectName of the NodeAgent MBean on the given node
    try
    {
      String query = "WebSphere:type=NodeAgent,*";
      ObjectName queryName = new ObjectName(query);
      Set<?> s = adminClient.queryNames(queryName, null);
      if (!s.isEmpty())
      {
        nodeAgents = s.toArray(new ObjectName[s.size()]);
        printNodeAgentsDetails();
      }
      else
      {
        System.out.println("No node agent MBeans were found");
        System.exit(-1);
      }
    }
    catch (MalformedObjectNameException e)
    {
      System.out.println(e);
      System.exit(-1);
    }
    catch (ConnectorException e)
    {
      System.out.println(e);
      System.exit(-1);
    }

    System.out.println("Found NodeAgent MBeans");
  }

  private void invokeLaunchProcess(String serverName)
  {
    // Use the launchProcess operation on the NodeAgent MBean to start
    // the given server
    String opName = "launchProcess";
    String signature[] =
    {
      "java.lang.String"
    };
    String params[] =
    {
      serverName
    };
    boolean launched = false;
    try
    {
      Boolean b = (Boolean) adminClient.invoke(nodeAgents[0], opName, params, signature);
      launched = b.booleanValue();
      if (launched)
        System.out.println(serverName + " was launched");
      else
        System.out.println(serverName + " was not launched");

    }
    catch (Exception e)
    {
      System.out.println("Exception invoking launchProcess: " + e);
    }
  }

  private void invokeStopProcess(String serverName)
  {
    // Use the launchProcess operation on the NodeAgent MBean to start
    // the given server
    String opName = "launchProcess";
    String signature[] =
    {
      "java.lang.String"
    };
    String params[] =
    {
      serverName
    };
    boolean launched = false;
    try
    {
      Boolean b = (Boolean) adminClient.invoke(nodeAgents[0], opName, params, signature);
      launched = b.booleanValue();
      if (launched)
        System.out.println(serverName + " was launched");
      else
        System.out.println(serverName + " was not launched");

    }
    catch (Exception e)
    {
      System.out.println("Exception invoking launchProcess: " + e);
    }
  }

  private void registerNotificationListener()
  {
    for (ObjectName nodeAgent : nodeAgents)
    {
      // Register this object as a listener for notifications from the
      // NodeAgent MBean.  Don't use a filter and don't use a handback
      // object.
      try
      {
        adminClient.addNotificationListener(nodeAgent, this, null, null);
        System.out.println("Registered for event notifications");
      }
      catch (InstanceNotFoundException e)
      {
        System.out.println(e);
        e.printStackTrace();
      }
      catch (ConnectorException e)
      {
        System.out.println(e);
        e.printStackTrace();
      }
    }
  }

  private void printNodeAgentsDetails()
  {
    for (ObjectName nodeAgent : nodeAgents)
    {
      printNodeDetails(nodeAgent);
    }
  }

  public void handleNotification(Notification ntfyObj, Object handback)
  {
    // Each notification that the NodeAgent MBean generates will result in
    // this method being called
    ntfyCount++;
    System.out.println("***************************************************");
    System.out.println("* Notification received at " + new Date().toString());
    System.out.println("* type      = " + ntfyObj.getType());
    System.out.println("* message   = " + ntfyObj.getMessage());
    System.out.println("* source    = " + ntfyObj.getSource());
    System.out.println("* seqNum    = " + Long.toString(ntfyObj.getSequenceNumber()));
    System.out.println("* timeStamp = " + new Date(ntfyObj.getTimeStamp()));
    System.out.println("* userData  = " + ntfyObj.getUserData());
    System.out.println("***************************************************");

  }

  public void printNodeDetails(ObjectName nodeAgent)
  {
    System.out.println("***************************************************");
    System.out.println("* NodeAgent canonical name      = " + nodeAgent.getCanonicalName());
    System.out.println("***************************************************");
  }

  @SuppressWarnings("static-access")
  private void countNotifications()
  {
    // Run until killed
    try
    {
      while (true)
      {
        Thread.currentThread().sleep(60000);
        System.out.println(ntfyCount + " notification have been received");
      }
    }
    catch (InterruptedException e)
    {
    }
  }

  public DBCollection getApplications()
  {
    return database.getCollection("application");
  }

  public DBCollection getClusters()
  {
    return database.getCollection("cluster");
  }

  public DBCollection getServers()
  {
    return database.getCollection("server");
  }

  public DBCollection getPrefixes()
  {
    return database.getCollection("prefix");
  }

  public DBCollection getNodes()
  {
    return database.getCollection("node");
  }

  public String getURL()
  {
    return URL;
  }

  public DBObject getProfileDocByNameAndType(String name, String type)
  {
    DBObject profileDoc = null;
    for (String collectionName : database.getCollectionNames())
    {
      DBCollection collection = database.getCollection(collectionName);
      BasicDBObject searchFor = new BasicDBObject("name", name);
      searchFor.append("type", type);
      profileDoc = collection.findOne(searchFor);
      if (profileDoc != null)
        break;
    }
    return profileDoc;
  }

}
