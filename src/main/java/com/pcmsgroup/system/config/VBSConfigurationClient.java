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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.ibm.websphere.management.async.client.AsyncCommandHandlerIF;
import com.ibm.websphere.management.cmdframework.provider.CommandNotification;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

@Component
public class VBSConfigurationClient extends ManagementClient
{
  @Autowired(required = true)
  protected MongoClient mongoConfigClient;

  @Value("classpath:config.A.properties")
  private Properties config;

  @Value("classpath:config.A.properties")
  private Resource configFile;

  private String prefixServerCount, prefixServerName, prefixServerPort, filterKey;
  private BasicDBObject jaasDefaults, serverDefaults, variableDefaults, jdbcProviderDefaults, mqDefaults, applicationDefaults;
  protected MongoTemplate mongoConfigTemplate;

  public VBSConfigurationClient()
  {
  }

  @PostConstruct
  public void init() throws IOException
  {
    this.readConfigDotProperties(configFile);
    mongoConnect("vbs_config");
    this.clearConfigAndPopulateFromFile();
  }

  protected void mongoConnect(String databaseName) throws IOException
  {
    mongoConfigTemplate = new MongoTemplate(mongoConfigClient, databaseName);
    database = mongoConfigTemplate.getDb();
    database.dropDatabase();

    System.out.println("Connected to mongo...");
  }

  public void readConfigDotProperties(Resource configFile) throws IOException
  {
    InputStream in = null;
    config = new Properties();
    if (configFile == null)
      throw new IOException("ConfigFile resource not present");

    in = configFile.getInputStream();

    if (in == null)
    {
      throw new IOException("Unable to find " + configFile.getFilename());
    }
    else
    {
      try
      {
        config.load(in);
      }
      finally
      {
        if (in != null)
          in.close();
      }
    }
  }

  public class AsyncCmdHandler implements AsyncCommandHandlerIF
  {

    public void handleNotification(CommandNotification commandnotification)
    {
      System.out.println("Notification received: " + commandnotification);
    }

  }

  public void clearConfigAndPopulateFromFile()
  {
    //mongoConnect("vbs_config");
    database.dropDatabase();
    DBCollection prefixCollection = database.getCollection("prefix");
    DBCollection webserverCollection = database.getCollection("webserver");

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

    ArrayList<String> prefixes = new ArrayList<String>();
    processAndStoreDefaults();
    prefixes.add("default");

    for (DBObject nextPrefixType : prefixTypeCollection.find())
    {
      prefixes.add((String) nextPrefixType.get("name"));
      prefixCollection.insert(handlePrefix((String) nextPrefixType.get("name")));
    }

    BasicDBObject webservers = processWebServers();
    if (webservers != null && !webservers.isEmpty())
    {
      webserverCollection.insert(webservers);
      prefixes.add("webserver");
    }

    DBCollection globalCollection = database.getCollection("global");
    BasicDBObject global = handleGlobalProps(prefixes);
    if (global != null && !global.isEmpty())
      globalCollection.insert(global);

  }

  private BasicDBObject processWebServers()
  {
    Map<String, String> prefixProperties = createConfigMap("webserver");

    BasicDBObject webservers = new BasicDBObject();
    String webserverCount = config.getProperty("pcms.webserver.count");
    if (webserverCount != null && !webserverCount.equals(""))
    {
      for (int i = 0; i < Integer.parseInt(webserverCount); i++)
      {
        String webserverPrefix = "pcms.webserver." + i + ".";
        BasicDBObject webserver = filterAndPopulateDocument(prefixProperties.keySet(), webserverPrefix);
        webservers.append("webserver" + i, webserver);
      }
    }
    return webservers;
  }

  private void processAndStoreDefaults()
  {
    Map<String, String> defaultProperties = new HashMap<String, String>();

    for (Enumeration<?> e = config.propertyNames(); e.hasMoreElements();)
    {
      String name = (String) e.nextElement();
      String value = config.getProperty(name);
      // now you have name and value
      if (name.startsWith("pcms.default"))
      {
        defaultProperties.put(name, value);
      }
    }
    Set<String> defaultKeys = defaultProperties.keySet();

    Set<String> defaultApplicationKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.application")).collect(Collectors.toSet());
    processApplicationDefaults(defaultApplicationKeys);
    DBCollection applicationDefaultsCollection = database.getCollection("applictionDefaults");
    applicationDefaultsCollection.insert(applicationDefaults);

    Set<String> defaultServerKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.server")).collect(Collectors.toSet());
    processServerDefaults(defaultServerKeys);
    DBCollection serverDefaultsCollection = database.getCollection("serverDefaults");
    serverDefaultsCollection.insert(serverDefaults);

    Set<String> defaultVariableKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.variable")).collect(Collectors.toSet());
    processVariableDefaults(defaultVariableKeys);
    DBCollection variableDefaultsCollection = database.getCollection("variableDefaults");
    variableDefaultsCollection.insert(variableDefaults);

    Set<String> defaultJaasKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.jaas")).collect(Collectors.toSet());
    processJaasDefaults(defaultJaasKeys);
    DBCollection jaasDefaultsCollection = database.getCollection("jaasDefaults");
    jaasDefaultsCollection.insert(jaasDefaults);

    Set<String> defaultJdbcProviderKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.jdbcprovider")).collect(Collectors.toSet());
    processJdbcProviderDefaults(defaultJdbcProviderKeys);
    DBCollection jdbcProviderDefaultsCollection = database.getCollection("jdbcProviderDefaults");
    jdbcProviderDefaultsCollection.insert(jdbcProviderDefaults);

    Set<String> defaultMqKeys = defaultKeys.stream().filter(s -> s.startsWith("pcms.default.mq")).collect(Collectors.toSet());
    processMqDefaults(defaultMqKeys);
    DBCollection mqDefaultsCollection = database.getCollection("mqDefaults");
    mqDefaultsCollection.insert(mqDefaults);

  }

