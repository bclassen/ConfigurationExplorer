package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class JaasDefaults extends Document
{
  public void addJaasId(String name, String username, String password, String description)
  {
    append(name,new JaasId(name, username, password, description));
  }
}
