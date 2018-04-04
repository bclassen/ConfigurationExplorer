package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class JDBCDatasource extends Document
{
  Document properties = new Document();
  Document customProperties = new Document();

  public void addProperty(String nextProp, String propValue)
  {
    properties.append(nextProp, propValue);
  }

  public void addCustomProperty(String nextKey, String propValue)
  {
    customProperties.append(nextKey, propValue);
  }
}
