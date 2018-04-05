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
import java.util.HashMap;

import org.springframework.core.io.InputStreamResource;

import com.ibm.websphere.management.exception.ConnectorException;

/**
 * @author Administrator
 *
 */
public class ConfigurationManager
{
  public static String main(String[] args)
  {
    //JMXClientClass jmxClient = new JMXClientClass();
    //jmxClient.getClusterMemberBootStraps();

    VBSConfigurationClient ace = new VBSConfigurationClient();
    //RunningProfileClient rpc = null;
/*    try
    {
      rpc = new RunningProfileClient(RunningProfileClient.DMGR_HOST + ":" + RunningProfileClient.DMGR_PORT);
    }
    catch (ConnectorException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
*/    ProfileConfigComparator profileComparator = new ProfileConfigComparator();
    InputStream in = VBSConfigurationClient.class.getClassLoader().getResourceAsStream("config.E.properties");
    try
    {
      ace.readConfigDotProperties(new InputStreamResource(in));
    }
    catch (IOException e)
    {
      e.printStackTrace();
      System.exit(1);
    }

    ace.clearConfigAndPopulateFromFile();

    //rpc.pullFromRunningProfile();

    //HashMap<String, ArrayList<ComparisonResult>> clusterResults = profileComparator.compareClusterCollections(ace.getClusters(), rpc.getClusters());
    //return clusterResults.toString();
    return "";
    //HashMap<String,ArrayList<ComparisonResult>> results = profileComparator.compareApplicationCollections(ace.getApplications(), rpc.getApplications());
    //System.out.println(results);

    // Find a NodeAgent MBean
    //ace.getNodeAgentMBeans();

    // List nodes
    //ace.listNodesInCell();
    //ace.listServersInCell();

    //ace.listBLAsInCell(new String[]{"blaID", "DefaultApplication", "includeDescription", "true"});

    //ace.showServerInfo("Node=VBSTEST_NODE01:Server=BS_APPSRV_01");
    //ace.populateServerProperties("VBSTEST_NODE01","BS_APPSRV_01");

    //ace.showJvmProperties("Node=VBSTEST_NODE01:Server=BS_APPSRV_01");

    //ace.listAllCommand();
    // Invoke launchProcess
    //ace.invokeLaunchProcess("BS_APPSRV_01");

    // Register for NodeAgent events
    //ace.registerNotificationListener();

    // Run until interrupted
    //ace.countNotifications();
  }

  public static String run()
  {
    return main(null);
  }
}
