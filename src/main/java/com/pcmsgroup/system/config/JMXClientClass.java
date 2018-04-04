package com.pcmsgroup.system.config;

import java.io.File;
import java.io.FileWriter;
import java.io.StringBufferInputStream;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/*
 * You can import the following classes from the
 * com.ibm.ws.runtime_6.1.0.jar available with
 * the IBM Software Development Platform.
 *
 *  The these classes are documented here:
 *  http://publib.boulder.ibm.com/infocenter/wasinfo/v7r0/index.jsp?_
    * topic=/com.ibm.websphere.javadoc.doc/web/mbeanDocs/index.html
 */
import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.wlm.ClusterMemberData;
import com.ibm.ws.exception.WsException;


public class JMXClientClass {

    private AdminClient adminClient = null;

    public void connectToWAS() throws WsException {

        java.util.Properties props = new java.util.Properties();
        props.setProperty(AdminClient.CONNECTOR_TYPE, AdminClient.CONNECTOR_TYPE_SOAP);
        props.setProperty(AdminClient.CONNECTOR_HOST, "172.17.0.136");
        props.setProperty(AdminClient.CONNECTOR_PORT, "10003");

        adminClient = AdminClientFactory.createAdminClient(props);

    }

    public void getClusterMemberBootStraps() {

        try {

            /*
             * This method will write the bootstrap port numbers and servers
             * for the cluster on which MDM is running to file. This file
             * can be retrieved and updated each time a client application
             * accesses MDM.  With this approach, the client application can
             * have a continuously updated list of cluster members it can
             * access when retrieving the EJB Home interface for MDM
             */
            File file = new File("C:/MDM/output/MDMCluster.txt");
            FileWriter fileWriter = new  FileWriter(file);

            /*
             * We must build an XML Document containing the topology for Cell and
             * Cluster in which MDM is installed.  Here is a function we've written
             * to do that.
             * We'll use this XML document to get the host and node information.
             */
            Document DOM = getTargetTreeMBean();


            /*
             * First we must establish a connection to MBean server on the
             * WAS deployment manager
             */
            if (adminClient == null) {
                this.connectToWAS();
            }

            /*
             * The Various MBeans are documented here:
             * http://publib.boulder.ibm.com/infocenter/wasinfo/v7r0/index.jsp?
                                        topic=/com.ibm.websphere.javadoc.doc/web/mbeanDocs/index.html
             * The first task is to query the MBean server and to find the
             * MBean for our Cluster.  Our hypothetical cluster is named "c1".
             * The WAS API (i.e. adminClient) will return a SET of clusters
             * matching our search criteria.  Most likely it will be a set of one.
             */
            ObjectName clusterQueryName = new ObjectName("WebSphere:*,type=Cluster");
            Set<ObjectName> clusters = adminClient.queryNames(clusterQueryName, null);

            String memberName = null;
            String nodeName = null;
            String hostName = null;
            int bootStrapPortNum = 0;
            String outputString = null;


            for (ObjectName objCluster : clusters) {  // We iterate through the set of clusters
                System.out.println(objCluster.getCanonicalName());
                ClusterMemberData[] obj = (ClusterMemberData[])adminClient
                                .invoke(objCluster, "getClusterMembers", null, null);
                /*
                 *  For details on this API method please see:
                 *  http://publib.boulder.ibm.com/infocenter/wasinfo/v7r0/index.jsp?topic=/com.ibm.websphere.javadoc.doc/web/mbeanDocs/index.html
                 *                   */
                // We are going to store a vector of the nodes in which each cluster member resides
                Vector<String> nodes = new Vector<String>();


                for (int i = 0; i < obj.length; i++) {
                    memberName = obj[i].memberName;
                    nodeName = obj[i].nodeName;
                    hostName = getHostNameforNode(nodeName, DOM);
                    if (!nodes.contains(nodeName)) {
                        // Storing node name in the vector of node names
                        nodes.add(nodeName);
                    }
                    /*
                     * Having got the Server names and node names of the cluster members, we can now
                     * make another call to the WAS API (i.e. adminClient) to retrieve the Object
                     * Request Brokers for each.  Each ORB has a property called "bootStrapPort".
                     * We'll retrieve that property as the bootstrap port number we are seeking
                     */
                    String orbQuery =
                        String.format("WebSphere:*,type=ORB,j2eeType=RMI_IIOPResource,J2EEServer=%1$s,node=%2$s",
                                        memberName, nodeName);
                    Set<ObjectName> orbSet = adminClient.queryNames(new ObjectName(orbQuery),null);
                    bootStrapPortNum = 0;
                    for (ObjectName orbObj : orbSet) {
                        bootStrapPortNum = (Integer)adminClient.getAttribute(orbObj, "bootstrapPort");
                        outputString = String.format("Node=%1$s,Server=%2$s,BootStrap=%3$s,HostName=%4$s",
                                nodeName, memberName, bootStrapPortNum, hostName) + "\n";
                        fileWriter.write(outputString);
                    }
                }

                /*
                 * Now get all the bootstrap port numbers for the nodeagents
                 */
                for (String strNodeName : nodes) {
                    String orbQuery =
                        String.format("WebSphere:*,type=ORB,j2eeType=RMI_IIOPResource,J2EEServer=%1$s,node=%2$s",
                                "nodeagent", strNodeName);
                    hostName = getHostNameforNode(strNodeName, DOM);
                    Set<ObjectName> orbSet = adminClient.queryNames(new ObjectName(orbQuery), null);
                    for (ObjectName orbObj : orbSet) {
                        bootStrapPortNum = (Integer)adminClient.getAttribute(orbObj, "bootstrapPort");
                        outputString = String.format("Node=%1$s,BootStrap=%2$s,HostName=%3$s",
                                        strNodeName, bootStrapPortNum, hostName) + "\n";
                        fileWriter.write(outputString);
                    }
                }
                fileWriter.flush();
                fileWriter.close();

            }


        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private String getHostNameforNode(String nodeName, Document DOM) {
        // TODO Auto-generated method stub

        String xPathQuery =
                String.format("//node[@name='%1$s']/property[@name='hostName']/@value", nodeName);
        String hostNameforNode = this.executeXPATHQuery(DOM, xPathQuery);

        return hostNameforNode;
    }

    public Document getTargetTreeMBean() {

        Document DOM = null;
        try {
            if (adminClient == null) {
                this.connectToWAS();
            }

            ObjectName endPointMgr = new ObjectName("WebSphere:*,type=TargetTreeMbean,process=dmgr");
            Set<ObjectName> endPointMgrSet = adminClient.queryNames(endPointMgr, null);
            int i = 0;
            for (ObjectName objName : endPointMgrSet) {
                System.out.println(objName.getCanonicalName());

                DOM = this.getDOMFromString(
                    (String)adminClient.invoke(objName, "getTargetTree", null, null));
                break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return DOM;
    }

    @SuppressWarnings("deprecation")
    public Document getDOMFromString(String theRequest) throws Exception {

        StringBufferInputStream sb = new StringBufferInputStream(theRequest);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(sb);

        return dom;

    }

    public String executeXPATHQuery(Document DOM, String xPathQuery) {

        String nodeValues = "";
        try {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            XPathExpression expr;
            expr = xpath.compile(xPathQuery);
            Object result = expr.evaluate(DOM, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for (int j = 0; j < nodes.getLength(); j++) {
                if (nodeValues.length() > 0) {
                    nodeValues = nodeValues + ",";
                }
                nodeValues = nodeValues + nodes.item(j).getNodeValue();
            }
        } catch (XPathExpressionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return nodeValues;

    }

}