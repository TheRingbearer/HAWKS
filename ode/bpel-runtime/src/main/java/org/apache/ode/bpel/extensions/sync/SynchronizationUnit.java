//package org.apache.ode.bpel.extensions.sync;
//
//import java.util.ArrayList;
//import java.util.Date;
//
//import org.apache.activemq.ActiveMQConnectionFactory;
//import org.apache.commons.lang.SystemUtils;
//
//import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
//import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
//import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
//import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
////import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Skipped;
////import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Running;
////import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
////import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
////import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.Serializable;
//
//import javax.jms.Connection;
//import javax.jms.Destination;
//import javax.jms.Message;
//import javax.jms.MessageConsumer;
//import javax.jms.MessageProducer;
//import javax.jms.ObjectMessage;
//import javax.jms.Session;
//
//import java.io.BufferedReader;
////import java.io.BufferedWriter;
//import java.io.FileReader;
////import java.io.FileWriter;
//import java.io.IOException;
////import java.net.URL;
//
///**
// * A class responsible for supervising communication between workflows and instances of SynchronizationController.
// * 
// * @author krawczls
// *
// */
//public class SynchronizationMiddleware {
//		
//	public static Connection connectionLocal = null;
//	public static Connection connectionMiddleware = null;
//    /**
//     * A method that initializes a new SynchronizationMiddleware instance, if necessary.
//     * 
//     * @param arg
//     */
//    public void init(String arg1, String arg2, String arg3) {
//    	if (Constants.DEBUG_LEVEL > 0) {
//    		System.out.println("SynchronizationMiddleware.init");
//    	}
//    	try{
//    		if(arg1.equals("true")) { 
//    			thread(new SynchronizationMiddlewareThread(arg2, arg3), false);
//    			Thread.sleep(1000);
//    		}
//    	}
//    	catch(Exception e) {
//    		System.out.println("SynchronizationMiddlewareThread could not be started.");
//    	}
//    }
//    
//    
//    /**
//     * A method for initializing and starting a thread.
//     * 
//     * @param runnable
//     * @param daemon
//     */
//    public static void thread(Runnable runnable, boolean daemon) {
//    	System.out.println("SynchronizationMiddleware.thread");
//        Thread brokerThread = new Thread(runnable);
//        brokerThread.setDaemon(daemon);
//        brokerThread.start();
//    }
//    
//    
//    /**
//     * A thread that is resposible for relaying messages from specific workflows to respective SynchronizationController threads.
//     * 
//     * @author krawczls
//     *
//     */
//    public static class SynchronizationMiddlewareThread implements Runnable {
//    	
//    	//variables for local connection
//		Session sessionLocal = null;
//		MessageProducer producerLocal = null;
//		MessageConsumer consumerLocal = null;
//		
//		Serializable localObject = null;
//		
//		//variables for connection with the middleware
//		Session sessionMiddleware = null;
//		MessageProducer producerMiddleware = null;
//		MessageConsumer consumerMiddleware = null;
//		
//		Serializable middlewareObject = null;
//		
//		//ArrayList<SynchronizationControllerRepresentation> controllers = new ArrayList<SynchronizationControllerRepresentation>();
//		
//		ArrayList<Long> invokes = new ArrayList<Long>();
//		
//		ArrayList<ArrayList<?>> instancesReadyToBeStarted = new ArrayList<ArrayList<?>>();
//		ArrayList<Long> processIDOfInstancesReadyToBeStarted = new ArrayList<Long>();
//		
//		String myIP;
//		String middlewareIP;
//		
//		long heartbeat = (new Date()).getTime();
//		
//		public SynchronizationMiddlewareThread(String myIP, String middlewareIP) {
//			this.myIP = myIP;
//			this.middlewareIP = middlewareIP;
//		}
//		
//    	/**
//    	 * A method that sets up the connection to the local ode and registers the event "Activity_Ready" as blocking.
//    	 * It also sets up the connection to the other execution engines, creates a class for every execution engine
//    	 * and saves them in a list.
//    	 * 
//    	 * @throws Exception
//    	 */
//    	public void setupConnections() throws Exception {
//    		if (Constants.DEBUG_LEVEL > 0) {
//    			System.out.println("setup connections");
//    		}
//    		//setting up local connections
//    		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616?jms.prefetchPolicy.queuePrefetch=100000");       
//	        connectionLocal = connectionFactory.createConnection();
//	        connectionLocal.start();
//	        sessionLocal = connectionLocal.createSession(false, Session.AUTO_ACKNOWLEDGE);
//	            
//	        //setting up communication with the ode
//	        //(check in org.apache.ode.bpel.extensions.comm.Communication.java)
//	        Destination destinationLocal = sessionLocal.createQueue("org.apache.ode.in");
//	        Destination sourceLocal = sessionLocal.createTopic("org.apache.ode.events");
//	            
//	        //creating message producer for ode
//	        producerLocal = sessionLocal.createProducer(destinationLocal);
//	        //creating message consumer for ode 
//	        consumerLocal = sessionLocal.createConsumer(sourceLocal); 
//
//	        //send request registration information message to ode
//	        RequestRegistrationInformation requestRegistrationMessageLocal = new RequestRegistrationInformation();			
//	        ObjectMessage requestProducerLocal = sessionLocal.createObjectMessage(requestRegistrationMessageLocal);
//	        requestProducerLocal.setJMSReplyTo(sourceLocal);           
//	        producerLocal.send(requestProducerLocal);   			
//       
//	        consumerLocal.receive();
//					
//			RegisterRequestMessage registerRequestMessage = new RegisterRequestMessage(); 
//
//			//set Activity_Ready to be blocking
//			Requested_Blocking_Events blockedEvents = registerRequestMessage.new Requested_Blocking_Events();
//			blockedEvents.Activity_Ready = true;
//			registerRequestMessage.setGlobalEventBlockings(blockedEvents);
//			
//			//send register request message to ode
//	        ObjectMessage registerProducerLocal = sessionLocal.createObjectMessage(registerRequestMessage);
//	        registerProducerLocal.setJMSReplyTo(sourceLocal);
//	        producerLocal.send(registerProducerLocal);	
//	                       
//		    //setting up middleware connections
//	    	ActiveMQConnectionFactory connectionFactoryMiddleware = new ActiveMQConnectionFactory("tcp://" + middlewareIP + ":61616?jms.prefetchPolicy.queuePrefetch=100000");     
//	
//		    connectionMiddleware = connectionFactoryMiddleware.createConnection();
//		    connectionMiddleware.start();
//		    sessionMiddleware = connectionMiddleware.createSession(false, Session.AUTO_ACKNOWLEDGE);
//	
//		    Destination destinationMiddleware = sessionMiddleware.createQueue("de.unistuttgart.rep");
//		    Destination sourceMiddleware = sessionMiddleware.createQueue("de.unistuttgart.rep." + myIP);
//		         
//		    producerMiddleware = sessionMiddleware.createProducer(destinationMiddleware);
//	
//		    //registering workflow engine with the middleware
//		    ObjectMessage registerWorkflowEngine = sessionMiddleware.createObjectMessage(myIP);           
//		    producerMiddleware.send(registerWorkflowEngine);  
//	
//		    consumerMiddleware = sessionMiddleware.createConsumer(sourceMiddleware);
//    	}
//    	
//    	/**
//    	 * A procedure that empties the log file and deletes the old data from
//    	 * previous tests. Check if it works.
//    	 * 
//    	 */
//    	public void emptyLogFile() {
//    		try {
//    			File file;
//    			if(SystemUtils.IS_OS_UNIX) {
//    				file = new File("/home/ubuntu/logs/log.txt");
//    			}
//    			else {
//    				file = new File("log.txt");
//    			}
//    			if(file.exists()) {
//        			FileWriter fWriter = new FileWriter(file.getAbsoluteFile());
//        			BufferedWriter bWriter = new BufferedWriter(fWriter);
//        			bWriter.write("");
//        			bWriter.close();
//    			}
//    		}
//    		catch(IOException e) {
//    			e.printStackTrace();
//    		}
//    	}
//    	
//    	/**
//    	 * A function that returns a message it receives from the ode/a running workflow instance.
//    	 * 
//    	 * @return
//    	 * @throws Exception
//    	 */
//    	public Serializable receiveMessageFromWorkflow() throws Exception {
//    		/*Message message = null;
//    		if(time == 0) {
//            	message = consumer_local.receive();
//    		}
//    		else {
//    			message = consumer_local.receive(time);
//    		}*/
//    		//22.01.16 - changed to receiveNoWait without argument 
//    		Message message = consumerLocal.receive(50);
//            if(message != null){
//            	ObjectMessage messageLocal = (ObjectMessage) message;
//            	return messageLocal.getObject();
//            }
//            return null;
//    	}
//    	
//    	/**
//    	 * A function that returns a message it receives from the workflow replication middleware.
//    	 * 
//    	 * @return
//    	 * @throws Exception
//    	 */
//    	public Serializable receiveMessageFromMiddleware() throws Exception {
//    		//Message message = null;
//    		/*if(time == 0) {
//    			message = consumer_middleware.receive();
//    		}
//    		else {
//    			message = consumer_middleware.receive(time);
//    		}*/
//    		//22.01.16 - changed to receiveNoWait without argument 
//    		Message message = consumerMiddleware.receive(50);
//    		if(message != null) {
//    			ObjectMessage messageMiddleware = (ObjectMessage) message;
//    			return messageMiddleware.getObject();
//    		}
//    		return null;
//    	}
//    	
//    	/**
//    	 * Starts a new instance of SynchronizationController.
//    	 * 
//    	 * @param id
//    	 */
//    	public void createNewInstanceOfSynchronizationController(String myIP, String middlewareIP, long id, String replicatedWorkflowID, int replicationDegree, int engineNumber, boolean planningMaster, Activity_Ready firstReady, int heartbeatTime, int timeout, boolean enginesEqualToReplicationDegree, boolean error) {
//    		try {
//    			new SynchronizationController().init(myIP, middlewareIP, id, replicatedWorkflowID, replicationDegree, engineNumber, planningMaster, firstReady, heartbeatTime, timeout, enginesEqualToReplicationDegree, error);
//    		}
//    		catch(Exception e) {
//    			System.out.println("New SynchronizationController could not be instantiated.");
//    		}
//    	}   	
//    	
//    	/**
//    	 * @return
//    	 */
//    	public boolean checkInvokes(Long Id) {
//    		for(int i = 0; i < invokes.size(); i++) {
//    			if(Id.equals((Long) invokes.get(i))) {
//    				return true;
//    			}
//    		}
//    		return false;
//    	}
//    	
//    	/**
//    	 * A method for closing the connections.
//    	 * 
//    	 * @throws Exception
//    	 */
//    	public void closeConnections() throws Exception {
//    		if (Constants.DEBUG_LEVEL > 0) {
//    			System.out.println("SynchronizationMiddleware.closeConnections");
//    		}
//            // Clean up
//            sessionLocal.close();
//            connectionLocal.close();
//    	}
//    	
//    	/**
//    	 * A helper method for reading my ip.
//    	 */
//    	public void readIPs() {  	
//    		if (Constants.DEBUG_LEVEL > 0) {
//    			System.out.println("SynchronizationMiddleware.readIPs");
//    		}
//    		BufferedReader reader = null;
//    		try {
//    			//URL url = getClass().getResource("ip.txt");
//    			//reader = new BufferedReader(new FileReader (url.getPath()));
//    			if(SystemUtils.IS_OS_UNIX) {
//    				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/ip.txt"));
//    			}
//    			else {
//    				reader = new BufferedReader(new FileReader ("ip.txt"));
//    			}
//    			myIP = reader.readLine();
//    			if (Constants.DEBUG_LEVEL > 0) {
//    				System.out.println(myIP);
//    			}
//    			middlewareIP = reader.readLine();
//    			if (Constants.DEBUG_LEVEL > 0) {
//    				System.out.println(middlewareIP);
//    			}
//    		}
//    		catch(IOException e) {
//    			e.printStackTrace();
//    		}
//    		finally {
//    			try {
//    				if(reader != null) {
//    					reader.close();
//    				}
//    			}
//    			catch(IOException e) {
//    				e.printStackTrace();
//    			}
//    		}
//    		
//    	}
//    	
//    	/**
//    	 * A function that searches the instancesReadyToBeStarted ArrayList, to return the first position
//    	 * at which a workflow model is saved, that matches the processName and hasn't been marked for execution yet.
//    	 * Returns -1 if nothing appropriate could be found. 
//    	 * 
//    	 * @param processName
//    	 * @return
//    	 */
//    	public int returnIndexOfInstancesReadyToBeStarted(String processName) {
//    		for(int i = 0; i < instancesReadyToBeStarted.size(); i++) {
//    			if(((ArrayList<?>) instancesReadyToBeStarted.get(i)).get(0).toString().equals(processName) &&
//    					processIDOfInstancesReadyToBeStarted.get(i).equals(-1L)) {
//    				return i;
//    			}
//    		}
//    		return -1;
//    	}
//    	
//    	/**
//    	 * A function that searches the processIDOfInstancesToBeStarted ArrayList, to return the position of 
//    	 * the matching processID.
//    	 * 
//    	 * @param processID
//    	 * @return
//    	 */
//    	public int returnIndexOfProcessIDOfInstancesReadyToBeStarted(Long processID) {
//    		for(int i = 0; i < processIDOfInstancesReadyToBeStarted.size(); i++) {
//    			if(processIDOfInstancesReadyToBeStarted.get(i).equals(processID)) {
//    				return i;
//    			}
//    		}
//    		return -1;
//    	}
//    	
//    	/* (non-Javadoc)
//    	 * @see java.lang.Runnable#run()
//    	 */
//    	public void run() {
//    		System.out.println("SynchronizationMiddleware.run");
//    		synchronized(this) {
//    			try {
//    				readIPs();
//    				setupConnections();
//    				emptyLogFile();
//    				
//    				boolean x = true;
//    				while(x) {
//    					if(middlewareObject == null) {
//    						middlewareObject = receiveMessageFromMiddleware();
//    					}  					
//    					//An ArrayList object represents the various information, the middleware sends us and 
//    					//we need to execute a new replicated workflow instance, started by the middleware
//    					if(middlewareObject instanceof ArrayList) {
//    						instancesReadyToBeStarted.add((ArrayList<?>) middlewareObject);
//    						processIDOfInstancesReadyToBeStarted.add(-1L);
//    						middlewareObject = null;
//    					}
//    					else if (middlewareObject != null){
//    						if (Constants.DEBUG_LEVEL > 0) {
//    							System.out.println(middlewareObject.toString());
//    						}
//    						middlewareObject = null;
//    					}
//    					
//    					//trying to receive a new message from the ode
//    					localObject = receiveMessageFromWorkflow();
//
//    					//the message is an instance of Instance_Running
//    					//a new SynchronizationController instance needs to be initialized
//    					if(localObject instanceof Instance_Running){
//    						if (Constants.DEBUG_LEVEL > 0) {
//    							System.out.println(new Date().getTime());
//    							System.out.println("*" + ((Instance_Running) localObject).getProcessName().toString() + "*");
//    						}
//    						
//    						int processIDIndex = returnIndexOfInstancesReadyToBeStarted(((Instance_Running) localObject).getProcessName().toString());
//    						if(processIDIndex == -1) {
//    							if (Constants.DEBUG_LEVEL > 0) {
//    								System.out.println("SynchronizationMiddleware - Starting a non-replicated workflow instance.");
//    							}
//    							invokes.add(((Instance_Running) localObject).getProcessID());
//    						}	
//    						else {
//    							if (Constants.DEBUG_LEVEL > 0) {
//    								System.out.println("SynchronizationMiddleware - Marking replicated workflow " + ((Instance_Running) localObject).getProcessID() + " instance for execution at index " + processIDIndex + ".");
//    							}
//    							processIDOfInstancesReadyToBeStarted.set(processIDIndex, ((Instance_Running) localObject).getProcessID());
//    						}
//    					}
//    					//the message is an instance of Activity_Ready
//    					else if(localObject instanceof Activity_Ready) {   	
//    						if(checkInvokes(((Activity_Ready) localObject).getProcessID())){
//    							Start_Activity start_message = new Start_Activity();
//    							start_message.setReplyToMsgID(((Activity_Ready) localObject).getMessageID());
//    						
//    							ObjectMessage message_producer_local = sessionLocal.createObjectMessage(start_message);
//    							producerLocal.send(message_producer_local);
//    						}
//    						else {
//    							int processIDIndex = returnIndexOfProcessIDOfInstancesReadyToBeStarted(((Activity_Ready) localObject).getProcessID());
//    							if(processIDIndex != -1) {
//    								ArrayList<?> list = instancesReadyToBeStarted.get(processIDIndex);
//    							
//    								if (Constants.DEBUG_LEVEL > 0) {
//    									System.out.println("SynchronizationMiddleware - Starting a replicated workflow instance.");
//    								}
//    								String replicatedWorkflowID = ((ArrayList<?>) list).get(1).toString();
//    								int replicationDegree = ((Integer)((ArrayList<?>) list).get(2));
//    								int engineNumber = ((Integer)((ArrayList<?>) list).get(3));
//    								boolean planningMaster;
//    								if(((ArrayList<?>) list).get(4).toString().equals("true")) {
//    									planningMaster = true;
//    									String newLine = System.getProperty("line.separator");
//    									if (Constants.DEBUG_LEVEL > 0) {
//    										System.out.println("SynchronizationMiddleware - Creating new Instance for replicated workflow " + replicatedWorkflowID + " and process " + ((Activity_Ready) localObject).getProcessID() + " with replication grade " + replicationDegree + "." + newLine 
//    													+ "My role is " + engineNumber + " and I am the planning master.");
//    									}
//    								}
//    								else {
//    									planningMaster = false;
//    									String newLine = System.getProperty("line.separator");
//    									if (Constants.DEBUG_LEVEL > 0) {
//    										System.out.println("SynchronizationMiddleware - Creating new Instance for replicated workflow " + replicatedWorkflowID + " and process " + ((Activity_Ready) localObject).getProcessID() + " with replication grade " + replicationDegree + "." + newLine 
//    													+ "My role is " + engineNumber + " and I am not the planning master.");
//    									}
//    								}
//    								int heartbeatTime = ((Integer)((ArrayList<?>) list).get(5));
//    								int timeout = ((Integer)((ArrayList<?>) list).get(6));
//    								boolean enginesEqualToReplicationDegree = ((Boolean)((ArrayList<?>) list).get(7));
//    								boolean error = ((Boolean)((ArrayList<?>) list).get(8));
//    								createNewInstanceOfSynchronizationController(myIP, middlewareIP, ((Activity_Ready) localObject).getProcessID(), replicatedWorkflowID, replicationDegree, engineNumber, planningMaster, (Activity_Ready) localObject, heartbeatTime, timeout, enginesEqualToReplicationDegree, error);
//    								instancesReadyToBeStarted.remove(processIDIndex);
//    								processIDOfInstancesReadyToBeStarted.remove(processIDIndex);
//    								
//    							}
//    						}
//    					}
//    					//the message is an instance of Instance_Suspended
//    					else if(localObject instanceof Instance_Suspended) {
//    						if(checkInvokes(((Instance_Suspended) localObject).getProcessID())){
//    							invokes.remove(((Instance_Suspended) localObject).getProcessID());
//    						}
//    					}
//    					
//    					localObject = null;
//    					
//    					//receiveFinishFromSynchronizationController();
//    					
//    					//send a heartbeat to the workflow replication middleware ever so often
//    					if(new Date().getTime() > (heartbeat + 1000000)) {
//    				        ObjectMessage message_heartbeat = sessionMiddleware.createObjectMessage(myIP);           
//    				        producerMiddleware.send(message_heartbeat);
//    				        heartbeat = new Date().getTime();
//    					}
//    				}
//    				//we close all connections
//    				closeConnections();
//    				
//    			}
//    			catch(Exception e) {
//	                System.out.println("Caught: " + e);
//	                e.printStackTrace();
//    			}
//    		}   		   		
//    	}    	
//    }	
//}
package org.apache.ode.bpel.extensions.sync;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.OperationClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.lang.SystemUtils;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Instantiated;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Skipped;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Running;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
//import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import java.io.BufferedReader;
//import java.io.BufferedWriter;
import java.io.FileReader;
//import java.io.FileWriter;
import java.io.IOException;
//import java.net.URL;

