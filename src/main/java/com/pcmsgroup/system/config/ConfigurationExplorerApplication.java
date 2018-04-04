package com.pcmsgroup.system.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootApplication
@EnableAutoConfiguration(exclude =
{
  JmxAutoConfiguration.class
})
public class ConfigurationExplorerApplication
{
  @RequestMapping("/")
  @ResponseBody
  String home()
  {
    return ConfigurationManager.run();
  }

  public static void main(String[] args)
  {
    SpringApplication.run(ConfigurationExplorerApplication.class, args);
  }
}
