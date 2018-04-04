package com.pcmsgroup.system.config.defaults;

import java.util.Properties;

import org.bson.Document;

public class MQActivationSpec extends Document
{
  Document properties = new Document();
  Document specialProperties = new Document();

  public void addProperty(String nextKey, String propValue)
  {
    properties.append(nextKey, propValue);
    append("properties",properties);
  }

  public void addSpecialProperty(String nextKey, String propValue)
  {
    specialProperties.append(nextKey,propValue);
    append("specialProperties",specialProperties);
  }

}
