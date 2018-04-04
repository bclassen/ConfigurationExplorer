package com.pcmsgroup.system.config;

public class ComparisonResult
{
  @Override
  public String toString()
  {
    StringBuilder bldr = new StringBuilder();
    bldr.append("{\n");
    bldr.append("\"fieldName\": \"" + fieldName + "\"");
    bldr.append(",\n");
    bldr.append("\"configValue\": \"" + configValue + "\"");
    bldr.append(",\n");
    bldr.append("\"profileValue\": \"" + profileValue + "\"");
    bldr.append("}\n");
    return bldr.toString();
  }

  private String fieldName;
  private String configValue;
  private String profileValue;

  public String getProfileValue()
  {
    return profileValue;
  }

  public void setProfileValue(String profileValue)
  {
    this.profileValue = profileValue;
  }

  public String getConfigValue()
  {
    return configValue;
  }

  public void setConfigValue(String configValue)
  {
    this.configValue = configValue;
  }

  public String getFieldName()
  {
    return fieldName;
  }

  public void setFieldName(String fieldName)
  {
    this.fieldName = fieldName;
  }
}
