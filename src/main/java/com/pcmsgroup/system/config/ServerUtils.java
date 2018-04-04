package com.pcmsgroup.system.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerUtils
{

  public static void main(String[] args)
  {
    System.out.println("Hostname = " + getServerHostName());
  }

  public static String getServerHostName()
  {
    try
    {
      return InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).getHostName();
    }
    catch (UnknownHostException e)
    {
      e.printStackTrace();
    }

    return "localhost";
  }
}
