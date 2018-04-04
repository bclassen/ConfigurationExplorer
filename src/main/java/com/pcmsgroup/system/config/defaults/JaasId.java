package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class JaasId extends Document
{
  public JaasId(String name, String username, String password, String description)
  {
    append("name",name);
    append("username", username);
    append("password", password);
    append("description", description);
  }
}