  private void processMqDefaults(Set<String> defaultMqKeys)
  {
    mqDefaults = new BasicDBObject();

    mqDefaults.append("cluster", (String) config.get("pcms.default.mq.cluster"));
    mqDefaults.append("node", (String) config.get("pcms.default.mq.node"));
    mqDefaults.append("server", (String) config.get("pcms.default.mq.server"));

    String mqTopicCount = config.getProperty("pcms.default.mq.topic.count");
    if (Integer.parseInt(mqTopicCount) > 0)
    {
      for (int i = 0; i < Integer.parseInt(mqTopicCount); i++)
      {
        Document topic = new Document();
        String topicPrefix = "pcms.default.mq.topic." + i + ".";
        Set<String> topicKeys = defaultMqKeys.stream().filter(s -> s.startsWith(topicPrefix)).collect(Collectors.toSet());
        for (String nextKey : topicKeys)
        {
          String propValue = config.getProperty(nextKey);
          topic.append(nextKey.substring(topicPrefix.length()), propValue);
        }
        mqDefaults.append("topic" + i, topic);
      }
    }

    String mqQueueCount = config.getProperty("pcms.default.mq.queue.count");
    if (Integer.parseInt(mqQueueCount) > 0)
    {
      for (int i = 0; i < Integer.parseInt(mqQueueCount); i++)
      {
        Document queue = new Document();
        String queuePrefix = "pcms.default.mq.queue." + i + ".";
        Set<String> queueKeys = defaultMqKeys.stream().filter(s -> s.startsWith(queuePrefix)).collect(Collectors.toSet());
        for (String nextKey : queueKeys)
        {
          String propValue = config.getProperty(nextKey);
          queue.append(nextKey.substring(queuePrefix.length()), propValue);
        }
        mqDefaults.append("queue" + i, queue);
      }
    }

    String qcfPrefix = "pcms.default.mq.qcf";
    BasicDBObject mqQcf = filterAndPopulateDocument(defaultMqKeys, qcfPrefix);
    mqDefaults.append("qcf", mqQcf);

    String tcfPrefix = "pcms.default.mq.tcf";
    BasicDBObject mqTcf = filterAndPopulateDocument(defaultMqKeys, tcfPrefix);
    mqDefaults.append("tcf", mqTcf);

    String asPrefix = "pcms.default.mq.as";
    Set<String> asKeys = defaultMqKeys.stream().filter(s -> s.startsWith(asPrefix)).filter(s -> !s.contains("as.prop")).collect(Collectors.toSet());
    BasicDBObject mqActivationSpec = new BasicDBObject();
    for (String nextKey : asKeys)
    {
      String propValue = config.getProperty(nextKey);
      mqActivationSpec.append(convertFromDotToCamel(nextKey.substring(asPrefix.length() + 1)), propValue);
    }

    String asPropCount = config.getProperty(asPrefix + ".prop.count");
    if (Integer.parseInt(asPropCount) > 0)
    {
      for (int i = 0; i < Integer.parseInt(asPropCount); i++)
      {
        String asPropPrefix = asPrefix + ".prop." + i + ".";
        BasicDBObject asProperty = filterAndPopulateDocument(defaultMqKeys, asPropPrefix);
        mqActivationSpec.append("asProperty" + i, asProperty);
      }
    }

    mqDefaults.append("activationSpec", mqActivationSpec);
  }