/**
 * A class responsible for supervising communication between workflows and instances of SynchronizationController.
 * 
 * @author krawczls
 *
 */
public class SynchronizationUnit {
		
	public static Connection connectionLocal = null;
	public static Connection connectionMiddleware = null;
	
	public static HashMap<String, String> hAWKSControllerMappingReplicatedWorkflowIDToIP = new HashMap<String, String>();
	public static HashMap<String, MessageProducer> hAWKSControllerIPMappingToMessageProducers = new HashMap<String, MessageProducer>();
	public static HashMap<String, Session> hAWKSControllerIPMappingToSession = new HashMap<String, Session>();
	
    /**
     * A method that initializes a new SynchronizationMiddleware instance, if necessary.
     * 
     * @param arg
     */
    public void init(String arg1, String arg2, String arg3) {
    	if(Constants.DEBUG_LEVEL > 0) {
    		System.out.println("SynchronizationMiddleware.init");
    	}
    	try{
    		if(arg1.equals("true")) { 
    			thread(new SynchronizationMiddlewareThread(arg2, arg3), false);
    			Thread.sleep(1000);
    		}
    	}
    	catch(Exception e) {
    		System.out.println("SynchronizationMiddlewareThread could not be started.");
    	}
    }
    
    
    /**
     * A method for initializing and starting a thread.
     * 
     * @param runnable
     * @param daemon
     */
    public static void thread(Runnable runnable, boolean daemon) {
    	if(Constants.DEBUG_LEVEL > 0) {
    		System.out.println("SynchronizationMiddleware.thread");
    	}
    	Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
    }
    
    
    /**
     * A thread that is resposible for relaying messages from specific workflows to respective SynchronizationController threads.
     * 
     * @author krawczls
     *
     */
    public static class SynchronizationMiddlewareThread implements Runnable {
    	
