package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class MQTcf extends Document
{
  Document properties = new Document();

  public void addProperty(String nextKey, String propValue)
  {
    properties.append(nextKey, propValue);
    append("properties",properties);
  }

}