  private void processJdbcProviderDefaults(Set<String> defaultJdbcProviderKeys)
  {
    jdbcProviderDefaults = new BasicDBObject();

    String jdbcProviderCount = config.getProperty("pcms.default.jdbcprovider.count");
    if (Integer.parseInt(jdbcProviderCount) > 0)
    {
      for (int i = 0; i < Integer.parseInt(jdbcProviderCount); i++) //each provider
      {
        BasicDBObject jdbcProvider = new BasicDBObject();
        String prefix = "pcms.default.jdbcprovider." + i + ".";
        Set<String> thisProvidersKeys = defaultJdbcProviderKeys.stream().filter(s -> s.startsWith(prefix)).filter(s -> !s.contains("datasource")).collect(Collectors.toSet());
        for (String nextKey : thisProvidersKeys)
        {
          String nextValue = config.getProperty(nextKey);
          jdbcProvider.append(nextKey.substring(prefix.length()), nextValue);
        }

        String jdbcDatasourceCount = config.getProperty(prefix + "datasource.count");

        if (Integer.parseInt(jdbcDatasourceCount) > 0)
        {
          for (int j = 0; j < Integer.parseInt(jdbcDatasourceCount); j++)
          {
            BasicDBObject datasource = new BasicDBObject();

            String datasourcePrefix = prefix + "datasource." + j + ".";
            Set<String> datasourceKeys = defaultJdbcProviderKeys.stream().filter(s -> s.startsWith(datasourcePrefix)).filter(s -> !s.contains("customprops")).collect(Collectors.toSet());

            for (String nextKey : datasourceKeys)
            {
              String propValue = config.getProperty(nextKey);
              datasource.append(convertFromDotToCamel(nextKey.substring(datasourcePrefix.length())), propValue);
            }

            String dsCustomPropertyCount = config.getProperty(datasourcePrefix + "customprops.count");
            if (Integer.parseInt(dsCustomPropertyCount) > 0)
            {
              for (int k = 0; k < Integer.parseInt(dsCustomPropertyCount); k++)
              {
                String customPropsPrefix = datasourcePrefix + "customprops." + k + ".";
                Set<String> customPropsKeys = defaultJdbcProviderKeys.stream().filter(s -> s.startsWith(customPropsPrefix)).collect(Collectors.toSet());
                BasicDBObject customProp = populateDocument(customPropsPrefix, customPropsKeys);
                datasource.append("customProp" + k, customProp);
              }
            }

            jdbcProvider.append("datasource" + j, datasource);
          }
        }

        jdbcProviderDefaults.append("jdbcProvider" + i, jdbcProvider);
      }
    }
  }

  private void processJaasDefaults(Set<String> defaultJaasKeys)
  {
    jaasDefaults = new BasicDBObject();

    for (int i = 0; i < defaultJaasKeys.size(); i++)
    {
      String prefix = "pcms.default.jaas." + i + ".";
      Set<String> jaasIdKeys = defaultJaasKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
      if (jaasIdKeys != null && !jaasIdKeys.isEmpty())
      {
        BasicDBObject jaasId = populateDocument(prefix, jaasIdKeys);
        jaasDefaults.append("jaasId" + i, jaasId);
      }
      else
      {
        break;
      }
    }
  }

  private void processVariableDefaults(Set<String> defaultVariableKeys)
  {
    variableDefaults = new BasicDBObject();

    for (int i = 0; i < defaultVariableKeys.size(); i++)
    {
      String prefix = "pcms.default.variable." + i + ".";
      Set<String> variableKeys = defaultVariableKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
      if (variableKeys != null && !variableKeys.isEmpty())
      {
        BasicDBObject variable = populateDocument(prefix, variableKeys);
        variableDefaults.append("variable" + i, variable);
      }
      else
      {
        break;
      }
    }
  }

  private void processServerDefaults(Set<String> defaultServerKeys)
  {
    serverDefaults = new BasicDBObject();

    processNestedProperties(defaultServerKeys, "pcms.default.server.session.property.", "sessionProperty");

    processNestedProperties(defaultServerKeys, "pcms.default.server.jvm.property.", "jvmProperty");

    processNestedProperties(defaultServerKeys, "pcms.default.server.processDef.property.", "processDefProperty");

    processNestedProperties(defaultServerKeys, "pcms.default.server.webcontainer.property.", "webContainerProperty");

    processNestedProperties(defaultServerKeys, "pcms.default.server.log.attribute.", "logAttribute");

    processNestedProperties(defaultServerKeys, "pcms.default.server.errlog.attribute.", "errlogAttribute");

    serverDefaults.append("genericJvmArguments", config.getProperty("pcms.default.server.genericJvmArguments"));
    serverDefaults.append("maximumHeapSize", config.getProperty("pcms.default.server.maximumHeapSize"));
    serverDefaults.append("minimumHeapSize", config.getProperty("pcms.default.server.minimumHeapSize"));
    serverDefaults.append("portIncrement", config.getProperty("pcms.default.server.port.increment"));
    serverDefaults.append("verboseGarbageCollection", config.getProperty("pcms.default.server.verboseGarbageCollection"));
  }

  private void processNestedProperties(Set<String> keys, String prefix, String type)
  {
    for (int i = 0; i < keys.size(); i++)
    {
      String nestedPrefix = prefix + i + ".";
      Set<String> nestedKeys = keys.stream().filter(s -> s.startsWith(nestedPrefix)).collect(Collectors.toSet());
      if (nestedKeys != null && !nestedKeys.isEmpty())
      {
        BasicDBObject property = populateDocument(nestedPrefix, nestedKeys);
        serverDefaults.append(type + i, property);
      }
      else
      {
        break;
      }
    }
  }

