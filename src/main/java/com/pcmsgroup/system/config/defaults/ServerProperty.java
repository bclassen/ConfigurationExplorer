package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class ServerProperty extends Document
{
  public ServerProperty(String name, String value)
  {
    append("name", name);
    append("value", value);
  }

  public ServerProperty(String name, String value, String description, String validationExpression, boolean required)
  {
    append("name", name);
    append("value", value);
    append("description", description);
    append("validationExpression", validationExpression);
    append("required", required);
  }
}
