package com.pcmsgroup.system.config.model;

public class ComparisonDocument
{
  Object name, configValue, profileValue;

  public void setName(Object name)
  {
    this.name = name;
  }

  public void setConfigValue(Object configValue)
  {
    this.configValue = configValue;
  }

  public void setProfileValue(Object profileValue)
  {
    this.profileValue = profileValue;
  }

  public Object getName()
  {
    return name;
  }

  public Object getConfigValue()
  {
    return configValue;
  }

  public Object getProfileValue()
  {
    return profileValue;
  }

}
