/*
 * Copyright (c) PCMS Group plc 2017. All Rights Reserved.
 * This source code is copyright of PCMS Group plc. The information
 * contained herein is proprietary and confidential to PCMS Group plc.
 * This proprietary and confidential information, either in whole or in
 * part, shall not be used for any purpose unless permitted by the terms
 * of a valid license agreement.
 */
package com.pcmsgroup.system.config;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.DBCollection;
import com.mongodb.client.MongoCollection;

/**
 * @author Administrator
 *
 */
public class ProfileConfigComparator
{
  public class ApplicationComparator implements Block<Document>
  {
    private static final String RESULT_ISOLATION_PROFILE = "profile_only";
    private static final String RESULT_ISOLATION_CONFIG = "config_only";
    private static final String RESULT_ISOLATION_BOTH = "both";
    MongoCollection<Document> profileApplications = null;
    private boolean matchFound = false;
    Document matchingProfileDoc = null;
    ArrayList<ComparisonResult> presentInBoth = null;
    ArrayList<ComparisonResult> presentInConfigNotInProfile = null;
    ArrayList<ComparisonResult> presentInProfileNotInConfig = null;

    public Document getMatchingProfileDoc()
    {
      return matchingProfileDoc;
    }

    public void setMatchingProfileDoc(Document matchingProfileDoc)
    {
      this.matchingProfileDoc = matchingProfileDoc;
    }

    public boolean isMatchFound()
    {
      return matchFound;
    }

    public MongoCollection<Document> getProfileApplications()
    {
      return profileApplications;
    }

    public void setProfileApplications(MongoCollection<Document> profileApplications)
    {
      this.profileApplications = profileApplications;
    }

    @Override
    public void apply(Document arg0)
    {
      if (matchFound)
        return;
      else
      {
        matchingProfileDoc = profileApplications.find(eq("name", (String) arg0.get("name"))).first();
        if (matchingProfileDoc != null)
        {
          matchFound = true;

          presentInBoth = new ArrayList<ComparisonResult>();
          presentInConfigNotInProfile = new ArrayList<ComparisonResult>();
          presentInProfileNotInConfig = new ArrayList<ComparisonResult>();
          for (String nextKey : arg0.keySet())
          {
            ComparisonResult nextResult = new ComparisonResult();
            nextResult.setFieldName(nextKey);
            nextResult.setProfileValue(matchingProfileDoc.get(nextKey) != null ? matchingProfileDoc.get(nextKey).toString() : "");
            nextResult.setConfigValue(arg0.get(nextKey) != null ? arg0.get(nextKey).toString() : "");
            if (!nextResult.getConfigValue().equals("") && !nextResult.getProfileValue().equals(""))
              presentInBoth.add(nextResult);
            else if (!nextResult.getConfigValue().equals("") && nextResult.getProfileValue().equals(""))
              presentInConfigNotInProfile.add(nextResult);
            else if (nextResult.getConfigValue().equals("") && !nextResult.getProfileValue().equals(""))
              presentInProfileNotInConfig.add(nextResult);
          }
        }
      }
    }

    public HashMap<String, ArrayList<ComparisonResult>> getComparisonResults()
    {
      HashMap<String, ArrayList<ComparisonResult>> results = new HashMap<String, ArrayList<ComparisonResult>>();
      results.put(RESULT_ISOLATION_BOTH, presentInBoth);
      results.put(RESULT_ISOLATION_CONFIG, presentInConfigNotInProfile);
      results.put(RESULT_ISOLATION_PROFILE, presentInProfileNotInConfig);
      return results;
    }

  }

  public class ClusterComparator implements Block<BasicDBObject>
  {
    DBCollection profileClusters, configServers, profileServers = null;
    private boolean matchFound = false;
    BasicDBObject matchingProfileDoc = null;
    ArrayList<ComparisonResult> presentInBoth = null;
    ArrayList<ComparisonResult> presentInConfigNotInProfile = null;
    ArrayList<ComparisonResult> presentInProfileNotInConfig = null;
    HashMap<String, ArrayList<ComparisonResult>> results = new HashMap<String, ArrayList<ComparisonResult>>();


    public BasicDBObject getMatchingProfileDoc()
    {
      return matchingProfileDoc;
    }

    public void setMatchingProfileDoc(BasicDBObject matchingProfileDoc)
    {
      this.matchingProfileDoc = matchingProfileDoc;
    }

    public boolean isMatchFound()
    {
      return matchFound;
    }

    public DBCollection getProfileApplications()
    {
      return profileClusters;
    }