    	//variables for local connection
		Session sessionLocal = null;
		MessageProducer producerLocal = null;
		MessageConsumer consumerLocal = null;
		
		Serializable localObject = null;
		
		//variables for connection with the middleware
		Session sessionMiddleware = null;
		//MessageProducer producerMiddleware = null;
		//MessageConsumer consumerHAWKSController = null;
		
		Serializable middlewareObject = null;
		
		//TODO Read from File
		//ArrayList<SynchronizationControllerRepresentation> controllers = new ArrayList<SynchronizationControllerRepresentation>();
		
		ArrayList<Long> nonReplicatedExecutions = new ArrayList<Long>();
		
		ArrayList<ArrayList<?>> instancesReadyToBeStarted = new ArrayList<ArrayList<?>>();
		ArrayList<Long> processIDOfInstancesReadyToBeStarted = new ArrayList<Long>();
		
		String myIP;
		//String hAWKSControllerIP;
		ArrayList<String> hAWKSControllerIPs = new ArrayList<String>();
		//HashMap<String, String> hAWKSControllerIPMappingToReplicatedWorkflowIDs = new HashMap<String, String>();
		//HashMap<String, MessageProducer> hAWKSControllerIPMappingToMessageProducers = new HashMap<String, MessageProducer>();
		HashMap<String, MessageConsumer> hAWKSControllerIPMappingToMessageConsumers = new HashMap<String, MessageConsumer>();
		int producerIndex = 0;
		
