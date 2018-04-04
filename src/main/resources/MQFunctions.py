from com.pcmsgroup.system.config import MQFunctionsType

##########################################################################
# File Name:    MQFunctions.py
# Description:  This file contains function definitions to create, delete
#               and list MQ components
#                    - Connection Factories
#                    - Queue Connection Factories
#                    - TopicConnectionFactories
#                    - Queues
#                    - Topics
#
# Function List:
#    createMQQueueConnectionFactory
#    deleteMQConnectionFactory
#    listMQQueueConnectionFactories
#    createMQTopicConnectionFactory
#    deleteMQTopicConnectionFactory
#    listMQTopicConnectionFactories
#    addMQQueue
#    deleteMQQueue
#    listMQQueues
#    addMQTopic
#    deleteMQTopic
#    listMQTopics
#
#
##########################################################################
class MQFunctions(MQFunctionsType):

    ##########################################################################
    # File Name:    Utils.py
    # Description:  This file contains common function definitions for the
    #               scripting framework
    #
    #               _app_message
    #               _app_trace
    #               configSave
    #               funcArgs
    #               getContainmentPath
    #               getWebSphereVariableMapID
    #               initialise
    #               isEmpty
    #               modifyObject
    #               objectExists
    #               searchReplaceFile
    #               tron
    #               troff
    #               trlogon
    #               trlogoff
    #               trlogclear
    #
    ##########################################################################


    ##########################################################################
    #
    # FUNCTIONS:
    #    _app_message
    #    _app_trace:
    #
    #    Print out trace information
    #
    # SYNTAX:
    #    _app_message (args)
    #    _app_trace   (type, args, force)
    #
    # PARAMETERS:
    #
    #    type   - [entry|exit|debug] referring to type of trace message
    #         default = debug
    #    args       - a string to be printed as a helpful trace message
    #    force  - force display even if trace is off (1 = force; 0 = no force)
    #
    # USAGE NOTES:
    #    1. Call _app_trace at the beginning and end of every procedure.
    #    2. Call _app_trace at processing points that you think are significant.
    #    3. Turn the facility on at runtime by setting the property
    #       configInfo["trace"] = 1 in a properties file and including the -p
    #       option on starting wsadmin.
    #    4. Use _app_message to force output to the console regardless of trace settings
    #
    # RETURNS:
    #    N/A
    #
    # IMPLEMENTATION:
    #    1. Display the parameter string.
    #
    ##########################################################################
    def _app_message(args):
        _app_trace(args, "none", 1)

    def _app_trace(self, args, type="debug", force = 0):
        import sys

        global configInfo
        ltype = type.lower()

        #   If tracing is enabled then print out args
        if force == 1 or configInfo["config.trace.level"] != 0:

            if ltype not in ("entry", "exit", "debug", "exception", "none"):
                print "Bad 1st argument to _app_trace(); expected entry|exit|debug|exception|none"
                return

            from java.util import Date
            from java.text import SimpleDateFormat

            now = Date()
            sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss:SSS z")
            tnow = "[%s]" % (sdf.format(now))

            tabs = int(configInfo["config.trace.spaces"])

            if ltype == "entry":
                printStr = "%*s%s > %s" % (configInfo["config.trace.current"], " ", tnow, args)
                configInfo["config.trace.current"] = configInfo["config.trace.current"] + tabs
            elif ltype == "exit":
                if configInfo["config.trace.current"] >= tabs:
                    configInfo["config.trace.current"] = configInfo["config.trace.current"] - tabs
                printStr = "%*s%s < %s" % (configInfo["config.trace.current"], " ", tnow, args)
            elif ltype == "debug":
                printStr = "%*s%s (d) %s" % (configInfo["config.trace.current"], " ", tnow, args)
            elif ltype == "none":
                printStr = "%s %s" % (tnow, args)
            elif ltype == "exception":

                #AdminConfig.reset()
                printStr =  "%*s%s (E) %s" % (configInfo["config.trace.current"], " ", tnow, args)

                i = 1;

                for exc in sys.exc_info():
                    printStr += "\n(%d): %s" % (i, exc)
                    i = i + 1

            writeStr = "%s%s" % (printStr, configInfo["line.separator"])

            configInfo["app.trace.tracelogfp"].write(writeStr)
            configInfo["app.trace.tracelogfp"].flush()
            if force == 1:
                print printStr



    ##########################################################################
    #
    # FUNCTION:
    #    funcArgs:  Displays function arguments for givenj function
    #
    # SYNTAX:
    #    funcArgs(name)
    #
    # PARAMETERS:
    #
    #    name               -   Function name
    #
    # RETURNS:
    #       List of function arguments
    #
    ##########################################################################
    def funcArgs(name):

        try:
            args  = "%s.func_code.co_varnames" % (name)
            argsc = "%s.func_code.co_argcount" % (name)

            argv = eval(args)
            argc = eval(argsc)

            argList = []

            for i in range(argc):
                argList.append(argv[i])

            return argList
        except:
            _app_trace("Error getting arg list for " + name)

    ##########################################################################
    #
    # FUNCTION:
    #    getContainmentPath: Checks that either a cluster or node/server is
    #           passed and returns containment path suitable
    #           for AdminConfig.getid()
    #  Combinations
    #  ============
    #  C   Cl  N   S   Valid?
    #  -----------------
    #  N   N   N   N    No
    #  N   N   Y   N    No
    #  N   Y   N   N    No
    #  N   Y   Y   Y    No
    #  Y   N   N   N    Yes - Cell Containment Path
    #  Y   Y   N   N    Yes - Cell & Cluster Containment Path
    #  Y   Y   Y   N    No
    #  Y   Y   Y   Y    No
    #  Y   N   Y   N    Yes - Cell & Node Containment Path
    #  Y   N   N   Y    No
    #  Y   N   Y   Y    Yes - Cell, Node & Server Containment Path
    #
    #
    # SYNTAX:
    #    getContainmentPath(cell, cluster, node, server)
    #
    # PARAMETERS:
    #   cell        - Name of Cell
    #   cluster     - Name of cluster or None if using node/server
    #   node        - Name of node or None
    #   server      - Name of server or None if using cluster
    #
    # USAGE:
    #
    #    Use where a function needs the containment path for a Scoped Object.
    #    This returns the path to be used by
    #    AdminConfig.getid(containment path) or objectExists() in this module
    #
    # RETURNS:
    #   containment path string or "none" on error
    #
    ##########################################################################

    def getContainmentPath(cell, cluster, node, server):
        _app_trace("getContainmentPath(%s, %s, %s, %s)" % (cell, cluster, node, server), "entry")
        retval = "none"
        if isEmpty(cell):
            retval =  "None"
            _app_trace("Must specify cell name")
        else:
            if isEmpty(cluster) and isEmpty(node) and isEmpty(server):
                retval = "/Cell:%s/" % (cell)
            elif not isEmpty(cluster) and isEmpty(node) and isEmpty(server):
                retval = "/ServerCluster:%s/" % (cluster)
            elif isEmpty(cluster) and not isEmpty(node) and isEmpty(server):
                retval = "/Cell:%s/Node:%s/" % (cell, node)
            elif isEmpty(cluster) and not isEmpty(node) and not isEmpty(server):
                retval = "/Cell:%s/Node:%s/Server:%s/" % (cell, node, server)
            else:
                retval = "none"

        _app_trace("getContainmentPath(%s)" % (retval), "exit")
        return retval






    ##########################################################################
    #
    # FUNCTION:
    #    initialise: Performs start-up initialisation.
    #
    # SYNTAX:
    #    initialise ()
    #
    # PARAMETERS:
    #    N/A
    #
    # USAGE NOTES:
    #    Call this at the start of the program.  Place all initialisation
    #    of global items here (essentially that means configInfo).
    #
    # RETURNS:
    #    N/A
    #
    # IMPLEMENTATION:
    #    1. Set up globals.
    #
    ##########################################################################
    def initialise(self):
        import sys
        from java.lang import System
        from java.net import InetAddress
        from java.util import Properties
        from java.io import FileInputStream
        print "Initialise "
        try:
            global configInfo

            if configInfo is None:
                configInfo = {}
        except NameError:
                configInfo = {}

        if len(configInfo) == 0:
            try:
                #   Get java system properties - cannot determine trace level until this time
                props = System.getProperties()
                names = props.propertyNames()

                for name in names:
                    if not isEmpty(name):
                        configInfo[name] = System.getProperty(name)

          # Read trace settings from config.properties
                configInfo["config.trace.log"] = int(configInfo["config.trace.log"])
                configInfo["config.trace.log.dir"] = "%s/%s" % (java.lang.System.getenv("HOME_DIR"), configInfo["config.trace.log.dir"])

                trace = configInfo["config.trace.level"]
                tabs  = configInfo["config.trace.spaces"]
                #log   = configInfo["config.trace.log"]
                log = 1

                if trace is not None:
                    configInfo["config.trace.level"] = trace
                    configInfo["config.trace.spaces"] = int(tabs)
                    configInfo["config.trace.current"] = 0
                else:
                    configInfo["config.trace.level"] = 0
                    configInfo["config.trace.spaces"] = 0
                    configInfo["config.trace.current"] = 0

                #   If logging trace, open file
                if log == 1:
                    trlogon()

                _app_trace("initialise()", "entry")

                #   Get program arguments and count
                configInfo["argv"] = sys.argv
                configInfo["argc"] = len(sys.argv)


                configInfo["hostip"] = InetAddress.getByName(configInfo["com.ibm.ws.scripting.host"])

                _app_trace("host IP     = %s" % (configInfo["hostip"]))
                _app_trace("username    = %s" % (configInfo["user.name"]))

                _app_trace("initialise()", "exit")
            except:
                #   Do not call _app_trace, all variables it requires may not be initialised
                print "Unknown error during initialise()"

                i = 1;

                for exc in sys.exc_info():
                    print "(%d): %s" % (i, exc)
                    i = i + 1

    ##########################################################################
    #
    # FUNCTION:
    #    isEmpty: Checks if variable is empty or None
    #
    # SYNTAX:
    #    isEmpty(variable)
    #
    # PARAMETERS:
    #
    #   variable - Name of variable to validate
    #
    # RETURNS:
    #   1:  variable = "" or None
    #   0:  variable != "" and != None
    #
    ##########################################################################
    def isEmpty(var):
        #_app_trace("isEmpty(%s)" % (var), "entry")
        retval = (var == "" or var == "none" or var == "None" or var == None or var == "[]" or var == [''])
        #_app_trace("isEmpty(%d)" % (retval), "exit")

        return retval




    ##########################################################################
    #
    # FUNCTION:
    #    modifyObject: Modify the attributes of a given object using AdminConfig
    #
    # SYNTAX:
    #    modifyObject(objid, attributes)
    #
    # PARAMETERS:
    #
    #    objid  - Object ID (usu. retrieved via AdminConfig.getid)
    #    attributes - List of attributes in format recognised by AdminConfig.modify
    #
    # RETURNS:
    #       0:  Success
    #   1:  Failure
    #
    ##########################################################################
    def modifyObject(objid, attributes):

        retval = 1

        try:
            _app_trace("modifyObject(%s, %s)" % (objid, attributes), "entry")

            _app_trace("Running command: AdminConfig.modify(%s, %s)" % (objid, attributes))
            AdminConfig.modify(objid, attributes)

            retval = 0

        except:
            _app_trace("An error was encountered modifying object attributes", "exception")
            retval = 1

        _app_trace("modifyObject(%d)" % (retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    objectExists: Determines existence of object
    #
    # SYNTAX:
    #    objectExists (type, parent, name)
    #
    # PARAMETERS:
    #
    #   type    - Config object type, e.g. JDBCProvider
    #         See AdminConfig.types()
    #   parent  - Object's parent as containment path
    #         e.g. /Cluster:_CLUSTER/
    #   name    - Name of object
    #
    # USAGE NOTES:
    #    1. Call this to ensure an object doesn't exist before creating it
    #   or an object does exist before querying, modifying or deleting it
    #
    # RETURNS:
    #       1:  object exists
    #   0:  object does not exist
    #
    ##########################################################################
    def objectExists (type, parent, name):

        retval = 1

        try:
            _app_trace("objectExists(%s, %s, %s)" % (type, parent, name), "entry")

            if isEmpty(type) or isEmpty(name):
                _app_trace("type and name MUST contain non-empty values")
                retval = 0
            else:
                #   Get the ID for this object
                if isEmpty(parent):
                    cpath = "/%s:%s/" % (type, name)
                else:
                    cpath = "%s%s:%s/" % (parent, type, name)

                _app_trace("getid for " + cpath)

                objid = AdminConfig.getid(cpath)

                if isEmpty(objid):
                    _app_trace("Cannot get Object ID for %s" % (cpath))
                    retval = 0

        except:
            _app_trace("An error was encountered checking object exists", "exception")
            retval = 0

        _app_trace("objectExists(%d)" % (retval), "exit")
        return retval

    ##########################################################################
    #
    # FUNCTION:
    #    getObject: Determines existence of object
    #
    # SYNTAX:
    #    getObject (type, parent, name)
    #
    # PARAMETERS:
    #
    #   type    - Config object type, e.g. JDBCProvider
    #         See AdminConfig.types()
    #   parent  - Object's parent as containment path
    #         e.g. /Cluster:_CLUSTER/
    #   name    - Name of object
    #
    # USAGE NOTES:
    #    1. Call this to ensure an object doesn't exist before creating it
    #   or an object does exist before querying, modifying or deleting it
    #
    # RETURNS:
    #       objectid:   object exists
    #   None    :   object does not exist
    #
    ##########################################################################
    def getObject(type, parent, name):

        retval = None

        try:
            _app_trace("getObject(%s, %s, %s)" % (type, parent, name), "entry")

            if isEmpty(type) or isEmpty(name):
                _app_trace("type and name MUST contain non-empty values")
                retval = 0
            else:
                #   Get the ID for this object
                if isEmpty(parent):
                    cpath = "/%s:%s/" % (type, name)
                else:
                    cpath = "%s%s:%s/" % (parent, type, name)

                _app_trace("getid for " + cpath)

                retval = AdminConfig.getid(cpath)

                if isEmpty(objid):
                    _app_trace("Cannot get Object ID for %s" % (cpath))
                    retval = None

        except:
            _app_trace("An error was encountered checking object exists", "exception")
            retval = 0

        _app_trace("getObject(%s)" % (retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #   searchReplaceFile:  Search/replace within file
    #
    # SYNTAX:
    #   searchReplaceFile (file, s_token, d_token)
    #
    # PARAMETERS:
    #
    #   file        -   Name of file to perform search/replace in
    #   s_token -   String to search for
    #   d_token -   Replacement string
    #
    #
    # USAGE NOTES:
    #     1.    This function will overwrite the orginal file with the
    #       replacement string, it will not create a backup
    #
    # RETURNS:
    #       N/A
    #
    ##########################################################################
    def searchReplaceFile(file, s_token, d_token):

        fp = open(file, "r")
        buff = fp.read()
        fp.close()

        buff2 = buff.replace(s_token, d_token)

        fp = open(file, "w")
        fp.write(buff2)
        fp.close()

    ##########################################################################
    #
    # FUNCTIONS:
    #    tron, troff, trlogon, trlogoff, trlogclear:    Trace functions
    #
    # SYNTAX:
    #    tron()     :   Turn tracing on (outputs to console by default)
    #    troff()            Turn tracing off
    #    trlogon()          Turn tracing to file on
    #    trlogoff()         Turn tracing to file off
    #    trlogclearon()     Clear (truncate) trace file
    #
    # PARAMETERS:
    #
    #    NONE
    #
    # RETURNS:
    #       N/A
    #
    ##########################################################################

    def tron():

        global configInfo
        configInfo["trace.level"] = 1

        _app_trace("Trace on")

    def troff():

        global configInfo
        _app_trace("Trace off")
        configInfo["trace.level"] = 0



    def trlogon():
        global configInfo

        try:
            from java.util import Date
            from java.text import SimpleDateFormat
            sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
            tnow = sdf.format(Date())
            logFileName = "%s/%s.%s.%s.log" % (configInfo["config.trace.log.dir"],configInfo["config.trace.log.file"],java.lang.System.getenv("ENV"), tnow)

            configInfo["app.trace.tracelogfp"] = open(logFileName, "a")
            configInfo["app.trace.log"] = 1
            tron()
        except:
            configInfo["app.trace.log"] = 0
            print "Error opening tracefile %s" % (logFileName)

    def trlogoff():
        global configInfo

        try:
            if configInfo["app.trace.log"] == 1:
                _app_message("Tracelog closed")

                troff()
                configInfo["app.trace.tracelogfp"].close()
                configInfo["app.trace.log"] = 0

        except:
            _app_message("Error closing tracefile %s" % (configInfo["config.trace.log.file"]))

    def trlogclear():
        global configInfo

        try:
            if configInfo["app.trace.log"] == 1:

                #   fp.truncate() not supported?
                configInfo["app.trace.tracelogfp"].close()
                configInfo["app.trace.tracelogfp"] = open(configInfo["config.trace.log.file"], "w")
                _app_message("Tracelog cleared")

        except:
            _app_message("Error clearing tracefile %s" % (configInfo["config.trace.log.file"]))

    def exit():
        import sys
        trlogoff()
        sys.exit()
        #AdminConfig.reset()
        #pass

##########################################################################
#
# FUNCTION:
#    createMQQueueConnectionFactory: Create an MQ Queue Connection Factory
#
# SYNTAX:
#    createMQConnectionFactory name, (cluster|node, server), bus
#
# PARAMETERS:
#    name       - Name for MQ Queue Connection Factory entry
#    type       - Type of connection factory - QCF or TCF
#    jndiname   - JNDI Name for MQ Queue Connection Factory
#    cluster    - Name of cluster for cluster scoped provider
#    cell       - Name of cell for cell scoped provider
#    node       - Name of node for server scoped provider
#    server     - Name of server for server scoped provider
#    connPool   - List of attributes for connection pool in the form of [[name1, value1] [name2, value2] ...]
#    attrs      - Array of Attributes in the form of [[name1, value1] [name2, value2] ...]
#
# USAGE NOTES:
#    Creates a JMS Connection Factory on the bus at the desired scope.
#    You can specifiy the following combinations of cell, cluster, node, server
#        for cluster scope = clusterName, "none, "none", "none"
#        for cell scope = "none", cellName, "none", "none"
#        for node scope = "none", cellName, nodeName, "none"
#        for server scope = "none", cellName, nodeName, serverName
#
#    Other combinations will fail
#
#
# RETURNS:
#    ObjID    Object ID of new MQ Connection Factory
#    None    Failure
#
# THROWS:
#    N/A
##########################################################################

    def createMQConnectionFactory(name, type, jndiname, description, cluster, node, server, ccdt, ccdtQmgrName, clientId, connPool=[], sessionPool=[]):

        global configInfo

        retval = 1

        try:
            traceStr = "createMQQueueConnectionFactory(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)" % (name, jndiname, description, cluster, node, server, ccdt, ccdtQmgrName, clientId, connPool)
            _app_trace(traceStr, "entry")

            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)

            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to create MQ Connection Factory at" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            if objectExists("J2CConnectionFactory", j2craID, name):
                raise StandardError("MQ Connection Factory %s already exists at scope %s" % (name, configID))

            parentID  = AdminConfig.getid(j2craID)

            if isEmpty(parentID):
                raise StandardError("Cannot find ID; check cluster, cell or node/server values are correct")

            _app_trace("Got parent ID = " + parentID)

            attributes = "["
            attributes += "-type %s " % (type)
            attributes += "-name %s " % (name)
            attributes += "-jndiName %s " % (jndiname)
            attributes += "-description %s " % (description)
            attributes += "-ccdtUrl %s " % (ccdt)
            attributes += "-ccdtQmgrName %s " % (ccdtQmgrName)
            if not isEmpty(clientId):
                attributes += "-clientId %s" % (clientId)
            attributes += "]"

            _app_trace("Running command: AdminTask.createWMQConnectionFactory(%s, %s)" % (parentID, attributes))
            newcf = AdminTask.createWMQConnectionFactory(parentID, attributes)


            _app_trace("Got J2CConnectionFactory ID for %s = %s" % (name, newcf))

            _app_trace("Running command: AdminConfig.create('ConnectionPool', %s, %s)" % (newcf, connPool))
            AdminConfig.create('ConnectionPool', newcf, connPool, "connectionPool")

            _app_trace("Running command: AdminConfig.create('SessionPool', %s, %s)" % (newcf, sessionPool))
            AdminConfig.create('ConnectionPool', newcf, sessionPool, "sessionPool")


        except:
            _app_trace("An error was encountered creating the MQ Queue Connection Factory", "exception")
            retval = 0

        _app_trace("createMQConnectionFactory(%s)" % (retval), "exit")
        return retval

##########################################################################
#
# FUNCTION:
#    createMQQueueConnectionFactory: Create an MQ Queue Connection Factory
#
# SYNTAX:
#    createMQQueueConnectionFactory name, (cluster|node, server), bus
#
# PARAMETERS:
#    name       - Name for MQ Queue Connection Factory entry
#    jndiname   - JNDI Name for MQ Queue Connection Factory
#    cluster    - Name of cluster for cluster scoped provider
#    cell       - Name of cell for cell scoped provider
#    node       - Name of node for server scoped provider
#    server     - Name of server for server scoped provider
#    connPool   - List of attributes for connection pool in the form of [[name1, value1] [name2, value2] ...]
#    attrs      - Array of Attributes in the form of [[name1, value1] [name2, value2] ...]
#
# USAGE NOTES:
#    Creates a JMS Connection Factory on the bus at the desired scope.
#    You can specifiy the following combinations of cell, cluster, node, server
#        for cluster scope = clusterName, "none, "none", "none"
#        for cell scope = "none", cellName, "none", "none"
#        for node scope = "none", cellName, nodeName, "none"
#        for server scope = "none", cellName, nodeName, serverName
#
#    Other combinations will fail
#
#
# RETURNS:
#    ObjID    Object ID of new MQ Connection Factory
#    None    Failure
#
# THROWS:
#    N/A
##########################################################################

    def updateMQQueueConnectionFactory(name, jndiname, description, cluster, node, server, ccdt, ccdtQmgrName, connPool):

        global configInfo
        retval = 1

        try:
            traceStr = "updateMQQueueConnectionFactory(%s, %s, %s, %s, %s, %s, %s, %s, %s)" % (name, jndiname, description, cluster, node, server, ccdt, ccdtQmgrName, connPool)
            _app_trace(traceStr, "entry")

            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)

            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to create MQ Queue Connection Factory at" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            if objectExists("J2CConnectionFactory", j2craID, name):
                if not deleteMQConnectionFactory(name, cluster, node, server):
                    raise StandardError("MQ Queue Connection Factory exists at %s" % (configID))

            retval = createMQConnectionFactory(name, jndiname, description, cluster, node, server, ccdt, ccdtQmgrName, connPool)
        except:
            _app_trace("An error was encountered creating the MQ Queue Connection Factory", "exception")
            retval = 0

        _app_trace("updateMQQueueConnectionFactory(%s)" % (retval), "exit")
        return retval

##########################################################################
#
# FUNCTION:
#    deleteMQConnectionFactory: Delete an MQ Queue Connection Factory
#
# SYNTAX:
#    deleteMQConnectionFactory name, cluster|(node, server)
#
# PARAMETERS:
#    name    -    Name for MQ Queue Connection Factory entry
#    cluster    -    Name of cluster for cluster scoped provider
#    node    -    Name of node for server scoped provider
#    server    -    Name of server for server scoped provider
#
# USAGE NOTES:
#    Deletes an MQ Queue Connection Factory from the desired scope.

# RETURNS:
#    1    Success
#    0    Failure
#
# THROWS:
#    N/A
##########################################################################

    def deleteMQConnectionFactory(name, cluster, node, server):

        global configInfo

        retval = 1

        try:
            traceStr = "deleteMQConnectionFactory(%s, %s, %s, %s)" % (name, cluster, node, server)
            _app_trace(traceStr, "entry")

            if isEmpty(name):
                raise StandardError("MQ Connection Factory name not specified")

            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)

            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                _app_trace("No such target as %s to delete MQ Connection Factory from" % (configID))
            else:
                parentID  = AdminConfig.getid(configID)
                qcfList = AdminTask.listWMQConnectionFactories(parentID).split(configInfo["line.separator"])
                if isEmpty(qcfList):
                    _app_trace("No Queue Connection Factories found at %s" % (configID))
                else:
                    for factory in qcfList:
                        qcfName = AdminConfig.showAttribute(factory, 'name')
                        if qcfName == name:
                            _app_trace("Running Command: AdminTask.deleteWMQConnectionFactory(%s)" % (factory))
                            AdminTask.deleteWMQConnectionFactory(factory)
                            retval = 1
                        #end if
                    #end for
                #end if
            #end if

        except:
            _app_trace("An error was encountered deleting the MQ Queue Connection Factory", "exception")
            retval = 0

        _app_trace("deleteMQConnectionFactory(%d)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    listMQQueueConnectionFactories: List MQ Queue Connection Factories at scope
    #
    # SYNTAX:
    #    listMQQueueConnectionFactories cluster| (node, server), displayFlag
    #
    # PARAMETERS:
    #    cluster    -    Name of cluster for cluster scoped provider
    #    node    -    Name of node for server scoped provider
    #    server    -    Name of server for server scoped provider
    #    displayFlag-    Boolean indicating whether to print list
    #            (default = 1)
    #
    # USAGE NOTES:
    #    Lists MQ Queue Connection Factorys at the desired scope.
    #
    # RETURNS:
    #    The list or None in case of error
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def listMQQueueConnectionFactories(self, cell, cluster, node, server, displayFlag = 1):

        global configInfo

        retval = None

        try:
            traceStr = "listMQQueueConnectionFactories(%s, %s, %s, %d)" % (cluster, node, server, displayFlag)
            _app_trace(traceStr, "entry")

            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to list MQ Queue Connection Factories at" % (configID))
            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"
            configID += "JMS Provider:WebSphere MQ JMS Provider/"

            #    Get the parentID
            parentID = AdminConfig.getid(j2craID)

            _app_trace("Running command: AdminConfig.list('J2CConnectionFactory', %s)" % (parentID))
            str = AdminConfig.list('J2CConnectionFactory', parentID)

            if isEmpty(str):
                retval = []
            else:
                retval = str.split(configInfo["line.separator"])

            if displayFlag:
                print "\nMQ Queue Connection Factories\n-------------------"

                for r in retval:
                    print AdminConfig.showAttribute(r, "name")

                print "-------------------"

        except:
            _app_trace("An error was encountered listing the Mq Queue Connection Factories", "exception")
            retval = None

        _app_trace("listMQQueueConnectionFactories(%s)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    listMQTopicConnectionFactories: List MQ Topic Connection Factories at scope
    #
    # SYNTAX:
    #    listMQTopicConnectionFactories cluster| (node, server), displayFlag
    #
    # PARAMETERS:
    #    cluster    -    Name of cluster for cluster scoped provider
    #    node    -    Name of node for server scoped provider
    #    server    -    Name of server for server scoped provider
    #    displayFlag-    Boolean indicating whether to print list
    #            (default = 1)
    #
    # USAGE NOTES:
    #    Lists MQ Topic Connection Factorys at the desired scope.
    #
    # RETURNS:
    #    The list or None in case of error
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def listMQTopicConnectionFactories(cell, cluster, node, server, displayFlag = 1):

        global configInfo

        retval = None

        try:
            traceStr = "listMQTopicConnectionFactories(%s, %s, %s, %d)" % (cluster, node, server, displayFlag)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to list MQ Topic Connection Factories at" % (configID))

            configID += "JMS Provider:WebSphere MQ JMS Provider/"

            #    Get the parentID
            parentID = AdminConfig.getid(configID)

            _app_trace("Running command: AdminCongif.list('J2CConnectionFactory', %s)" % (parentID))
            str = AdminCongif.list('J2CConnectionFactory', parentID)

            if isEmpty(str):
                retval = []
            else:
                retval = str.split(configInfo["line.separator"])

            if displayFlag:
                print "\nMQ Topic Connection Factories\n-------------------"

                for r in retval:
                    print AdminConfig.showAttribute(r, "name")

                print "-------------------"

        except:
            _app_trace("An error was encountered listing the Mq Topic Connection Factories", "exception")
            retval = None

        _app_trace("listMQTopicConnectionFactories(%s)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    createMQQueue: Create an MQ Queue
    #
    # SYNTAX:
    #    createMQQueue name, (cluster|node, server), bus, queueName, attrs
    #
    # PARAMETERS:
    #    name    -    Name for MQ Queue entry
    #    cluster    -    Cluster to assign Queue
    #    node    -    Node to assign Queue
    #    server    -    Server to assign Queue
    #    queueName    -    Name of underlying SIB queue
    #    attrs    -    Array of Attributes in the form of [[name1, value1] [name2, value2] ...]
    #
    # USAGE NOTES:
    #    Creates an MW Queue at the desired scope.
    #
    # RETURNS:
    #    1    Success
    #    0    Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def createMQQueue(name, jndiname, queueName, cluster, node, server, attrs):
        global configInfo

        retval = 1

        try:
            traceStr = "createMQQueue(%s, %s, %s, %s, %s, %s, %s)" % (name, jndiname, queueName, cluster, node, server, attrs)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to create MQ Queue Connection Factory at" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            if objectExists("MQQueue", j2craID, name):
                raise StandardError("MQ Queue %s already exists at scope %s" % (name, configID))

            parentID  = AdminConfig.getid(j2craID)

            attributes = []
            attributes.append(["name", name])
            attributes.append(['jndiName', jndiname])
            attributes.append(['baseQueueName', queueName])

            templates = AdminConfig.listTemplates("MQQueue").split(configInfo["line.separator"])
            _app_trace("MQQueue Templates: %s" % (templates))

            if isEmpty(templates[0]):
                raise StandardError("MQ Queue template not found")

            template = templates[0]
            _app_trace("Using template %s to create MQ Queue" % (template))

            _app_trace("Running command AdminConfig.createUsingTemplate('MQQueue', %s, %s, %s)" % (parentID, attributes, template))
            mqqueue = AdminConfig.createUsingTemplate('MQQueue', parentID, attributes, template)

            _app_trace("Running command AdminConfig.modify(%s, %s)" % (mqqueue, attrs))
            AdminConfig.modify(mqqueue, attrs)

            _app_trace("Created MQ Queue %s" %(name))

        except:
            _app_trace("An error was encountered creating the MQ Queue", "exception")
            retval = 0

        _app_trace("createMQQueue(%s)" % (retval), "exit")
        return retval

    ##########################################################################
    #
    # FUNCTION:
    #    updateMQQueue: Create an MQ Queue
    #
    # SYNTAX:
    #    createMQQueue name, (cluster|node, server), bus, queueName, attrs
    #
    # PARAMETERS:
    #    name    -    Name for MQ Queue entry
    #    cluster    -    Cluster to assign Queue
    #    node    -    Node to assign Queue
    #    server    -    Server to assign Queue
    #    queueName    -    Name of underlying SIB queue
    #    attrs    -    Array of Attributes in the form of [[name1, value1] [name2, value2] ...]
    #
    # USAGE NOTES:
    #    Creates an MW Queue at the desired scope.
    #
    # RETURNS:
    #    1    Success
    #    0    Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def updateMQQueue(name, jndiname, queueName, cluster, node, server, attrs):
        global configInfo

        retval = 1

        try:
            traceStr = "updateMQQueue(%s, %s, %s, %s, %s, %s, %s)" % (name, jndiname, queueName, cluster, node, server, attrs)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to create MQ Queue Connection Factory at" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            if objectExists("MQQueue", j2craID, name):
                if not deleteMQQueue(name, cluster, node, server):
                    raise StandardError("MQ Queue %s already exists at scope %s" % (name, configID))


            retval = createMQQueue(name, jndiname, queueName, cluster, node, server, attrs)

        except:
            _app_trace("An error was encountered creating the MQ Queue", "exception")
            retval = 0

        _app_trace("createMQQueue(%s)" % (retval), "exit")
        return retval

    ##########################################################################
    #
    # FUNCTION:
    #    deleteMQQueue: Delete an MQ Queue
    #
    # SYNTAX:
    #    deleteMQQueue (name, cluster|(node, server)
    #
    # PARAMETERS:
    #    name    -    Name for MQ Queue Connection Factory entry
    #    cluster    -    Name of cluster for cluster scoped provider
    #    node    -    Name of node for server scoped provider
    #    server    -    Name of server for server scoped provider
    #
    # USAGE NOTES:
    #    Deletes an MQ Queue  from the desired scope.

    # RETURNS:
    #    0    Success
    #    1    Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def deleteMQQueue(name, cluster, node, server):

        global configInfo

        retval = 1

        try:
            traceStr = "deleteMQQueue(%s, %s, %s, %s)" % (name, cluster, node, server)
            _app_trace(traceStr, "entry")

            if isEmpty(name):
                raise StandardError("MQ Queue name not specified")

            #    Check function parameters
            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)

            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                _app_trace("No such target as %s from which to delete MQ Queue" % (configID))
            else:
                parentID = AdminConfig.getid(configID)
                _app_trace("Got Parent ID: %s" % (parentID))

                idList  = AdminTask.listWMQQueues(parentID)
                qList = idList.split(configInfo["line.separator"])
                _app_trace("Got Queues = %s" % (qList))

                if isEmpty(qList):
                    _app_trace("No Queues defined at %s" % (configID))
                else:
                    for q in qList:
                        nameAttr = AdminConfig.showAttribute(q, 'name')
                        if nameAttr == name:
                            _app_trace("Running command: AdminTask.deleteWMQQueue(%s)" % (q))
                            AdminTask.deleteWMQQueue(q)
                        #end if
                    #end for
                #end if
            #end if
            retval = 1

        except:
            _app_trace("An error was encountered deleting the MQ Queue", "exception")
            retval = 0

        _app_trace("deleteMQQueue(%d)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    listMQQueues: List MQ Queues at scope
    #
    # SYNTAX:
    #    listMQQueues cluster | cell | (node, server), displayFlag
    #
    # PARAMETERS:
    #    cluster     - Name of cluster for cluster scoped provider
    #    cell        - Name of cell for cell scoped provider
    #    node        - Name of node for server scoped provider
    #    server      - Name of server for server scoped provider
    #    displayFlag - Boolean indicating whether to print list
    #            (default = 1)
    #
    # USAGE NOTES:
    #    Lists MQ Queues at the desired scope.
    #
    # RETURNS:
    #    The list or None in case of error
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def listMQQueues(cell, cluster, node, server, displayFlag = 1):

        global configInfo

        retval = None

        try:
            traceStr = "listMQQueues(%s, %s, %s, %d)" % (cluster, node, server, displayFlag)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s at which to list MQ Queues" % (configID))
            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"
            configID += "JMS Provider:WebSphere MQ JMS Provider/"

            #    Get the parentID
            parentID = AdminConfig.getid(j2craID)

            _app_trace("Running command: AdminCongif.list('MQQueues', %s)" % (parentID))
            str = AdminCongif.list('MQQueues', parentID)

            if isEmpty(str):
                retval = []
            else:
                retval = str.split(configInfo["line.separator"])

            if displayFlag:
                print "\nMQ Queue Connection Factories\n-------------------"

                for r in retval:
                    print AdminConfig.showAttribute(r, "name")

                print "-------------------"

        except:
            _app_trace("An error was encountered listing the Mq Queues", "exception")
            retval = None

        _app_trace("listMQQueues(%s)" %(retval), "exit")
        return retval

    ##########################################################################
    #
    # FUNCTION:
    #    createMQTopic: Create an MQ Topic
    #
    # SYNTAX:
    #    createMQTopic name, (cluster|node, server), bus, TopicName, attrs
    #
    # PARAMETERS:
    #    name    -    Name for MQ Topic entry
    #    cluster    -    Cluster to assign Topic
    #    node    -    Node to assign Topic
    #    server    -    Server to assign Topic
    #    TopicName    -    Name of underlying SIB Topic
    #    attrs    -    Array of Attributes in the form of [[name1, value1] [name2, value2] ...]
    #
    # USAGE NOTES:
    #    Creates an MW Topic at the desired scope.
    #
    # RETURNS:
    #    ObjID   Object ID of new appserver
    #    None    Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def createMQTopic(name, jndiname, queueName, cluster, node, server, attrs):
        global configInfo

        retval = None

        try:
            traceStr = "createMQTopic(%s, %s, %s, %s, %s, %s, %s)" % (name, jndiname, queueName, cluster, node, server, attrs)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s to create MQ Topic at" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            if objectExists("MQTopic", j2craID, name):
                _app_trace("MQ Topic Exisits, so Delete")
                if deleteMQTopic(name, cell, cluster, node, server):
                    raise StandardError("MQ Topic %s already exists at scope %s" % (name, configID))
                else:
                    _app_trace("Deleted Topic, re-create")

            parentID  = AdminConfig.getid(j2craID)

            attributes = []
            attributes.append(["name", name])
            attributes.append(['jndiName', jndiname])
            attributes.append(['baseTopicName', queueName])

            templates = AdminConfig.listTemplates("MQTopic").split(configInfo["line.separator"])
            _app_trace("MQTopic Templates: %s" % (templates))

            if isEmpty(templates[0]):
                raise StandardError("MQ Topic template not found")

            template = templates[0]
            _app_trace("Using template %s to create MQ Topic" % (template))

            _app_trace("Running command AdminConfig.createUsingTemplate('MQTopic', %s, %s, %s)" % (parentID, attributes, template))
            mqqueue = AdminConfig.createUsingTemplate('MQTopic', parentID, attributes, template)

            _app_trace("Running command AdminConfig.modify(%s, %s)" % (mqqueue, attrs))
            AdminConfig.modify(mqqueue, attrs)

            retval = mqqueue

            _app_trace("Created MQ Topic %s" %(name))

        except:
            _app_trace("An error was encountered creating the MQ Topic", "exception")
            retval = None

        _app_trace("createMQTopic(%s)" % (retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    deleteMQTopic: Delete an MQ Topic
    #
    # SYNTAX:
    #    deleteMQTopic (name, cluster|(node, server)
    #
    # PARAMETERS:
    #    name    -    Name for MQ Topic Connection Factory entry
    #    cluster    -    Name of cluster for cluster scoped provider
    #    node    -    Name of node for server scoped provider
    #    server    -    Name of server for server scoped provider
    #
    # USAGE NOTES:
    #    Deletes an MQ Topic  from the desired scope.

    # RETURNS:
    #    0    Success
    #    1    Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def deleteMQTopic(name, cluster, node, server):

        global configInfo

        retval = 1

        try:
            traceStr = "deleteMQTopic(%s, %s, %s, %s)" % (name, cluster, node, server)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s from which to delete MQ Topic" % (configID))

            if isEmpty(name):
                raise StandardError("MQ Topic name not specified")

            #    If object doesn't exist, warn and exit
            if not objectExists("MQTopic", configID, name):
                raise StandardError("MQ Topic  %s does not exist at scope %s" % (name, configID))

            configID += "MQTopic:%s/" % (name)
            parentID  = AdminConfig.getid(configID)

            if isEmpty(parentID):
                raise StandardError("Cannot find ID; check cluster or node/server and MQ Topic values are correct")

            _app_trace("Got parent ID = " + parentID)
            _app_trace("Running command: AdminConfig.remove(%s)" % (parentID))

            AdminConfig.remove(parentID)

            retval = 0
        except:
            _app_trace("An error was encountered deleting the MQ Topic", "exception")
            retval = 1

        _app_trace("deleteMQTopic(%d)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    listMQTopics: List MQ Topics at scope
    #
    # SYNTAX:
    #    listMQTopics cluster | cell | (node, server), displayFlag
    #
    # PARAMETERS:
    #    cluster     - Name of cluster for cluster scoped provider
    #    cell        - Name of cell for cell scoped provider
    #    node        - Name of node for server scoped provider
    #    server      - Name of server for server scoped provider
    #    displayFlag - Boolean indicating whether to print list
    #            (default = 1)
    #
    # USAGE NOTES:
    #    Lists MQ Topics at the desired scope.
    #
    # RETURNS:
    #    The list or None in case of error
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def listMQTopics(cell, cluster, node, server, displayFlag = 1):

        global configInfo

        retval = None

        try:
            traceStr = "listMQTopics(%s, %s, %s, %d)" % (cluster, node, server, displayFlag)
            _app_trace(traceStr, "entry")

            #    Check function parameters
            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s at which to list MQ Topics" % (configID))
            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"
            configID += "JMS Provider:WebSphere MQ JMS Provider/"

            #    Get the parentID
            parentID = AdminConfig.getid(j2craID)

            _app_trace("Running command: AdminCongif.list('MQTopics', %s)" % (parentID))
            str = AdminCongif.list('MQTopics', parentID)

            if isEmpty(str):
                retval = []
            else:
                retval = str.split(configInfo["line.separator"])

            if displayFlag:
                print "\nMQ Topic Connection Factories\n-------------------"

                for r in retval:
                    print AdminConfig.showAttribute(r, "name")

                print "-------------------"

        except:
            _app_trace("An error was encountered listing the Mq Topics", "exception")
            retval = None

        _app_trace("listMQTopics(%s)" %(retval), "exit")
        return retval


    ##########################################################################
    #
    # FUNCTION:
    #    createMQActivationSpec: Create MQ Activation Spec at scope
    #
    # SYNTAX:
    #    createMQActivationSpec cluster | cell | (node, server), name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount, props
    #
    # PARAMETERS:
    #    cluster              - Name of cluster for cluster scoped provider
    #    cell                 - Name of cell for cell scoped provider
    #    node                 - Name of node for server scoped provider
    #    server               - Name of server for server scoped provider
    #    name                 - Name of Activation Spec
    #    jndiName             - JNDI Name of Activation Spec
    #    queueJndi            - JNDI Name of Queue to act on
    #    ccdtUrl              - Client Channel definition table
    #    ccdtQmgrName         - Client Channel definition QManager Name
    #    failureDeliveryCount - Number of sequential delivery failures before endpoint is suspended
    #    props                - General properties
    #
    # USAGE NOTES:
    #    creates MQ Activation Spec at the desired scope.
    #
    # RETURNS:
    #    1 = Success
    #    0 = Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def createMQActivationSpec(cluster, node, server, name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount, props = "None"):
      global configInfo
      retval = 1
      try:
          traceStr = "createMQActivationSpec(%s, %s, %s, %s, %s, %s, %s, %s, %s)" % (cluster, node, server, name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount)
          _app_trace(traceStr, "entry")

          cell = AdminControl.getCell()

          configID = getContainmentPath(cell, cluster, node, server)
          if isEmpty(configID):
              raise StandardError("Could not get containment path")

          if isEmpty(AdminConfig.getid(configID)):
              raise StandardError("No such target as %s at which to create Activation Spec" % (configID))

          attributes = "["
          attributes += " -name %s " % (name)
          attributes += " -description %s " % (name)
          attributes += " -jndiName %s " % (jndiName)
          attributes += " -destinationJndiName %s " % (queueJndi)
          attributes += " -destinationType javax.jms.Queue "
          attributes += " -ccdtUrl %s " % (ccdtUrl)
          attributes += " -ccdtQmgrName %s " % (ccdtQmgrName)
          attributes += " -stopEndpointIfDeliveryFails true "
          attributes += " -failIfQuiescing true "
          attributes += " -failureDeliveryCount %s " % (failureDeliveryCount)
          attributes += "]"

          _app_trace("Attributes created: %s" % (attributes))

          j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

          #    Get the parentID
          parentID = AdminConfig.getid(j2craID)

          _app_trace("Running command: AdminTask.createWMQActivationSpec(%s, %s)" % (parentID, attributes))

          wmqspec = AdminTask.createWMQActivationSpec(parentID, attributes)

          props = props.strip()
          _app_trace("Running command: AdminTask.modifyWMQActivationSpec(%s, %s)" % (wmqspec, props))
          AdminTask.modifyWMQActivationSpec(wmqspec, props)

          retval = 1

      except:
          _app_trace("An error was encountered creating Activation Spec", "exception")
          retval = 0

      _app_trace("createMQActivationSpec(%s)" %(retval), "exit")

      return retval
    #end def

    ##########################################################################
    #
    # FUNCTION:
    #    updateMQActivationSpec: Create MQ Activation Spec at scope
    #
    # SYNTAX:
    #    updateMQActivationSpec cluster | cell | (node, server), name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount
    #
    # PARAMETERS:
    #    cluster              - Name of cluster for cluster scoped provider
    #    cell                 - Name of cell for cell scoped provider
    #    node                 - Name of node for server scoped provider
    #    server               - Name of server for server scoped provider
    #    name                 - Name of Activation Spec
    #    jndiName             - JNDI Name of Activation Spec
    #    queueJndi            - JNDI Name of Queue to act on
    #    ccdtUrl              - Client Channel definition table
    #    ccdtQmgrName         - Client Channel definition QManager Name
    #    failureDeliveryCount - Number of sequential delivery failures before endpoint is suspended
    #
    # USAGE NOTES:
    #    updates MQ Activation Spec at the desired scope.
    #
    # RETURNS:
    #    1 = Success
    #    0 = Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def updateMQActivationSpec(cluster, node, server, name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount):
        global configInfo
        retval = 1
        try:
            traceStr = "updateMQActivationSpec(%s, %s, %s, %s, %s, %s, %s, %s, %s)" % (cluster, node, server, name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount)
            _app_trace(traceStr, "entry")

            cell = AdminControl.getCell()

            configID = getContainmentPath(cell, cluster, node, server)
            if isEmpty(configID):
                raise StandardError("Could not get containment path")

            if isEmpty(AdminConfig.getid(configID)):
                raise StandardError("No such target as %s at which to create Activation Spec" % (configID))

            j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

            #    Get the parentID
            parentID = AdminConfig.getid(j2craID)
            asList = AdminTask.listWMQActivationSpecs(parentID)
            asList = asList.split(configInfo["line.separator"])

            _app_trace("Got MQ Activation Specs = %s" % (asList))

            for aspec in asList:
                if not isEmpty(aspec):
                    _app_trace("Working with Spec: %s" % (aspec))
                    nameAttr = AdminConfig.showAttribute(aspec, 'name')
                    if name == nameAttr:
                        _app_trace("Running Command: AdminTask.deleteWMQActivationSpec(%s)" % (aspec))
                        AdminTask.deleteWMQActivationSpec(aspec)
                        retval = 1
                    #end if
                #end if
            #end for
            retval = createMQActivationSpec(cluster, node, server, name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName, failureDeliveryCount)
        except:
            _app_trace("An error was encountered updating Activation Spec", "exception")
            retval = 0

        _app_trace("updateMQActivationSpec(%s)" %(retval), "exit")
        return retval
    #end def

    ##########################################################################
    #
    # FUNCTION:
    #    deleteMQActivationSpec: Create MQ Activation Spec at scope
    #
    # SYNTAX:
    #    deleteMQActivationSpec cluster | cell | (node, server), name, jndiName, queueJndi, ccdtUrl, ccdtQmgrName
    #
    # PARAMETERS:
    #    cluster     - Name of cluster for cluster scoped provider
    #    cell        - Name of cell for cell scoped provider
    #    node        - Name of node for server scoped provider
    #    server      - Name of server for server scoped provider
    #    name        - Name of Activation Spec
    #    jndiName    - JNDI Name of Activation Spec
    #    queueJndi   - JNDI Name of Queue to act on
    #    ccdtUrl     - Client Channel definition table
    #    ccdtQmgrName- Client Channel definition QManager Name
    #
    # USAGE NOTES:
    #    creates MQ Activation Spec at the desired scope.
    #
    # RETURNS:
    #    1 = Success
    #    0 = Failure
    #
    # THROWS:
    #    N/A
    ##########################################################################

    def deleteMQActivationSpec(cluster, node, server, name):
        global configInfo
        retval = 1
        try:
            traceStr = "deleteMQActivationSpec(%s, %s, %s, %s)" % (cluster, node, server, name)
            _app_trace(traceStr, "entry")

            cell = AdminControl.getCell()
            configID = getContainmentPath(cell, cluster, node, server)

            if isEmpty(configID):
                raise StandardError("Could not get containment path")
            if isEmpty(AdminConfig.getid(configID)):
                _app_trace("No such target as %s at which to delete Activation Spec" % (configID))
            else:
                j2craID = configID + "JMSProvider:WebSphere MQ JMS Provider/"

                #    Get the parentID
                parentID = AdminConfig.getid(j2craID)
                asList = AdminTask.listWMQActivationSpecs(parentID)
                asList = asList.split(configInfo["line.separator"])

                _app_trace("Got MQ Activation Specs = %s" % (asList))
                for aspec in asList:
                    _app_trace("Working with Spec: %s" % (aspec))
                    nameAttr = AdminConfig.showAttribute(aspec, 'name')
                    if name == nameAttr:
                        _app_trace("Running Command: AdminTask.deleteWMQActivationSpec(%s)" % (aspec))
                        AdminTask.deleteWMQActivationSpec(aspec)
                        retval = 1
                    #end if
                #end for
            #end if
        except:
            _app_trace("An error was encountered deleting Activation Spec", "exception")
            retval = 0

        _app_trace("deleteMQActivationSpec(%s)" %(retval), "exit")
        return retval
    #end def