    public void setProfileClusters(DBCollection profileClusters)
    {
      this.profileClusters = profileClusters;
    }

    public DBCollection getConfigServers()
    {
      return configServers;
    }

    public void setConfigServers(DBCollection configServers)
    {
      this.configServers = configServers;
    }

    public DBCollection getProfileServers()
    {
      return profileServers;
    }

    public void setProfileServers(DBCollection profileServers)
    {
      this.profileServers = profileServers;
    }

    @Override
    public void apply(BasicDBObject configCluster)
    {
      matchingProfileDoc = (BasicDBObject) profileClusters.findOne(new BasicDBObject("Name",(String) configCluster.get("Name")));
      if (matchingProfileDoc != null)
      {
        presentInBoth = new ArrayList<ComparisonResult>();
        presentInConfigNotInProfile = new ArrayList<ComparisonResult>();
        presentInProfileNotInConfig = new ArrayList<ComparisonResult>();
        for (String nextKey : configCluster.keySet())
        {
          ComparisonResult nextResult = new ComparisonResult();
          nextResult.setFieldName(nextKey);
          nextResult.setProfileValue(matchingProfileDoc.get(nextKey) != null ? matchingProfileDoc.get(nextKey).toString() : "");
          nextResult.setConfigValue(configCluster.get(nextKey) != null ? configCluster.get(nextKey).toString() : "");
          if (!nextResult.getConfigValue().equals("") && !nextResult.getProfileValue().equals(""))
            presentInBoth.add(nextResult);
          else if (!nextResult.getConfigValue().equals("") && nextResult.getProfileValue().equals(""))
            presentInConfigNotInProfile.add(nextResult);
          else if (nextResult.getConfigValue().equals("") && !nextResult.getProfileValue().equals(""))
            presentInProfileNotInConfig.add(nextResult);
        }
        results.put(configCluster.getString("Name"), presentInConfigNotInProfile);
      }
    }

    public HashMap<String, ArrayList<ComparisonResult>> getComparisonResults()
    {
      return results;
    }

  }

  public class ServerComparator implements Block<BasicDBObject>
  {
    BasicDBObject serverA = null;
    StringBuilder results = new StringBuilder();

    public String getResults()
    {
      return results.toString();
    }

    public BasicDBObject getServerA()
    {
      return serverA;
    }

    public void setServerA(BasicDBObject serverDoc)
    {
      this.serverA = serverDoc;
    }

    @Override
    public void apply(BasicDBObject serverB)
    {
      Set<String> intersectionOfAandBKeys = serverB.keySet().stream().filter(s -> serverA.keySet().contains(s)).collect(Collectors.toSet());
      Set<String> BOnlyKeys = serverB.keySet().stream().filter(s -> !serverA.keySet().contains(s)).collect(Collectors.toSet());

      results.append("These server properties are only found on server B:\n");
      for (String nextKey : BOnlyKeys)
      {
        String propValue = serverB.getString(nextKey);
        results.append("Server B: " + serverB.getString("Name") + "." + nextKey + " = " + propValue + "\n");
      }

      results.append("These server properties are found on both servers but don't match:\n");
      for (String nextKey : intersectionOfAandBKeys)
      {
        String propValue = serverB.getString(nextKey);
        String propAValue = serverA.getString(nextKey);
        if (!propValue.equals(propAValue))
        {
          results.append("Server A: " + serverA.getString("Name") + "." + nextKey + " = " + propAValue + "\n");
          results.append("Server B: " + serverB.getString("Name") + "." + nextKey + " = " + propValue + "\n");
        }
      }
    }
  }

  public HashMap<String, ArrayList<ComparisonResult>> compareApplicationCollections(MongoCollection<Document> configApplications, MongoCollection<Document> profileApplications)
  {
    HashMap<String, ArrayList<ComparisonResult>> results = null;

    ApplicationComparator compareAction = new ApplicationComparator();
    compareAction.setProfileApplications(profileApplications);
    configApplications.find().forEach(compareAction);

    results = compareAction.getComparisonResults();

    return results;
  }

  public HashMap<String, ArrayList<ComparisonResult>> compareClusterCollections(DBCollection configClusters, DBCollection profileClusters)
  {
    HashMap<String, ArrayList<ComparisonResult>> results = null;

    ClusterComparator compareAction = new ClusterComparator();
    compareAction.setProfileClusters(profileClusters);
    //configClusters.find().forEach(compareAction);

    results = compareAction.getComparisonResults();

    return results;
  }
}
