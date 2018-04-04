package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class JDBCProvider extends Document
{
  Document properties = new Document();
  Document datasources = new Document();

  public void addDatasource(JDBCDatasource datasource)
  {
    datasources.append(datasource.getString("name"), datasource);
    append("datasources",datasources);
  }

  public void addProperty(String nextKey, String nextValue)
  {
    properties.append(nextKey, nextValue);
    append("properties",properties);
  }
}