  private void processApplicationDefaults(Set<String> defaultApplicationKeys)
  {
    applicationDefaults = new BasicDBObject();

    String optionCount = config.getProperty("pcms.default.application.options.count");
    if (Integer.parseInt(optionCount) > 0)
    {
      for (int i = 0; i < Integer.parseInt(optionCount); i++)
      {
        Document applicationOption = new Document();
        String prefix = "pcms.default.application.options." + i + ".";
        Set<String> applicationOptionKeys = defaultApplicationKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
        for (String nextKey : applicationOptionKeys)
        {
          String propValue = config.getProperty(nextKey);
          applicationOption.append(convertFromDotToCamel(nextKey.substring(prefix.length())), propValue);
        }
        applicationDefaults.append("applicationOption" + i, applicationOption);
      }
    }
  }

  private BasicDBObject handleGlobalProps(ArrayList<String> prefixes)
  {
    BasicDBObject global = new BasicDBObject();
    Map<String, String> prefixProperties = createGlobalMap(prefixes);

    Set<String> globalKeys = prefixProperties.keySet();
    if (globalKeys != null && !globalKeys.isEmpty())
    {
      for (String nextKey : globalKeys)
      {
        String propValue = config.getProperty(nextKey);
        global.append(convertFromDotToCamel(nextKey), propValue);
      }
    }
    return global;
  }

  private Map<String, String> createGlobalMap(ArrayList<String> prefixes)
  {
    HashMap<String, String> globalMap = new HashMap<String, String>();

    for (Enumeration<?> e = config.propertyNames(); e.hasMoreElements();)
    {
      String name = (String) e.nextElement();
      String value = config.getProperty(name);
      // now you have name and value
      boolean propertyNameContainsPrefix = false;
      for (String nextPrefix : prefixes)
      {
        if (name.contains("pcms." + nextPrefix))
        {
          propertyNameContainsPrefix = true;
        }
      }
      if (!propertyNameContainsPrefix)
        globalMap.put(name, value);
    }

    return globalMap;
  }