		long heartbeat = (new Date()).getTime();
		
		HashMap<String, ArrayList<Object>> threadQueues = new HashMap<String, ArrayList<Object>>();
				
		// Translating processIDs to replicatedworkflowIDs
		HashMap<Long,String> processIDToReplicatedWorkflowID;
		
		ConfigReader configReader;
		int threadPoolSize = 1;
		int waitTimeForInputQueueToContainMessage = 0;
		int viewchangeCheckInterval = 25;
		int checkForWorkflowMessageInterval = 1;
		int checkForMiddlewareMessageInterval = 10; 
		
		
		public SynchronizationMiddlewareThread(String myIP, String middlewareIP) {
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddlewareThread::SynchronizationMiddlewareThread");
			}
			this.myIP = myIP;
			//this.hAWKSControllerIP = middlewareIP;
			processIDToReplicatedWorkflowID = new HashMap<Long,String>();
		}
		
    	/**
    	 * A method that sets up the connection to the local ode and registers the event "Activity_Ready" as blocking.
    	 * It also sets up the connection to the other execution engines, creates a class for every execution engine
    	 * and saves them in a list.
    	 * 
    	 * @throws Exception
    	 */
    	public void setupConnections() throws JMSException {
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::Setup Connections");
			}
    		setupConnectionsToODEAndMakeActivityReadyBlocking();
		    setupConnectionsToHAWKSController();
    	}
    	
    	/**
    	 * Setting up the connections for sending messages to and receiving messages from the HAWKS Controller.
    	 * 
    	 * @throws JMSException
    	 */
    	private void setupConnectionsToHAWKSController() throws JMSException {
    		 if (Constants.DEBUG_LEVEL > 0) {
 				System.out.println("SynchronizationMiddleware::setupConnectionsToHAWKSController");
 			}
    		for(int i = 0; i < hAWKSControllerIPs.size(); i++) { 
	    		//setting up middleware connections
		    	ActiveMQConnectionFactory connectionFactoryMiddleware = new ActiveMQConnectionFactory("tcp://" + hAWKSControllerIPs.get(i) + ":61616?jms.prefetchPolicy.queuePrefetch=100000");     
		
			    connectionMiddleware = connectionFactoryMiddleware.createConnection();
			    connectionMiddleware.start();
			    sessionMiddleware = connectionMiddleware.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
			    Destination destinationMiddleware = sessionMiddleware.createQueue("de.unistuttgart.rep");
			    Destination sourceMiddleware = sessionMiddleware.createQueue("de.unistuttgart.rep." + myIP);
			         
			    MessageProducer producerMiddleware = sessionMiddleware.createProducer(destinationMiddleware);
			    
			    hAWKSControllerIPMappingToSession.put(hAWKSControllerIPs.get(i), sessionMiddleware);
			    hAWKSControllerIPMappingToMessageProducers.put(hAWKSControllerIPs.get(i), producerMiddleware);
			    
			    //registering workflow engine with the middleware
			    ObjectMessage registerWorkflowEngine = sessionMiddleware.createObjectMessage(myIP);           
			    producerMiddleware.send(registerWorkflowEngine);  
		
			    MessageConsumer consumerHAWKSController = sessionMiddleware.createConsumer(sourceMiddleware);
			    
			    hAWKSControllerIPMappingToMessageConsumers.put(hAWKSControllerIPs.get(i), consumerHAWKSController);
    		}
    	}

		/**
    	 * Setting up the connections for sending events to and receiving events from the ODE.
    	 * 
    	 * @throws JMSException
    	 */
    	private void setupConnectionsToODEAndMakeActivityReadyBlocking() throws JMSException {
   		 if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::setupConnectionsToODEAndMakeActivityReadyBlocking");
			}
    		//setting up local connections
    		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616?jms.prefetchPolicy.queuePrefetch=100000");       
	        connectionLocal = connectionFactory.createConnection();
	        connectionLocal.start();
	        sessionLocal = connectionLocal.createSession(false, Session.AUTO_ACKNOWLEDGE);
	            
	        //setting up communication with the ode
	        //(check in org.apache.ode.bpel.extensions.comm.Communication.java)
	        Destination destinationLocal = sessionLocal.createQueue("org.apache.ode.in");
	        Destination sourceLocal = sessionLocal.createTopic("org.apache.ode.events");

	        if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::setupConnectionsToODEAndMakeActivityReadyBlocking::Create Producer and Consumer");
			}
	        //creating message producer for ode
	        producerLocal = sessionLocal.createProducer(destinationLocal);
	        //creating message consumer for ode 
	        consumerLocal = sessionLocal.createConsumer(sourceLocal); 
	        
	        if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::setupConnectionsToODEAndMakeActivityReadyBlocking::Send request registration information message to ODE");
			}
	        //send request registration information message to ode
	        RequestRegistrationInformation requestRegistrationMessageLocal = new RequestRegistrationInformation();			
	        ObjectMessage requestProducerLocal = sessionLocal.createObjectMessage(requestRegistrationMessageLocal);
	        requestProducerLocal.setJMSReplyTo(sourceLocal);           
	        producerLocal.send(requestProducerLocal);   			
       
	        consumerLocal.receive();
					
			RegisterRequestMessage registerRequestMessage = new RegisterRequestMessage(); 

			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::setupConnectionsToODEAndMakeActivityReadyBlocking::Set Activity_Ready to be blocking");
			}
			//set Activity_Ready to be blocking
			Requested_Blocking_Events blockedEvents = registerRequestMessage.new Requested_Blocking_Events();
			blockedEvents.Activity_Ready = true;
			registerRequestMessage.setGlobalEventBlockings(blockedEvents);
			
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SynchronizationMiddleware::setupConnectionsToODEAndMakeActivityReadyBlocking::Send register request message to ODE");
			}
			//send register request message to ode
	        ObjectMessage registerProducerLocal = sessionLocal.createObjectMessage(registerRequestMessage);
	        registerProducerLocal.setJMSReplyTo(sourceLocal);
	        producerLocal.send(registerProducerLocal);	
		}

		/**
    	 * A procedure that empties the log file and deletes the old data from
    	 * previous tests. Check if it works.
    	 * 
    	 */
    	public void emptyLogFile() {
    		try {
    			File file;
    			if(SystemUtils.IS_OS_UNIX) {
    				file = new File("/home/ubuntu/logs/log.txt");
    			}
    			else {
    				file = new File("log.txt");
    			}
    			if(file.exists()) {
        			FileWriter fWriter = new FileWriter(file.getAbsoluteFile());
        			BufferedWriter bWriter = new BufferedWriter(fWriter);
        			bWriter.write("");
        			bWriter.close();
    			}
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    	}
    	
    	/**
    	 * A function that returns a message it receives from the ode/a running workflow instance.
    	 * 
    	 * @return
    	 * @throws Exception
    	 */
    	public Serializable receiveMessageFromWorkflow() throws Exception {
        	if(Constants.DEBUG_LEVEL > 0) {
        		System.out.print("SUCommComp::Check for workflow events");
        	}
        	Message message;
        	if(checkForWorkflowMessageInterval < 1) {
        		message = consumerLocal.receive(1);
        	}
        	else {
    			message = consumerLocal.receive(checkForWorkflowMessageInterval);
        	}
            if(message != null){
            	ObjectMessage messageLocal = (ObjectMessage) message;
            	if(Constants.DEBUG_LEVEL > 0) {
            		System.out.print("SUCommComp::Received event from workflow: " + messageLocal.toString());
            	}
            	return messageLocal.getObject();
            }
            return null;
    	}
    	
    	/**
    	 * A function that returns a message it receives from the HAWKS Controller.
    	 * 
    	 * @return
    	 * @throws Exception
    	 */
    	public Serializable receiveMessageFromHAWKSController(int index) throws Exception {
        	if(Constants.DEBUG_LEVEL > 0) {
        		System.out.print("SUCommComp::Check for messages from the HAWKS controller");
        	}
        	Message message;
        	String ip = hAWKSControllerIPs.get(index);
        	MessageConsumer consumer = hAWKSControllerIPMappingToMessageConsumers.get(ip);
        	if(checkForMiddlewareMessageInterval < 1) {
        		message = consumer.receive(1);
        	}
        	else {
    			message = consumer.receive(checkForMiddlewareMessageInterval);
        	}
    		if(message != null) {
    			ObjectMessage messageHAWKSController = (ObjectMessage) message;
    			if(messageHAWKSController.getObject() instanceof SynchronizationMessage) {
    				SynchronizationMessage synchMessage = (SynchronizationMessage) messageHAWKSController.getObject(); 
    				if(Constants.DEBUG_LEVEL > 0) {
    					System.out.println("SynchronizationUnit received: " + synchMessage.toString());
    				}
					if(synchMessage.get_message().equals("Master")) {
						if(Constants.DEBUG_LEVEL > 0) {
							System.out.println(synchMessage.get_replicated_workflow_id());
						}
					}
    			}
            	if(Constants.DEBUG_LEVEL > 0) {
            		System.out.print("SUCommComp::Received message from the HAWKS Controller: " + messageHAWKSController.toString());
            	}
    			return messageHAWKSController.getObject();
    		}
    		return null;
    	}
    	    	
    	/**
    	 * Check whether the id is contained in the nonReplicatedExecutions.
    	 * @param id
    	 * @return Return whether nonReplicatedExecutions contains the id.
    	 */
    	public boolean checkNonReplicatedExecutionsContain(Long id) {
    		return nonReplicatedExecutions.contains(id);
    	}
    	
    	/**
    	 * A method for closing the connections.
    	 * 
    	 * @throws Exception
    	 */
    	public void closeConnections() throws Exception {
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("SynchronizationMiddleware::closeConnections");
    		}
            // Clean up
            sessionLocal.close();
            connectionLocal.close();
    	}
    	
    	/**
    	 * A helper method for reading my ip and the ip of the middleware/s.
    	 */
    	public void readIPs() {  	
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("SynchronizationUnit::readIPsAndThreadPoolSize");
    		}
    		BufferedReader reader = null;
    		try {
    			//URL url = getClass().getResource("ip.txt");
    			//reader = new BufferedReader(new FileReader (url.getPath()));
    			if(SystemUtils.IS_OS_UNIX) {
    				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/ip.txt"));
    			}
    			else {
    				reader = new BufferedReader(new FileReader ("ip.txt"));
    			}
    			myIP = reader.readLine();
    			String ip = reader.readLine();
    			hAWKSControllerIPs.add(ip);
    			
    			while((ip = reader.readLine()) != null) {
	    			hAWKSControllerIPs.add(ip); 
	    			if (Constants.DEBUG_LEVEL > 0) {
	    				System.out.println("SynchronizationUnit::readIPsAndThreadPoolSize::My IP is: " + myIP);
	        			System.out.println("SynchronizationUnit::readIPsAndThreadPoolSize::The HAWKS Controller IP is: " + ip);
	    			}
    			}
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    		finally {
    			try {
    				if(reader != null) {
    					reader.close();
    				}
    			}
    			catch(IOException e) {
    				e.printStackTrace();
    			}
    		}	
    	}
    	
    	/**
    	 * A helper method for reading my ip. 
    	 */
    	public void readMyIP() {  	
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("SynchronizationUnit::readMyIP");
    		}
    		BufferedReader reader = null;
    		try {
    			//URL url = getClass().getResource("ip.txt");
    			//reader = new BufferedReader(new FileReader (url.getPath()));
    			if(SystemUtils.IS_OS_UNIX) {
    				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/ip.txt"));
    			}
    			else {
    				reader = new BufferedReader(new FileReader ("ip.txt"));
    			}
    			myIP = reader.readLine();

    			if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("SynchronizationUnit::readMyIP::My IP is: " + myIP);
    			}
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    		finally {
    			try {
    				if(reader != null) {
    					reader.close();
    				}
    			}
    			catch(IOException e) {
    				e.printStackTrace();
    			}
    		}	
    	}
    	
    	/**
    	 *  A helper method for reading the ip of the middleware/s.
    	 */
    	public void readMiddlewareIPs() {  	
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("SynchronizationUnit::readMiddlewareIPs");
    		}
    		BufferedReader reader = null;
    		try {
    			//URL url = getClass().getResource("ip.txt");
    			//reader = new BufferedReader(new FileReader (url.getPath()));
    			if(SystemUtils.IS_OS_UNIX) {
    				reader = new BufferedReader(new FileReader ("/home/ubuntu/configs/middlewareip.txt"));
    			}
    			else {
    				reader = new BufferedReader(new FileReader ("middlewareip.txt"));
    			}
    			String ip = reader.readLine();
    			hAWKSControllerIPs.add(ip);
    			
    			while((ip = reader.readLine()) != null) {
	    			hAWKSControllerIPs.add(ip); 
	    			if (Constants.DEBUG_LEVEL > 0) {
	        			System.out.println("SynchronizationUnit::readMiddlewareIPs::The HAWKS Controller IP is: " + ip);
	    			}
    			}
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    		finally {
    			try {
    				if(reader != null) {
    					reader.close();
    				}
    			}
    			catch(IOException e) {
    				e.printStackTrace();
    			}
    		}	
    	}
    	
    	/**
    	 * A function that searches the instancesReadyToBeStarted ArrayList, to return the first position
    	 * at which a workflow model is saved, that matches the processName and hasn't been marked for execution yet.
    	 * Returns -1 if nothing appropriate could be found. 
    	 * 
    	 * @param processName
    	 * @return
    	 */
    	public int returnIndexOfInstancesReadyToBeStarted(String processName) {
    		for(int i = 0; i < instancesReadyToBeStarted.size(); i++) {
    			if(((ArrayList<?>) instancesReadyToBeStarted.get(i)).get(0).toString().equals(processName) &&
    					processIDOfInstancesReadyToBeStarted.get(i).equals(-1L)) {
    				return i;
    			}
    		}
    		return -1;
    	}
    	
    	/**
    	 * A function that searches the processIDOfInstancesToBeStarted ArrayList, to return the position of 
    	 * the matching processID.
    	 * 
    	 * @param processID
    	 * @return
    	 */
    	public int returnIndexOfProcessIDOfInstancesReadyToBeStarted(Long processID) {
    		for(int i = 0; i < processIDOfInstancesReadyToBeStarted.size(); i++) {
    			if(processIDOfInstancesReadyToBeStarted.get(i).equals(processID)) {
    				return i;
    			}
    		}
    		return -1;
    	}
    	
    	/* (non-Javadoc)
    	 * @see java.lang.Runnable#run()
    	 */
    	public void run() {
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("SynchronizationMiddleware::run");
    		}
    		//initialize SynchronizationController threads
    		//read parameters from ip and config file
    		//readIPs();
    		//krawczls: separated the reading of ip's to easier configure the engine setup
    		readMyIP();
    		readMiddlewareIPs();
    		ConfigReader configReader = new ConfigReader();
    		threadPoolSize = configReader.getThreadPoolSize();
    		waitTimeForInputQueueToContainMessage = configReader.getWaitTimeForInputQueueToContainMessage();
    		viewchangeCheckInterval = configReader.getViewChangeCheckInterval();
    		checkForWorkflowMessageInterval = configReader.getCheckForWorkflowMessageInterval();
    		checkForMiddlewareMessageInterval = configReader.getCheckForMiddlewareMessageInterval();
    		
    		ArrayList<SynchronizationUnitThread> threadPool = new ArrayList<SynchronizationUnitThread>();
    		ArrayList<ArrayList<Object>> queues = new ArrayList<ArrayList<Object>>();
    		for(int i = 0; i < threadPoolSize; i++) {
    			if (Constants.DEBUG_LEVEL > 0) {
    				System.out.println("SynchronizationMiddleware::run::Starting SyncUnitThread " + i);
    			}
    			synchronized (queues) {
    				queues.add(new ArrayList<Object>());
    			}
    			SynchronizationUnitThread curSyncUnitThread = new SynchronizationUnitThread(queues.get(i), waitTimeForInputQueueToContainMessage, viewchangeCheckInterval);
    			threadPool.add(curSyncUnitThread);
//    			threadPool[i] = new SynchronizationControllerThread(queues.get(i));
    			try {
					new Thread(curSyncUnitThread).start();
				} catch (Exception e) {
					System.out.println("SynchronizationMiddleware::run - Failed to initialize a new thread.");
					e.printStackTrace();
				}
    		}		
    		if (Constants.DEBUG_LEVEL > 0) {
    			System.out.println("ThreadPoolSize: " + threadPoolSize + ", Number of queues: " + queues.size() + "\n Should be equal!\n\n");
    		}
    		int currentThreadCounter = 0;
    		
    		synchronized(this) {
    			try {
    				setupConnections();
    				emptyLogFile();
    				
    				boolean isThreadNeeded = true;
    				while(isThreadNeeded) {
    					if(middlewareObject == null) {
    						middlewareObject = receiveMessageFromHAWKSController(producerIndex);
    					}  					
    					//An ArrayList object represents the various information, the middleware sends us and 
    					//we need to execute a new replicated workflow instance, started by the middleware
    					if(middlewareObject instanceof ArrayList) {
    						//TODO check if this works
    						instancesReadyToBeStarted.add((ArrayList<?>) middlewareObject);
    						processIDOfInstancesReadyToBeStarted.add(-1L);
    						String replicatedWorkflowID = ((ArrayList<?>) middlewareObject).get(1).toString();
    						hAWKSControllerMappingReplicatedWorkflowIDToIP.put(replicatedWorkflowID, hAWKSControllerIPs.get(producerIndex));
    						/*sendSOAPToService((String)((ArrayList<?>) middlewareObject).get(7), (String)((ArrayList<?>) middlewareObject).get(8));
    						ArrayList<String> writeToLogArray = new ArrayList<String>();
    						String master;
    						if(((String)((ArrayList<?>) middlewareObject).get(4)).equals("true")) {
    							master = "|--M |";
    						}
    						else {
    							master = "|--F |";
    						}
    						writeToLogArray.add(myIP 
    			        			+ "|" + ((ArrayList<?>) middlewareObject).get(1).toString() 
    			        			+ "|" + (Integer)((ArrayList<?>) middlewareObject).get(2) 
    			        			+ master + "instance_started" 
    			        			+ "|-|" + (new Date()).getTime() 
    			        			+ "|" + (Boolean)((ArrayList<?>) middlewareObject).get(9) );
    			        	SynchronizationUnitLogWriter.getInstance().writeToLogFile(writeToLogArray);*/ 						
    					} else if (middlewareObject instanceof Boolean) {
    						for(int i = 0; i < threadPoolSize; i++) {
    							threadPool.get(i).isSimulateFailure = true;
    						}		
    					} else if (middlewareObject instanceof SynchronizationMessage){
	    					SynchronizationMessage message = (SynchronizationMessage) middlewareObject;
	    					String ID = message.get_replicated_workflow_id();
	    					ArrayList<Object> queue = threadQueues.get(ID);

    						if (queue == null) {
    							//TODO check if new logic works
    							//add message to the back of the queue again
    							Destination destinationBackToTheStart = sessionMiddleware.createQueue("de.unistuttgart.rep." + myIP);
    							MessageProducer producerBackToTheStart = sessionMiddleware.createProducer(destinationBackToTheStart);
    							ObjectMessage messageBackToTheStart = sessionMiddleware.createObjectMessage(message); 
    							producerBackToTheStart.send(messageBackToTheStart);
    							/*queue = addThreadQueue(queues, ID, currentThreadCounter++);
    							//TODO @krawczls: check if this is bad
    							if(currentThreadCounter >= threadPoolSize) {
    								currentThreadCounter = 0;
    							}*/
    						}
    						else {
    							synchronized(queue) {
    								queue.add(message);
    								queue.notifyAll();
    							}
    						}
    					}
    					middlewareObject = null;
    					
    					producerIndex++;
    					if(producerIndex >= hAWKSControllerIPs.size()) {
    						producerIndex = 0;
    					}
    					
    					//trying to receive a new message from the ode
    					localObject = receiveMessageFromWorkflow();
    					
    					if(localObject instanceof Instance_Running){
    						//the message is an instance of Instance_Running
        					//a new SynchronizationController instance needs to be initialized
    						handleInstanceRunningEvent((Instance_Running) localObject);
    					} else if(localObject instanceof Activity_Ready) {  
        					//the message is an instance of Activity_Ready 	
    						if (handleActivityReady((Activity_Ready) localObject, threadPool, queues, currentThreadCounter)) {
    							currentThreadCounter++;
    							if(currentThreadCounter >= threadPoolSize) {
    								currentThreadCounter = 0;
    							}
    						}
    					} else if(localObject instanceof Instance_Suspended) {
    						//the message is an instance of Instance_Suspended
    						handleInstanceSuspended((Instance_Suspended) localObject);
    					} else if(localObject instanceof InstanceEventMessage 
    							&& !(localObject instanceof Process_Instantiated)) {
    						// the message is an InstanceEventMessage, but not Process_Instantiated (which ignore on purpose!)
    						handleInstanceEventMessage((InstanceEventMessage) localObject);
    					} 	
    					localObject = null;
    					
    					//send a heartbeat to the workflow replication middleware ever so often
    					if(new Date().getTime() > (heartbeat + 120000)) { // Send Engine Heartbeat every 2 minutes
    						for(int i = 0; i < hAWKSControllerIPs.size(); i++) {
    				        	ObjectMessage message_heartbeat = sessionMiddleware.createObjectMessage(myIP);
    				        	(hAWKSControllerIPMappingToMessageProducers.get(hAWKSControllerIPs.get(i))).send(message_heartbeat);
    				        }
    				        heartbeat = new Date().getTime();
    					}
    				}
    				//we close all connections
    				closeConnections();
    				
    			}
    			catch(Exception e) {
	                System.out.println("Caught: " + e);
	                e.printStackTrace();
    			}
    		}   		   		
    	}

    	/**
    	 * Handle InstanceEventMessage
    	 * 
    	 * @param instanceEventMessage
    	 */
    	private void handleInstanceEventMessage(InstanceEventMessage instanceEventMessage) {
			String ID = String.valueOf(instanceEventMessage.getProcessID());
			ArrayList<Object> queue = threadQueues.get(ID);
			if (queue == null) {
				queue = addEventQueue(ID);
			}
			if (queue != null) {
				synchronized(queue) {
					queue.add(instanceEventMessage);
					queue.notifyAll();
				}
			}
		}

		/**
    	 * Handle Instance_Suspended event
    	 * @param instanceSuspended
    	 * @param currentThreadCounter
    	 */
    	private void handleInstanceSuspended(Instance_Suspended instanceSuspended) {
    		if(checkNonReplicatedExecutionsContain(instanceSuspended.getProcessID())){
				nonReplicatedExecutions.remove(instanceSuspended.getProcessID());
			}
			else {
				InstanceEventMessage message = (InstanceEventMessage) instanceSuspended;
				String ID = String.valueOf(message.getProcessID());
				ArrayList<Object> queue = threadQueues.get(ID);
				if (queue == null) {
					queue = addEventQueue(ID);
				}
				if (queue != null) {
					synchronized(queue) {
						queue.add(message);
						queue.notifyAll();
					}
				}
			}
		}

		/** 
    	 * Handle Activity Ready
    	 * @param activityReady
    	 * @param threadPool
    	 * @param queues 
		 * @throws Exception 
    	 */
    	private boolean handleActivityReady(Activity_Ready activityReady, 
    			ArrayList<SynchronizationUnitThread> threadPool,
    			ArrayList<ArrayList<Object>> queues, 
    			int currentThreadCounter) throws Exception {
    		boolean isIncrementCurrentThreadCounter = false;
    		if(checkNonReplicatedExecutionsContain(activityReady.getProcessID())){
    			// If the workflow execution is not replicated, handle the activity ready here
				Start_Activity start_message = new Start_Activity();
				start_message.setReplyToMsgID(activityReady.getMessageID());
			
				ObjectMessage message_producer_local = sessionLocal.createObjectMessage(start_message);
				producerLocal.send(message_producer_local);
			} else if(returnIndexOfProcessIDOfInstancesReadyToBeStarted(activityReady.getProcessID()) != -1){
				// This is the first Activity_Ready event, the management needs to be initialized accordingly
				int processIDIndex = returnIndexOfProcessIDOfInstancesReadyToBeStarted(activityReady.getProcessID());
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println(processIDIndex);
				}
				ArrayList<?> list = instancesReadyToBeStarted.get(processIDIndex);
				
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("SUCommComp::handleActivityReady - Starting a replicated workflow instance.");
				}
				String replicatedWorkflowID = ((ArrayList<?>) list).get(1).toString();
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("#" + replicatedWorkflowID);
				}
				int replicationDegree = ((Integer)((ArrayList<?>) list).get(2));
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println(replicationDegree);
				}
				int engineNumber = ((Integer)((ArrayList<?>) list).get(3));
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println(engineNumber);
				}
				boolean planningMaster;
				if(((ArrayList<?>) list).get(4).toString().equals("true")) {
					planningMaster = true;
					String newLine = System.getProperty("line.separator");
					if (Constants.DEBUG_LEVEL > 0) {
						System.out.println("SUCommComp::handleActivityReady - Creating new Instance for replicated workflow " + replicatedWorkflowID 
								+ " and process " + activityReady.getProcessID() 
								+ " with replication grade " + replicationDegree + "." + newLine 
								+ "My role is " + engineNumber + " and I am the planning master.");
					}
				}
				else {
					planningMaster = false;
					String newLine = System.getProperty("line.separator");
					if (Constants.DEBUG_LEVEL > 0) {
						System.out.println("SUCommComp::handleActivityReady - Creating new Instance for replicated workflow " 
								+ replicatedWorkflowID + " and process " + activityReady.getProcessID() 
								+ " with replication grade " + replicationDegree + "." + newLine 
								+ "My role is " + engineNumber + " and I am not the planning master.");
					}
				}
				int heartbeatRate = ((Integer)((ArrayList<?>) list).get(5));
				int timeout = ((Integer)((ArrayList<?>) list).get(6));
				System.out.println("This is the timeout: " + timeout);
				boolean enginesEqualToRepDegree = ((Boolean)((ArrayList<?>) list).get(7));
				boolean error = ((Boolean)((ArrayList<?>) list).get(8));
				
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("Add a new ControllerObject to SyncUnitThread " + currentThreadCounter);
				}
				synchronized (threadQueues) {
					ArrayList<Object> testQueue = threadQueues.get(replicatedWorkflowID);
					if(testQueue == null) {
						if (Constants.DEBUG_LEVEL > 0) {
							System.out.println("*1" + currentThreadCounter); //0
							System.out.println("*2" + queues.size()); //0
						}
						threadQueues.put(replicatedWorkflowID, queues.get(currentThreadCounter));
						threadQueues.put(String.valueOf(activityReady.getProcessID()), queues.get(currentThreadCounter));
					}
					else {
						threadQueues.put(String.valueOf(activityReady.getProcessID()), testQueue);
					}
				}
				
				/*ArrayList<String> writeToLogArray = new ArrayList<String>();
				String master;
				if(planningMaster) {
					master = "|--M |";
				}
				else {
					master = "|--F |";
				}
				writeToLogArray.add(myIP 
	        			+ "|" + ((ArrayList<?>) list).get(1).toString() 
	        			+ "|" + (Integer)((ArrayList<?>) list).get(2) 
	        			+ master + "activity_ready" 
	        			+ "|-|" + (new Date()).getTime() 
	        			+ "|" + (Boolean)((ArrayList<?>) list).get(9));
	        	SynchronizationUnitLogWriter.getInstance().writeToLogFile(writeToLogArray);*/
	        	
				threadPool.get(currentThreadCounter).addNewWorkflowInstance(myIP, 
						hAWKSControllerMappingReplicatedWorkflowIDToIP.get(replicatedWorkflowID), 
						((Activity_Ready) activityReady).getProcessID(), 
						replicatedWorkflowID, 
						replicationDegree, 
						engineNumber, 
						planningMaster, 
						(Activity_Ready) activityReady, 
						heartbeatRate, 
						timeout, 
						enginesEqualToRepDegree, 
						error);
				
				
				//createNewInstanceOfSynchronizationController(myIP, middlewareIP, ((Activity_Ready) localObject).getProcessID(), replicatedWorkflowID, replicationDegree, engineNumber, planningMaster, (Activity_Ready) localObject);
				isIncrementCurrentThreadCounter = true;
				
				instancesReadyToBeStarted.remove(processIDIndex);
				processIDOfInstancesReadyToBeStarted.remove(processIDIndex);
				
				
				processIDToReplicatedWorkflowID.put(activityReady.getProcessID(), replicatedWorkflowID);
