package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class JDBCProviderDefaults extends Document
{
  public void addJdbcProvider(JDBCProvider provider)
  {
    append(((Document)provider.get("properties")).getString("name"), provider);
  }
}