  private BasicDBObject handlePrefix(String nextPrefix)
  {
    BasicDBObject prefixDoc = new BasicDBObject();
    prefixDoc.append("name", nextPrefix);
    prefixDoc.append("type", "prefix");

    String prefixApplicationCount = "";
    Map<String, String> prefixProperties = createConfigMap(nextPrefix);

    DBCollection clusterCollection = database.getCollection("cluster");

    DBObject clusterDoc = handleClusterProps(nextPrefix, prefixProperties);
    if (clusterDoc != null)
    {
      clusterCollection.insert(clusterDoc);
      prefixDoc.append("clusterid", clusterDoc.get("_id"));
    }

    DBCollection nodeCollection = database.getCollection("node");

    filterKey = "pcms." + nextPrefix + ".node";
    Set<String> nodeKeys = prefixProperties.keySet().stream().filter(s -> s.startsWith(filterKey)).collect(Collectors.toSet());
    if (nodeKeys != null && !nodeKeys.isEmpty())
    {
      ArrayList<BasicDBObject> nodes = handleNodeProps(nodeKeys);
      for (int i = 0; i < nodes.size(); i++)
      {
        BasicDBObject node = nodes.get(i);
        if (!nodeCollection.find(node).hasNext())
        {
          nodeCollection.insert(node);
          prefixDoc.append("node" + i, node.get("_id"));
        }
      }
    }

    DBCollection virtualhostCollection = database.getCollection("virtualhost");

    filterKey = "pcms." + nextPrefix + ".virtualhost";
    Set<String> virtualhostKeys = prefixProperties.keySet().stream().filter(s -> s.startsWith(filterKey)).collect(Collectors.toSet());
    if (virtualhostKeys != null && !virtualhostKeys.isEmpty())
    {
      BasicDBObject virtualhostDoc = handleVHProps(virtualhostKeys);
      virtualhostCollection.insert(virtualhostDoc);
      prefixDoc.append("virtualhost", virtualhostDoc.get("_id"));
    }

    DBCollection applicationCollection = database.getCollection("application");

    filterKey = "pcms." + nextPrefix + ".application";
    Set<String> applicationKeys = prefixProperties.keySet().stream().filter(s -> s.startsWith(filterKey)).collect(Collectors.toSet());
    if (!applicationKeys.isEmpty())
    {
      prefixApplicationCount = config.getProperty("pcms." + nextPrefix + ".application.count");
      if (prefixApplicationCount != null && !prefixApplicationCount.equals(""))
      {
        prefixDoc.append("applicationCount", prefixApplicationCount);

        for (int i = 0; i < Integer.parseInt(prefixApplicationCount); i++)
        {
          BasicDBObject applicationDoc = handleApplicationProps(nextPrefix, applicationKeys, i);
          applicationCollection.insert(applicationDoc);
          prefixDoc.append("applicationid" + i, applicationDoc.get("_id"));
        }
      }
      else
      {
        BasicDBObject applicationDoc = handleApplicationProps(nextPrefix, applicationKeys, -1);
        applicationCollection.insert(applicationDoc);
        prefixDoc.append("applicationid", applicationDoc.get("_id"));
      }
    }

    DBCollection serverCollection = database.getCollection("server");
    BasicDBObject qcfDefaults = new BasicDBObject();
    BasicDBObject tcfDefaults = new BasicDBObject();
    BasicDBObject asDefaults = new BasicDBObject();

    Set<String> serverKeys = prefixProperties.keySet().stream().filter(s -> s.startsWith("pcms." + nextPrefix + ".server")).collect(Collectors.toSet());
    if (!serverKeys.isEmpty())
    {
      prefixServerCount = config.getProperty("pcms." + nextPrefix + ".server.count");
      prefixServerName = config.getProperty("pcms." + nextPrefix + ".server.name");
      prefixServerPort = config.getProperty("pcms." + nextPrefix + ".server.port");

      if (prefixServerCount != null)
      {
        prefixDoc.append("serverCount", prefixServerCount);
        prefixDoc.append("serverName", prefixServerName);
        prefixDoc.append("serverPort", prefixServerPort);

        for (int i = 0; i < Integer.parseInt(prefixServerCount); i++)
        {
          String serverPrefix = "pcms." + nextPrefix + ".server." + i + ".";

          String serverQcfDefaultName = config.getProperty(serverPrefix + "mq.qcf.default.name");
          if (serverQcfDefaultName != null && !serverQcfDefaultName.equals(""))
          {
            String qcfDefaultPrefix = serverPrefix + "mq.qcf.default.";
            qcfDefaults = filterAndPopulateDocument(serverKeys, qcfDefaultPrefix);
          }

          String serverTcfDefaultName = config.getProperty(serverPrefix + "mq.tcf.default.name");
          if (serverTcfDefaultName != null && !serverTcfDefaultName.equals(""))
          {
            String tcfDefaultPrefix = serverPrefix + "mq.tcf.default.";
            tcfDefaults = filterAndPopulateDocument(serverKeys, tcfDefaultPrefix);
          }

          String serverAsDefaultName = config.getProperty(serverPrefix + "mq.as.default.name");
          if (serverAsDefaultName != null && !serverAsDefaultName.equals(""))
          {
            String asDefaultPrefix = serverPrefix + "mq.as.default.";
            asDefaults = filterAndPopulateDocument(serverKeys, asDefaultPrefix);
          }

          BasicDBObject serverDoc = handleServerProps(nextPrefix, serverKeys, i, qcfDefaults, tcfDefaults, asDefaults);
          serverCollection.insert(serverDoc);
          prefixDoc.append("serverid" + i, serverDoc.get("_id"));
        }
      }
      else
      {
        BasicDBObject serverDoc = handleServerProps(nextPrefix, serverKeys, -1, qcfDefaults, tcfDefaults, asDefaults);
        serverCollection.insert(serverDoc);
        prefixDoc.append("serverid", serverDoc.get("_id"));
      }
    }

    return prefixDoc;
  }

  private Map<String, String> createConfigMap(String nextPrefix)
  {
    Map<String, String> prefixProperties = new HashMap<String, String>();

    for (Enumeration<?> e = config.propertyNames(); e.hasMoreElements();)
    {
      String name = (String) e.nextElement();
      String value = config.getProperty(name);
      // now you have name and value
      if (name.startsWith("pcms." + nextPrefix))
      {
        prefixProperties.put(name, value);
      }
    }
    return prefixProperties;
  }

  /**
   * Internal method to build a Document object representing the options associated with the application.
   *
   * @param prefix The configuration prefix being processed
   * @param applicationKeys The configuration keys for all applications
   * @param applicationIndex The index of the application being processed
   * @return The application properties returned as a Document object that can be inserted into Mongo
   */
  private BasicDBObject handleApplicationProps(String prefix, Set<String> applicationKeys, int applicationIndex)
  {
    BasicDBObject applicationDoc = new BasicDBObject();
    applicationDoc.append("type", "application");
    applicationDoc.append("index", applicationIndex);

    String applicationPrefix = filterKey + "." + applicationIndex + ".";
    Set<String> thisApplicationsKeys = applicationKeys.stream().filter(s -> s.startsWith(applicationPrefix)).collect(Collectors.toSet());

    applicationDoc.append("options", handleApplicationPart(thisApplicationsKeys, applicationPrefix, "options"));

    applicationDoc.append("ejbs", handleApplicationPart(thisApplicationsKeys, applicationPrefix, "ejb"));

    applicationDoc.append("resrefs", handleApplicationPart(thisApplicationsKeys, applicationPrefix, "resref"));

    thisApplicationsKeys = applicationKeys.stream().filter(s -> s.startsWith(applicationPrefix)).filter(s -> !s.contains(".options.")).filter(s -> !s.contains(".ejb.")).filter(s -> !s.contains(".resref.")).collect(Collectors.toSet());
    for (String nextKey : thisApplicationsKeys)
    {
      String propValue = config.getProperty(nextKey);
      applicationDoc.append(convertFromDotToCamel(nextKey.substring(applicationPrefix.length())), propValue);
    }

    return applicationDoc;
  }