//				replicatedWorkflowIDList.set(processIDIndex, replicatedWorkflowID);
//				processIDList.set(processIDIndex, activityReady.getProcessID()); 
				
				
				
			} else { 
				String ID = String.valueOf(activityReady.getProcessID());
				ArrayList<Object> queue = threadQueues.get(ID);
				if (queue == null) {
					queue = addEventQueue(ID);
				}
				if (queue != null) {
					synchronized(queue) {
						queue.add(activityReady);
						queue.notifyAll();
					}	
				}
			}
    		return isIncrementCurrentThreadCounter;
		}

		/**
    	 * Handles the Instance_Running event.
    	 * @param instanceRunning
    	 */
		private void handleInstanceRunningEvent(Instance_Running instanceRunning) {
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println(new Date().getTime());
				System.out.println("*" + instanceRunning.getProcessName().toString() + "*");
			}
			int processIDIndex = returnIndexOfInstancesReadyToBeStarted(instanceRunning.getProcessName().toString());
			if(processIDIndex == -1) {
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("SynchronizationMiddleware - Starting a non-replicated workflow instance.");
				}
				nonReplicatedExecutions.add(instanceRunning.getProcessID());
			} else {
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("SynchronizationMiddleware - Marking replicated workflow " 
							+ instanceRunning.getProcessID() + " instance for execution at index " 
							+ processIDIndex + ".");
				}
				processIDOfInstancesReadyToBeStarted.set(processIDIndex, instanceRunning.getProcessID());
			}
		}

		/**
		 * If the queue for a processID does not yet exist in the threadQueues HashMap,
		 * it is added. However, only if the corresponding workflowReplicationID is available.
		 * If that is not yet the case, then the queue cannot be added at this point in time and
		 * null is returened.
		 * 
		 * @param processID
		 * @return The added queue or null.
		 */
		private ArrayList<Object> addEventQueue(String processID) {
			String replicatedWorkflowID = processIDToReplicatedWorkflowID.get(Long.valueOf(processID));
			ArrayList<Object> queue = null;
			if (replicatedWorkflowID != null) {
				synchronized (threadQueues) {
					queue = threadQueues.get(replicatedWorkflowID);
					if (queue != null) {
						threadQueues.put(processID, queue);
					}
				}
			}
			return queue;		
		}

		/**
		 * If a queue does not already exist in the threadQueues HashMap, it is added.
		 * 
		 * @param queues Possible queues that can be Added
		 * @param workflowReplicationID
		 * @param currentThreadCounter The queue (and, thus, SUThread) that is used for this replicated execution
		 * @return The added queue.
		 */
		private ArrayList<Object> addThreadQueue(ArrayList<ArrayList<Object>> queues, String workflowReplicationID, int currentThreadCounter) {
			ArrayList<Object> queue = null;
			synchronized (threadQueues) {
				queue = threadQueues.get(workflowReplicationID);
				if (queue == null) {
					threadQueues.put(workflowReplicationID, queues.get(currentThreadCounter));
					queue = threadQueues.get(workflowReplicationID);
				}
			}
			return queue;
		}    	
    
	    /**
	     * Starting the ODE service.
	     * 
	     * @param message
	     * @param url
	     * @throws Exception
	     */
	    /*public void sendSOAPToService(String message, String url) throws Exception {
	        ServiceClient serviceClient = new ServiceClient();
	        Options options = new Options();
	        options.setTo(new EndpointReference("http://localhost:8080/ode/processes/" + url));
	        serviceClient.setOptions(options);
	        MessageContext messageContext = new MessageContext();
	        OMElement omelement = AXIOMUtil.stringToOM(message);
	        Iterator<?> itr = omelement.getChildrenWithLocalName("Body");
	        while (itr.hasNext()) {
	            omelement = (OMElement)itr.next();
	        }
	        omelement = omelement.getFirstElement();
	        SOAPEnvelope soapEnvelope = TransportUtils.createSOAPEnvelope(omelement);
	        messageContext.setEnvelope(soapEnvelope);
	        OperationClient operationClient = serviceClient.createClient(ServiceClient.ANON_OUT_IN_OP);
	        operationClient.addMessageContext(messageContext);
	        operationClient.execute(false);
	    }*/
    }	
}