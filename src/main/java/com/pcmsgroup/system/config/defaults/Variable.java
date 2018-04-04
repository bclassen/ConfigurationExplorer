package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class Variable extends Document
{
  public Variable(String cluster, String node, String server, String name, String value, String description)
  {
    append("cluster", cluster);
    append("node", node);
    append("server", server);
    append("name", name);
    append("value", value);
    append("description", description);
  }
}
