package org.apache.ode.bpel.extensions.sync;

import java.util.ArrayList;
import java.util.Date;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.SystemUtils;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Running;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
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
public class SynchronizationMiddleware {
		
    /**
     * A method that initializes a new SynchronizationMiddleware instance, if necessary.
     * 
     * @param arg
     */
    public void init(String arg1, String arg2, String arg3) {
    	System.out.println("SynchronizationMiddleware.init");
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
    	System.out.println("SynchronizationMiddleware.thread");
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
		Connection connection_local = null;
		Session session_local = null;
		MessageProducer producer_local = null;
		MessageConsumer consumer_local = null;
		
		Serializable local_object = null;
		
		//variables for connection with the middleware
		Connection connection_middleware = null;
		Session session_middleware = null;
		MessageProducer producer_middleware = null;
		MessageConsumer consumer_middleware = null;
		
		Serializable middleware_object = null;
		
		ArrayList<SynchronizationControllerRepresentation> Controllers = new ArrayList<SynchronizationControllerRepresentation>();
		
		ArrayList<Long> invokes = new ArrayList<Long>();
		
		ArrayList<ArrayList<?>> middlewareVariables = new ArrayList<ArrayList<?>>();
		
		String my_ip;
		String middleware_ip;
		
		int rep_grade = 0; 
		String replicated_workflow_id = null;
		int engine_number = 0;
		boolean planning_master = false;
		
		long heartbeat = (new Date()).getTime();
		
		boolean new_process = false;
		
		public SynchronizationMiddlewareThread(String myip, String middlewareip) {
			this.my_ip = myip;
			this.middleware_ip = middlewareip;
		}
		
    	/**
    	 * A method that sets up the connection to the local ode and registers the event "Activity_Ready" as blocking.
    	 * It also sets up the connection to the other execution engines, creates a class for every execution engine
    	 * and saves them in a list.
    	 * 
    	 * @throws Exception
    	 */
    	public void setupConnections() throws Exception {
    		System.out.println("setup connections");
    		//setting up local connections
    		ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://localhost:61616?jms.prefetchPolicy.queuePrefetch=0");       
	        connection_local = connection_factory.createConnection();
	        connection_local.start();
	        session_local = connection_local.createSession(false, Session.AUTO_ACKNOWLEDGE);
	            
	        //setting up communication with the ode
	        //(check in org.apache.ode.bpel.extensions.comm.Communication.java)
	        Destination destination_local = session_local.createQueue("org.apache.ode.in");
	        Destination source_local = session_local.createTopic("org.apache.ode.events");
	            
	        //creating message producer for ode
	        producer_local = session_local.createProducer(destination_local);
	        //creating message consumer for ode 
	        consumer_local = session_local.createConsumer(source_local); 

	        //send request registration information message to ode
	        RequestRegistrationInformation request_message_local = new RequestRegistrationInformation();			
	        ObjectMessage request_producer_local = session_local.createObjectMessage(request_message_local);
	        request_producer_local.setJMSReplyTo(source_local);           
	        producer_local.send(request_producer_local);   			
       
	        consumer_local.receive();
					
			RegisterRequestMessage register_message = new RegisterRequestMessage(); 

			//set Activity_Ready to be blocking
			Requested_Blocking_Events blocked_events = register_message.new Requested_Blocking_Events();
			blocked_events.Activity_Ready = true;
			register_message.setGlobalEventBlockings(blocked_events);
			
			//send register request message to ode
	        ObjectMessage register_producer_local = session_local.createObjectMessage(register_message);
	        register_producer_local.setJMSReplyTo(source_local);
	        producer_local.send(register_producer_local);	                

	        //setting up middleware connections
    		ActiveMQConnectionFactory connection_factory_middleware = new ActiveMQConnectionFactory("tcp://" + middleware_ip + ":61616?jms.prefetchPolicy.queuePrefetch=0");     

	        connection_middleware = connection_factory_middleware.createConnection();
	        connection_middleware.start();
	        session_middleware = connection_middleware.createSession(false, Session.AUTO_ACKNOWLEDGE);

	        Destination destination_middleware = session_middleware.createQueue("de.unistuttgart.rep");
	        Destination source_middleware = session_middleware.createQueue("de.unistuttgart.rep." + my_ip);
	         
	        producer_middleware = session_middleware.createProducer(destination_middleware);

	        //registering workflow engine with the middleware
	        ObjectMessage register_workflow_engine = session_middleware.createObjectMessage(my_ip);           
	        producer_middleware.send(register_workflow_engine);  

	        consumer_middleware = session_middleware.createConsumer(source_middleware);
    	}
    	
    	/**
    	 * 
    	 */
    	/*public void createLogFile() {
    		try {
    			File file;
    			if(SystemUtils.IS_OS_UNIX) {
    				file = new File("/home/lukaskrawc/logs/log.txt");
    			}
    			else {
    				file = new File("log.txt");
    			}
    			if(!file.exists()) {
    				file.createNewFile();
    			}
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    	}*/
    	
    	/**
    	 * A function that returns a message it receives from the local workflow.
    	 * 
    	 * @return
    	 * @throws Exception
    	 */
    	public Serializable receiveMessageFromWorkflow() throws Exception {
            Message message = consumer_local.receive(10);
            if(message != null){
            	ObjectMessage message_local = (ObjectMessage) message;
            	return message_local.getObject();
            }
            return null;
    	}
    	
    	/**
    	 * @return
    	 * @throws Exception
    	 */
    	public Serializable receiveMessageFromMiddleware(int time) throws Exception {
    		Message message = null;
    		if(time == 0) {
    			message = consumer_middleware.receive();
    		}
    		else {
    			message = consumer_middleware.receive(time);
    		}
    		if(message != null) {
    			ObjectMessage message_middleware = (ObjectMessage) message;
    			return message_middleware.getObject();
    		}
    		return null;
    	}
    	
    	/**
    	 * Starts a new instance of SynchronizationController, creates necessary queues for communication with the thread 
    	 * and stores them in an ArrayList.
    	 * 
    	 * @param id
    	 */
    	public void createNewInstanceOfSynchronizationController(String ip, long id, String replicated_workflow_id, int grade, int engine_number, boolean planning_master) {
    		System.out.println("SynchronizationMiddleware.createNewInstanceOfSynchronizationController");
    		try {
    			SynchronizationController controller = new SynchronizationController();
    			controller.init(ip, id, replicated_workflow_id, grade, engine_number, planning_master);
    			
    			SynchronizationControllerRepresentation controllerRepresentation = new SynchronizationControllerRepresentation(id, session_local, replicated_workflow_id);
    			Controllers.add(controllerRepresentation);
    		}
    		catch(Exception e) {
    			System.out.println("New SynchronizationController could not be instantiated.");
    		}
    	}
    	
    	/**
    	 * Sends a Serializable (received from workflow) to the correct SynchronizationController.
    	 * 
    	 * @param messageObject
    	 * @param id
    	 * @throws Exception
    	 */
    	public void sendMessageFromProcessToSynchronizationController(Serializable messageObject, long id) throws Exception {
    		for(int i = 0; i < Controllers.size(); i++) {
    			if(((SynchronizationControllerRepresentation) Controllers.get(i)).this_processID == id){
    				ObjectMessage message = session_local.createObjectMessage(messageObject);
    				((SynchronizationControllerRepresentation) Controllers.get(i)).this_producer_process.send(message);
    			}
    		}
    	}
    	
    	/**
    	 * @param messageObject
    	 * @param id
    	 * @throws Exception
    	 */
    	public void sendMessageFromMiddlewareToSynchronizationController(Serializable messageObject, String id) throws Exception {
    		for(int i = 0; i < Controllers.size(); i++) {
    			if(((SynchronizationControllerRepresentation) Controllers.get(i)).this_replicated_workflow_id.equals(id)){
    				ObjectMessage message = session_local.createObjectMessage(messageObject);
    				((SynchronizationControllerRepresentation) Controllers.get(i)).this_producer_middleware.send(message);
    			}
    		}
    	}
    	
    	/**
    	 * @param messageObject
    	 * @throws Exception
    	 */
    	public void sendReplyToMiddleware() throws Exception {
    		for(int i = 0; i < Controllers.size(); i++) {
    			Message message = ((SynchronizationControllerRepresentation) Controllers.get(i)).this_consumer_middleware.receive(10);
    			if(message != null){
    				Serializable object = ((ObjectMessage) message).getObject();
    				if(object instanceof SynchronizationMessage && ((SynchronizationMessage) object).get_message().equals("Finish")) {
						deleteSynchronizationController(((SynchronizationMessage) object).getProcessID());
				        ObjectMessage message_finished = session_middleware.createObjectMessage(new String("finished" + "*" + my_ip + "*" + ((SynchronizationMessage) object).get_replicated_workflow_id()));           
				        producer_middleware.send(message_finished);
    				}
    				else {
    					producer_middleware.send((ObjectMessage) message);
    				}
    			}
    		}
    	}
 
    	/**
    	 * A method that deletes a registered SynchronizationController thread.
    	 * This happens when the corresponding process finished execution.
    	 * 
    	 * @param id
    	 */
    	public void deleteSynchronizationController(long id) {
    		System.out.println("SynchronizationMiddleware.deleteSynchronizationController");
    		for(int i = 0; i < Controllers.size(); i++) {
    			if(((SynchronizationControllerRepresentation) Controllers.get(i)).this_processID == id){
    				Controllers.remove(i);
    			}
    		}
    	}
    	
    	/**
    	 * A method that iterates through the list of SynchronizationController threads 
    	 * and sends the first message in their queues to the workflow.
    	 * 
    	 * @throws Exception
    	 */
    	public void sendReplyToWorkflow() throws Exception {
    		for(int i = 0; i < Controllers.size(); i++) {
    			Message message = ((SynchronizationControllerRepresentation) Controllers.get(i)).this_consumer_process.receive(10);
    			if(message != null){
    				producer_local.send((ObjectMessage) message);
    			}
    		}
    	}
    	
    	/**
    	 * @return
    	 */
    	public boolean checkInvokes(Long Id) {
    		for(int i = 0; i < invokes.size(); i++) {
    			if(Id.equals((Long) invokes.get(i))) {
    				return true;
    			}
    		}
    		return false;
    	}
    	
    	/**
    	 * A method for closing the connections.
    	 * 
    	 * @throws Exception
    	 */
    	public void closeConnections() throws Exception {
    		System.out.println("SynchronizationMiddleware.closeConnections");
            // Clean up
            session_local.close();
            connection_local.close();
    	}
    	
    	/**
    	 * A helper method for reading my ip.
    	 */
    	public void readIPs() {  	
    		System.out.println("SynchronizationMiddleware.readIPs");
    		BufferedReader reader = null;
    		try {
    			//URL url = getClass().getResource("ip.txt");
    			//reader = new BufferedReader(new FileReader (url.getPath()));
    			if(SystemUtils.IS_OS_UNIX) {
    				reader = new BufferedReader(new FileReader ("/home/lukaskrawc/configs/ip.txt"));
    			}
    			else {
    				reader = new BufferedReader(new FileReader ("ip.txt"));
    			}
    			my_ip = reader.readLine();
    			System.out.println(my_ip);
    			middleware_ip = reader.readLine();
    			System.out.println(middleware_ip);
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
    	 * 
    	 * 
    	 * @param processName
    	 * @return
    	 */
    	public ArrayList<?> searchMiddlewareVariables(String processName) {
    		ArrayList<?> returnList = null;
    		for(int i = 0; i < middlewareVariables.size(); i++) {
    			if(((ArrayList<?>) middlewareVariables.get(i)).get(0).toString().equals(processName)) {
    				returnList = (ArrayList<?>) middlewareVariables.get(i);
    				middlewareVariables.remove(i);
    				return returnList;
    			}
    		}
    		return returnList;
    	}
    	
    	/* (non-Javadoc)
    	 * @see java.lang.Runnable#run()
    	 */
    	public void run() {
    		System.out.println("SynchronizationMiddleware.run");
    		synchronized(this) {
    			try {
    				readIPs();
    				setupConnections();
    				//createLogFile();
    				
    				boolean x = true;
    				while(x) {
    					if(middleware_object == null) {
    						middleware_object = receiveMessageFromMiddleware(10);
    					}  					
    					if(middleware_object instanceof SynchronizationMessage) {
    						System.out.println("synch message received");
    						sendMessageFromMiddlewareToSynchronizationController(middleware_object, ((SynchronizationMessage) middleware_object).get_replicated_workflow_id());
    						middleware_object = null;
    					}
    					else if(middleware_object instanceof ArrayList) {
    						//new_process = true;
    						middlewareVariables.add((ArrayList<?>) middleware_object);
    						middleware_object = null;
    					}
    					
    					//trying to receive a new message from the ode
    					local_object = receiveMessageFromWorkflow();

    					if(local_object != null) {
    						System.out.println(local_object.toString());
    					}
    					//the message is an instance of Instance_Running
    					//a new SynchronizationController instance needs to be initialized
    					if(local_object instanceof Instance_Running){
    						System.out.println(new Date().getTime());
    						System.out.println("*" + ((Instance_Running) local_object).getProcessName().toString() + "*");
    						//if(!new_process || !(((ArrayList<?>) middleware_object).get(0).toString().equals(((Instance_Running) local_object).getProcessName().toString()))) {
    						ArrayList<?> list = searchMiddlewareVariables(((Instance_Running) local_object).getProcessName().toString());
    						if(list == null){
    							invokes.add(((Instance_Running) local_object).getProcessID());
    						}
    						else {
    							replicated_workflow_id = ((ArrayList<?>) list).get(1).toString();
    							rep_grade = ((Integer)((ArrayList<?>) list).get(2));
    							engine_number = ((Integer)((ArrayList<?>) list).get(3));
    							System.out.println("Creating new Instance for replicated workflow " + replicated_workflow_id + " with replication grade " + rep_grade + ".");
    							if(((ArrayList<?>) list).get(4).toString().equals("true")) {
    								planning_master = true;
    								System.out.println("My role is " + engine_number + " and I am the planning master.");
    							}
    							else {
    								planning_master = false;
    								System.out.println("My role is " + engine_number + " and I am not the planning master.");
    							}
    							createNewInstanceOfSynchronizationController(my_ip, ((Instance_Running) local_object).getProcessID(), replicated_workflow_id, rep_grade, engine_number, planning_master);
    							new_process = false;
    						}
    					}
    					//the message is an instance of Activity_Ready
    					else if(local_object instanceof Activity_Ready) {
    						if(checkInvokes(((Activity_Ready) local_object).getProcessID())){
    							Start_Activity start_message = new Start_Activity();
    							start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
    						
    							ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
    							producer_local.send(message_producer_local);
    						}
    						else {
    							sendMessageFromProcessToSynchronizationController(local_object, ((Activity_Ready) local_object).getProcessID());
    						}

    					}
    					//the message is an instance of Variable_Modification
    					else if(local_object instanceof Variable_Modification) {
    						if(!checkInvokes(((Variable_Modification) local_object).getProcessID())){
    							sendMessageFromProcessToSynchronizationController(local_object, ((Variable_Modification) local_object).getProcessID());
    						}
    					}
    					//the message is an instance of Variable_Modification_At_Assign
    					else if(local_object instanceof Variable_Modification_At_Assign) {
    						if(!checkInvokes(((Variable_Modification_At_Assign) local_object).getProcessID())){
    							sendMessageFromProcessToSynchronizationController(local_object, ((Variable_Modification_At_Assign) local_object).getProcessID());
    						}
    					}
    					//the message is an instance of Activity_Complete
    					else if(local_object instanceof Activity_Complete) {
    						if(!checkInvokes(((Activity_Complete) local_object).getProcessID())){
    							sendMessageFromProcessToSynchronizationController(local_object, ((Activity_Complete) local_object).getProcessID());
    						}
    					}
    					//the message is an instance of Instance_Suspended
    					//the information for the associated SynchronizationControllerThread can be deleted afterwards
    					else if(local_object instanceof Instance_Suspended) {
    						if(checkInvokes(((Instance_Suspended) local_object).getProcessID())){
    							invokes.remove(((Instance_Suspended) local_object).getProcessID());
    						}
    						else {
    							sendMessageFromProcessToSynchronizationController(local_object, ((Instance_Suspended) local_object).getProcessID());
    							//deleteSynchronizationController(((Instance_Suspended) local_object).getProcessID());
        				        //ObjectMessage message_finished = session_middleware.createObjectMessage(my_ip);           
        				        //producer_middleware.send(message_finished);
    						}
    					}
    					
    					local_object = null;
    					//we send the reply from the SynchronizationControllerThread to the ode
    					sendReplyToWorkflow();
    					
    					sendReplyToMiddleware();
    					
    					if(new Date().getTime() > (heartbeat + 10000)) {
    				        ObjectMessage message_heartbeat = session_middleware.createObjectMessage(my_ip);           
    				        producer_middleware.send(message_heartbeat);
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
    }	
}