  private BasicDBObject handleApplicationPart(Set<String> thisApplicationsKeys, String applicationPrefix, String part)
  {
    BasicDBObject parts = new BasicDBObject();
    for (int i = 0; i < thisApplicationsKeys.size(); i++)
    {
      String partPrefix = applicationPrefix + part + "." + i + ".";
      Set<String> thisPartsKeys = thisApplicationsKeys.stream().filter(s -> s.startsWith(partPrefix)).collect(Collectors.toSet());
      if (thisPartsKeys != null && !thisPartsKeys.isEmpty())
      {
        BasicDBObject partDoc = populateDocument(partPrefix, thisPartsKeys);
        parts.append(part + i, partDoc);
      }
      else
      {
        break;
      }
    }
    return parts;
  }

  private BasicDBObject handleServerProps(String prefix, Set<String> serverKeys, int serverIndex, BasicDBObject qcfDefaults, BasicDBObject tcfDefaults, BasicDBObject asDefaults)
  {
    BasicDBObject serverDoc = new BasicDBObject();
    Set<String> thisServersKeys = null;

    if (serverIndex < 0)
    {
      thisServersKeys = serverKeys.stream().filter(s -> s.startsWith("pcms." + prefix + ".server")).filter(s -> !s.contains("pcms." + prefix + ".server." + serverIndex)).filter(s -> !s.contains("pcms." + prefix + ".cluster.server." + serverIndex)).collect(Collectors.toSet());

      filterKey = "pcms." + prefix + ".server";
      serverDoc = processServerKeys(serverIndex, serverDoc, thisServersKeys, qcfDefaults, tcfDefaults, asDefaults);
    }
    else
    {
      filterKey = "pcms." + prefix + ".server.";
      thisServersKeys = serverKeys.stream().filter(s -> s.startsWith(filterKey)).collect(Collectors.toSet());

      if (thisServersKeys == null || thisServersKeys.isEmpty())
      {
        filterKey = "pcms." + prefix + ".cluster.server.";
        thisServersKeys = serverKeys.stream().filter(s -> s.startsWith(filterKey)).collect(Collectors.toSet());
      }

      serverDoc = processServerKeys(serverIndex, serverDoc, thisServersKeys, qcfDefaults, tcfDefaults, asDefaults);

    }

    return serverDoc;
  }

  private BasicDBObject combineJvmProperties(BasicDBObject serverDoc, Set<String> thisServersKeys)
  {
    BasicDBObject jvmProperties = new BasicDBObject();
    String jvmPropertiesCount = config.getProperty(filterKey + "default.jvm.property.count");
    if (jvmPropertiesCount != null && !jvmPropertiesCount.equals(""))
    {
      for (int i = 0; i < Integer.parseInt(jvmPropertiesCount); i++)
      {
        String jvmPropertyPrefix = filterKey + "default.jvm.property." + i + ".";
        BasicDBObject jvmProperty = filterAndPopulateDocument(thisServersKeys, jvmPropertyPrefix);
        jvmProperties.append(convertFromDotToCamel(jvmProperty.getString("name")), jvmProperty);
      }
    }

    serverDoc.append("jvmProperties", jvmProperties);
    return serverDoc;
  }

