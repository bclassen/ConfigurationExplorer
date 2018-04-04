package com.pcmsgroup.system.config.defaults;

import org.bson.Document;

public class MQDefaults extends Document
{
  Document properties = new Document();
  Document topics = new Document();
  Document queues = new Document();

  public void addTopic(MQTopic mqTopic)
  {
    topics.append(((Document)mqTopic.get("properties")).getString("name"), mqTopic);
    append("topics",topics);
  }

  public void addQueue(MQQueue mqQueue)
  {
    queues.append(((Document)mqQueue.get("properties")).getString("name"), mqQueue);
    append("queues",queues);
  }

  public void setQcf(MQQcf mqQcf)
  {
    append("qcf",mqQcf);
  }

  public void setTcf(MQTcf mqTcf)
  {
    append("tcf",mqTcf);
  }

  public void setActivationSpec(MQActivationSpec mqActivationSpec)
  {
    append("as",mqActivationSpec);
  }

  public void addProperty(String name, String value)
  {
    properties.append(name, value);
  }
}
