package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class ServerDefaults extends Document
{
  Document sessionProperties = new Document(), jvmProperties = new Document(), processDefProperties = new Document(), webcontainerProperties = new Document(), logAttribs = new Document(), errlogAttribs = new Document();

  public void addSessionProperty(String name, String value, String description, String validationExpression, boolean required)
  {
    sessionProperties.append(name, new ServerProperty(name, value, description, validationExpression, required));
  }

  public void addSessionProperty(String name, String value)
  {
    sessionProperties.append(name, new ServerProperty(name, value));
  }

  public void addJvmProperty(String name, String value, String description, String validationExpression, boolean required)
  {
    jvmProperties.append(name, new ServerProperty(name, value, description, validationExpression, required));
  }

  public void addJvmProperty(String name, String value)
  {
    jvmProperties.append(name, new ServerProperty(name, value));
  }

  public void addProcessDefProperty(String name, String value, String description, String validationExpression, boolean required)
  {
    processDefProperties.append(name, new ServerProperty(name, value, description, validationExpression, required));
  }

  public void addProcessDefProperty(String name, String value)
  {
    processDefProperties.append(name, new ServerProperty(name, value));
  }

  public void addWebcontainerProperty(String name, String value, String description, String validationExpression, boolean required)
  {
    webcontainerProperties.append(name, new ServerProperty(name, value, description, validationExpression, required));
  }

  public void addWebcontainerProperty(String name, String value)
  {
    webcontainerProperties.append(name, new ServerProperty(name, value));
  }

  public void addLogAttr(String name, String value)
  {
    logAttribs.append(name, new ServerProperty(name, value));
  }

  public void addErrlogAttr(String name, String value)
  {
    errlogAttribs.append(name, new ServerProperty(name, value));
  }
}