  private BasicDBObject processServerKeys(int serverIndex, BasicDBObject serverDoc, Set<String> serverKeys, BasicDBObject qcfDefaults, BasicDBObject tcfDefaults, BasicDBObject asDefaults)
  {
    String serverPrefix = filterKey + serverIndex + ".";
    Set<String> thisServersKeys = serverKeys.stream().filter(s -> s.startsWith(serverPrefix)).collect(Collectors.toSet());
    serverDoc = combineJvmProperties(serverDoc, serverKeys);
    serverDoc.append("type", "server");

    String qcfCount = config.getProperty(serverPrefix + "mq.qcf.count");
    if (qcfCount != null && !qcfCount.equals(""))
    {
      BasicDBObject qcfs = new BasicDBObject();
      for (int i = 0; i < Integer.parseInt(qcfCount); i++)
      {
        String qcfPrefix = serverPrefix + "mq.qcf." + i + ".";
        BasicDBObject qcfDocument = filterAndPopulateDocument(thisServersKeys, qcfPrefix);

        if (qcfDefaults != null && !qcfDefaults.isEmpty())
        {
          for (String nextKey : qcfDefaults.keySet())
          {
            String propValue = qcfDefaults.getString(nextKey);
            qcfDocument.append(nextKey, propValue);
          }
        }
        qcfs.append(convertFromDotToCamel((String)qcfDocument.get("name")), qcfDocument);
      }
      serverDoc.append("qcfs", qcfs);
    }

    String tcfCount = config.getProperty(serverPrefix + "mq.tcf.count");
    if (tcfCount != null && !tcfCount.equals(""))
    {
      BasicDBObject tcfs = new BasicDBObject();
      for (int i = 0; i < Integer.parseInt(tcfCount); i++)
      {
        String tcfPrefix = serverPrefix + "mq.tcf." + i + ".";
        BasicDBObject tcfDocument = filterAndPopulateDocument(thisServersKeys, tcfPrefix);

        if (tcfDefaults != null && !tcfDefaults.isEmpty())
        {
          for (String nextKey : tcfDefaults.keySet())
          {
            String propValue = tcfDefaults.getString(nextKey);
            tcfDocument.append(nextKey, propValue);
          }
        }
        tcfs.append(convertFromDotToCamel((String)tcfDocument.get("name")), tcfDocument);
      }
      serverDoc.append("tcfs", tcfs);
    }

    String asCount = config.getProperty(serverPrefix + "mq.as.count");
    if (asCount != null && !asCount.equals(""))
    {
      BasicDBObject ases = new BasicDBObject();
      for (int i = 0; i < Integer.parseInt(asCount); i++)
      {
        String asPrefix = serverPrefix + "mq.as." + i + ".";
        BasicDBObject asDocument = filterAndPopulateDocument(thisServersKeys, asPrefix);

        if (asDefaults != null && !asDefaults.isEmpty())
        {
          for (String nextKey : asDefaults.keySet())
          {
            String propValue = asDefaults.getString(nextKey);
            asDocument.append(nextKey, propValue);
          }
        }
        ases.append(convertFromDotToCamel((String)asDocument.get("name")), asDocument);
      }
      serverDoc.append("ases", ases);
    }

    return serverDoc;
  }

