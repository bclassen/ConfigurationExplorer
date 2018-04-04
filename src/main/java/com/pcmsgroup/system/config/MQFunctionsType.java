package com.pcmsgroup.system.config;

public interface MQFunctionsType
{
  public int createMQConnectionFactory(String name, String type, String jndiname, String description, String cluster,
      String node, String server, String ccdt, String ccdtQmgrName, String clientId, String[] connPool, String[] sessionPool);
  public int updateMQQueueConnectionFactory(String name, String jndiname, String description, String cluster, String node,
      String server, String ccdt, String ccdtQmgrName, String connPool);
  public int deleteMQConnectionFactory(String name, String cluster, String node, String server);
  public String listMQQueueConnectionFactories(String cell, String cluster, String node, String server, Integer displayFlag);
  public int listMQTopicConnectionFactories(String cell, String cluster, String node, String cserver, int displayFlag);
  public int createMQQueue(String name, String jndiname, String queueName, String cluster, String node, String server, String attrs);
  public int updateMQQueue(String name, String jndiname, String queueName, String cluster, String node, String server, String attrs);
  public int deleteMQQueue(String name, String cluster, String node, String server);
  public int listMQQueues(String cell, String cluster, String node, String server, int displayFlag);
  public int createMQTopic(String name, String jndiname, String queueName, String cluster, String node, String server, String attrs);
  public int deleteMQTopic(String name, String cluster, String node, String server);
  public int listMQTopics(String cell, String cluster, String node, String server, int displayFlag);
  public int createMQActivationSpec(String cluster, String node, String server, String name, String jndiName, String queueJndi, String ccdtUrl, String ccdtQmgrName, int failureDeliveryCount, String props);
  public int updateMQActivationSpec(String cluster, String node, String server, String name, String jndiName, String queueJndi, String ccdtUrl, String ccdtQmgrName, int failureDeliveryCount);
  public int deleteMQActivationSpec(String cluster, String node, String server, String name);
  public void initialise();
}
