package com.pcmsgroup.system.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class ManagementClient
{
  protected Resource prefixTypes = new ClassPathResource("prefixTypes.json");
  protected Resource prefixPropertyTypes = new ClassPathResource("prefixPropertyTypes.json");
  protected Resource serverPropertyTypes = new ClassPathResource("serverPropertyTypes.json");
  protected Resource nodePropertyTypes = new ClassPathResource("nodePropertyTypes.json");
  protected Resource clusterPropertyTypes = new ClassPathResource("clusterPropertyTypes.json");
  protected Resource applicationPropertyTypes = new ClassPathResource("applicationPropertyTypes.json");
  protected DBCollection prefixTypeCollection, clusterPropertyTypeCollection, prefixPropertyTypeCollection, serverPropertyTypeCollection, applicationPropertyTypeCollection, nodePropertyTypeCollection;

  protected DB database;

  protected void prepopulatePrefixTypes() throws IOException
  {
    prefixTypeCollection = database.getCollection("prefixType");
    String json = "";
    if (prefixTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(prefixTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    prefixTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected void prepopulatePrefixPropertyTypes() throws IOException
  {
    prefixPropertyTypeCollection = database.getCollection("prefixPropertyType");
    String json = "";
    if (prefixPropertyTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(prefixPropertyTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    prefixPropertyTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected void prepopulateServerPropertyTypes() throws IOException
  {
    serverPropertyTypeCollection = database.getCollection("serverPropertyType");
    String json = "";
    if (serverPropertyTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(serverPropertyTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    serverPropertyTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected void prepopulateApplicationPropertyTypes() throws IOException
  {
    applicationPropertyTypeCollection = database.getCollection("applicationPropertyType");
    String json = "";
    if (applicationPropertyTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(applicationPropertyTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    applicationPropertyTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected void prepopulateNodePropertyTypes() throws IOException
  {
    nodePropertyTypeCollection = database.getCollection("nodePropertyType");
    String json = "";
    if (nodePropertyTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(nodePropertyTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    nodePropertyTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected void prepopulateClusterPropertyTypes() throws IOException
  {
    clusterPropertyTypeCollection = database.getCollection("clusterPropertyType");
    String json = "";
    if (clusterPropertyTypes == null)
    {
      System.out.println("Unable to find prefixTypes on the classpath");
      System.exit(1);
    }
    else
    {
      try
      {
        json = IOUtils.toString(clusterPropertyTypes.getInputStream(), "UTF-8");
      }
      catch (IOException e)
      {
        e.printStackTrace();
        System.exit(1);
      }
    }

    BasicDBObject jsonDoc = BasicDBObject.parse(json);
    clusterPropertyTypeCollection.insert((List<DBObject>) jsonDoc.get("types"));
  }

  protected String convertFromDotToCamel(String stringToConvert)
  {
    String convertedString = stringToConvert;
    int index = stringToConvert.indexOf(".");
    while (index > -1)
    {
      convertedString = stringToConvert.substring(0, index);
      stringToConvert = stringToConvert.substring(index + 1);
      char firstChar = stringToConvert.charAt(0);
      convertedString += Character.toUpperCase(firstChar);
      convertedString += stringToConvert.substring(1);
      stringToConvert = convertedString;
      index = stringToConvert.indexOf(".");
    }

    return convertedString;
  }

  protected String mapProfileNameToConfigName(String key, String subtype, DBCollection propertyTypeCollection)
  {
    String configName = key;
    if (subtype.equals("root"))
    {
      DBObject rootDocument = propertyTypeCollection.findOne(new BasicDBObject("profileName", key));
      if (rootDocument != null)
        configName = (String)rootDocument.get("name");
    }
    else
    {
      DBObject matchingDocument = propertyTypeCollection.findOne(new BasicDBObject("name", subtype));
      ArrayList<DBObject> subtypes = (ArrayList<DBObject>)matchingDocument.get("subtypes");

      for (DBObject subtypeObj : subtypes)
      {
        String profileName = (String)subtypeObj.get("profileName");
        if (profileName != null && profileName.equals(key))
        {
          configName = (String)subtypeObj.get("name");
        }
      }
    }
    return configName;
  }

  protected String mapConfigNameToProfileName(String key, String subtype, DBCollection propertyTypeCollection)
  {
    String profileName = key;
    DBObject matchingDocument = propertyTypeCollection.findOne(new BasicDBObject("name", subtype));
    ArrayList<DBObject> subtypes = (ArrayList<DBObject>)matchingDocument.get("subtypes");
    for (DBObject subtypeObj : subtypes)
    {
      String configName = (String)subtypeObj.get("name");
      if (configName != null && configName.equals(key))
      {
        profileName = (String)subtypeObj.get("profileName");
      }
    }
    return profileName;
  }


}