  private BasicDBObject filterAndPopulateDocument(Set<String> thisServersKeys, String prefix)
  {
    Set<String> keys = thisServersKeys.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toSet());
    BasicDBObject document = populateDocument(prefix, keys);
    return document;
  }

  private BasicDBObject handleVHProps(Set<String> virtualhostKeys)
  {
    BasicDBObject virtualhostDoc = new BasicDBObject();
    virtualhostDoc.append("type", "virtualhost");
    for (int i = 0; i < virtualhostKeys.size(); i++)
    {
      String aliasPrefix = filterKey + ".alias." + i + ".";
      Set<String> aliasKeys = virtualhostKeys.stream().filter(s -> s.startsWith(aliasPrefix)).collect(Collectors.toSet());
      if (aliasKeys != null && !aliasKeys.isEmpty())
      {
        virtualhostDoc.append("alias" + i, populateDocument(aliasPrefix, aliasKeys));
      }
      else
      {
        break;
      }
    }

    Set<String> nonAliasKeys = virtualhostKeys.stream().filter(s -> !s.contains("alias")).collect(Collectors.toSet());
    for (String nextKey : nonAliasKeys)
    {
      String propValue = config.getProperty(nextKey);
      virtualhostDoc.append(convertFromDotToCamel(nextKey.substring(filterKey.length())), propValue);
    }

    return virtualhostDoc;
  }

  private BasicDBObject populateDocument(String prefix, Set<String> keys)
  {
    BasicDBObject document = new BasicDBObject();
    for (String nextKey : keys)
    {
      String propValue = config.getProperty(nextKey);
      document.append(convertFromDotToCamel(nextKey.substring(prefix.length())), propValue);
    }
    return document;
  }

  private ArrayList<BasicDBObject> handleNodeProps(Set<String> nodeKeys)
  {
    ArrayList<BasicDBObject> nodes = new ArrayList<BasicDBObject>();
    for (int i = 0; i < nodeKeys.size(); i++)
    {
      String nodePrefix = filterKey + "." + i + ".";
      Set<String> thisNodesKeys = nodeKeys.stream().filter(s -> s.startsWith(nodePrefix)).collect(Collectors.toSet());
      if (thisNodesKeys != null && !thisNodesKeys.isEmpty())
      {
        BasicDBObject node = populateDocument(nodePrefix, thisNodesKeys);
        node.append("type", "node");
        boolean nodeAlreadyExists = false;
        for (BasicDBObject existingNode : nodes)
        {
          if (existingNode.getString("name").equalsIgnoreCase(node.getString("name")))
          {
            nodeAlreadyExists = true;
          }
        }
        if (!nodeAlreadyExists)
          nodes.add(node);
      }
      else
      {
        break;
      }
    }
    return nodes;
  }

  private BasicDBObject handleClusterProps(String nextPrefix, Map<String, String> prefixProperties)
  {
    BasicDBObject clusterDoc = new BasicDBObject();
    String clusterPrefix = "pcms." + nextPrefix + ".cluster.";
    Set<String> clusterKeys = prefixProperties.keySet().stream().filter(s -> (s.startsWith(clusterPrefix))).filter(s -> (!s.contains(clusterPrefix + "server"))).filter(s -> (!s.contains(clusterPrefix + ".mq."))).collect(Collectors.toSet());

    if (clusterKeys != null && !clusterKeys.isEmpty())
    {
      for (String nextKey : clusterKeys)
      {
        String propValue = config.getProperty(nextKey);
        clusterDoc.append(convertFromDotToCamel(nextKey.substring(clusterPrefix.length())), propValue);
      }
      clusterDoc.append("type", "cluster");

      Set<String> thisClustersServerKeys = prefixProperties.keySet().stream().filter(s -> s.startsWith("pcms." + nextPrefix + ".cluster.server")).collect(Collectors.toSet());

      DBCollection serverCollection = database.getCollection("server");

      BasicDBObject qcfDefaults = new BasicDBObject();
      BasicDBObject tcfDefaults = new BasicDBObject();
      BasicDBObject asDefaults = new BasicDBObject();
      String clusterServerCount = prefixProperties.get(clusterPrefix + "server.count");
      if (clusterServerCount != null && !clusterServerCount.equals(""))
      {
        String clusterQcfDefaultName = config.getProperty(clusterPrefix + "mq.qcf.default.name");
        if (clusterQcfDefaultName != null && !clusterQcfDefaultName.equals(""))
        {
          String qcfDefaultPrefix = clusterPrefix + "mq.qcf.default.";
          qcfDefaults = filterAndPopulateDocument(clusterKeys, qcfDefaultPrefix);
        }

        String clusterTcfDefaultName = config.getProperty(clusterPrefix + "mq.tcf.default.name");
        if (clusterTcfDefaultName != null && !clusterTcfDefaultName.equals(""))
        {
          String tcfDefaultPrefix = clusterPrefix + "mq.tcf.default.";
          tcfDefaults = filterAndPopulateDocument(clusterKeys, tcfDefaultPrefix);
        }

        String clusterAsDefaultName = config.getProperty(clusterPrefix + "mq.as.default.name");
        if (clusterAsDefaultName != null && !clusterAsDefaultName.equals(""))
        {
          String asDefaultPrefix = clusterPrefix + "mq.as.default.";
          asDefaults = filterAndPopulateDocument(clusterKeys, asDefaultPrefix);
        }

        for (int i = 0; i < Integer.parseInt(clusterServerCount); i++)
        {
          BasicDBObject serverDoc = handleServerProps(nextPrefix, thisClustersServerKeys, i, qcfDefaults, tcfDefaults, asDefaults);
          String name = config.getProperty(clusterPrefix + "server.name");
          String port = config.getProperty(clusterPrefix + "server.port");
          serverDoc.append("name", name + "_" + String.format("%02d", i + 1));
          serverDoc.append("port", Integer.valueOf(port) + 7);

          serverCollection.insert(serverDoc);
          clusterDoc.append("serverid" + i, serverDoc.get("_id"));
        }
      }
    }
    else
    {
      clusterDoc = null;
    }

    return clusterDoc;
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

  public DBCollection getNodes()
  {
    return database.getCollection("node");
  }

  public DBCollection getPrefixes()
  {
    return database.getCollection("prefix");
  }

  public DBObject getConfigDocument(String selectedNodeName, String parentNodeName)
  {
    DBObject configDocument = null;
    DBCollection configCollection = null;

    if (parentNodeName.equalsIgnoreCase("prefixes"))
    {
      configCollection = getPrefixes();
    }
    else if (parentNodeName.equalsIgnoreCase("clusters"))
    {
      configCollection = getClusters();
    }
    else if (parentNodeName.equalsIgnoreCase("servers"))
    {
      configCollection = getServers();
    }
    else if (parentNodeName.equalsIgnoreCase("nodes"))
    {
      configCollection = getNodes();
    }
    else if (parentNodeName.equalsIgnoreCase("applications"))
    {
      configCollection = getApplications();
    }

    configDocument = configCollection.findOne(new BasicDBObject("name", selectedNodeName));

    return configDocument;
  }

  public Resource getConfigFile()
  {
    return configFile;
  }

  public void setConfigFile(Resource configFile)
  {
    this.configFile = configFile;
  }

  public MongoClient getMongoConfigClient()
  {
    return mongoConfigClient;
  }

  public void setMongoConfigClient(MongoClient mongoConfigClient)
  {
    this.mongoConfigClient = mongoConfigClient;
  }

  public DBCollection getPropertyTypeCollection(String configComponent)
  {
    return database.getCollection(configComponent + "PropertyType");
  }
}