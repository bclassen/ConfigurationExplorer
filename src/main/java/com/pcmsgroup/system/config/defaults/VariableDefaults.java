package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class VariableDefaults extends Document
{
  public void addVariable(String cluster, String node, String server, String name, String value, String description)
  {
    append(name, new Variable(cluster, node, server, name, value, description));
  }
}
