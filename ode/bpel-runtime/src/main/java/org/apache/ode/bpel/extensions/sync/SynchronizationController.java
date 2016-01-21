package org.apache.ode.bpel.extensions.sync;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Date;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang.SystemUtils;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.Compensate_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Complete_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ActivityEventMessage;
//import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
//import org.apache.ode.bpel.extensions.processes.Activity_Status;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

//TODO change activity status change at incoming skip-message
/**
 * A class that executes a replicated workflow. Initializes an instance of
 * SynchronizationControllerThread, which processes the replicated execution of a new replicated workflow.
 * 
 * @author krawczls
 *
 */
public class SynchronizationController {
	
	
    /**
     * A method that initializes a new SynchronizationController instance.
     * 
     * @param id
     * @throws Exception
     */
    public void init(String ip, long id, String replicated_workflow_id, int grade, int engine_number, boolean planning_master) throws Exception {
    	thread(new SynchronizationControllerThread(id, replicated_workflow_id, grade, engine_number, planning_master), false);
    	Thread.sleep(1000);
    }
    

    /**
     * A method for initializing and starting a thread.
     * 
     * @param runnable
     * @param daemon
     */
    public static void thread(Runnable runnable, boolean daemon) {
        Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
    }
    
    /**
     * A thread in which the execution of a replicated workflow takes place. Communicates with other 
     * SynchronizationControllerThreads via Active MQ and receives messages from the specific workflow through 
     * the SynchronizationMiddleware.
     * 
     * @author krawczls
     *
     */
    public static class SynchronizationControllerThread implements Runnable {
    	
		Connection connection_local = null;
		Session session_local = null;
		
		MessageProducer producer_process = null;
		MessageConsumer consumer_process = null;
    	
		MessageProducer producer_middleware = null;
		MessageConsumer consumer_middleware = null;
    	
    	int activity_master_number = 0;
    	
    	ArrayList<Activity> activity_list = new ArrayList<Activity>();    	
        //state_list contains all variables and their values
        ArrayList<Write_Variable> state_list = new ArrayList<Write_Variable>();
        ArrayList<Serializable> message_list = new ArrayList<Serializable>();
        
        ArrayList<Integer> engine_number_list = new ArrayList<Integer>();
        ArrayList<SynchronizationMessage> vote_list = new ArrayList<SynchronizationMessage>();
        int counter = 1;
        
    	int majority;
    	int current_view = 0;
        int last_committed_view = 0;
    	
        boolean invoke = false;
        boolean firstActivities = false;
        long processID = 0;
        int rep_grade = 0;
        int engine_number = 0;
        
        boolean viewchange_in_progress = false;
        
    	boolean planning_master = false;
    	boolean activity_master = false;
    	
    	String master_status = "--F";
    	
    	Random random_generator; 
    	
        Serializable remote_object = null;
        SynchronizationMessage get_message = null;
        
        Serializable local_object = null;
        Serializable hold_object = null;
        
        long old_time = (new Date()).getTime();
        long heartbeat = (new Date()).getTime();
    	
        String replicated_workflow_id;
        //ActivityEventHandler activityHandler = null;
        ArrayList<String> logs = new ArrayList<String>();
        
        /**
         * Constructor for registering a process id to a thread
         * 
         * @param id
         */
        public SynchronizationControllerThread(long id, String replicated_workflow_id, int rep_grade, int engine_number, boolean planning_master) {
        	processID = id;
        	this.rep_grade = rep_grade;
        	this.engine_number = engine_number;
        	this.planning_master = planning_master;
        	this.replicated_workflow_id = replicated_workflow_id;
        }

    	/**
    	 * A function that returns the type (invoke, reply, wait etc.) of an activity.
    	 * 
    	 * @param aObject
    	 * @return
    	 */
    	public String getActivityType(Serializable aObject) {
			int from = (((ActivityEventMessage) aObject).getActivityXPath()).lastIndexOf("/");
			int to = -1;
			to = (((ActivityEventMessage) aObject).getActivityXPath()).lastIndexOf("[");
			if(to == -1){
				return ((((ActivityEventMessage) aObject).getActivityXPath()).substring(from + 1, (((ActivityEventMessage) aObject).getActivityXPath()).length()));
			}
			else{
				return ((((ActivityEventMessage) aObject).getActivityXPath()).substring(from + 1, to));
				
			}
    	}

    	/**
    	 * A method to simplify console output.
    	 * 
    	 * @param s
    	 */
    	public void printLine(String s){
    		System.out.println(master_status + " " + s);
    	}
    	
    	/**
    	 *A method that sets up communication with the workflow via the SynchronizationMiddleware.
    	 * 
    	 * @throws Exception
    	 */
    	public void setupConnections() throws Exception {	
    		ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://localhost:61616?jms.prefetchPolicy.queuePrefetch=0");       
	        connection_local = connection_factory.createConnection();
	        connection_local.start();
	        session_local = connection_local.createSession(false, Session.AUTO_ACKNOWLEDGE);
	            
	        //setting up communication with the ode
	        //(via the SynchronizationMiddleware)
	        Destination destination_process = session_local.createQueue("to process " + String.valueOf(processID));
	        Destination source_process = session_local.createQueue("from process " + String.valueOf(processID));
	            
	        //creating message producer
	        producer_process = session_local.createProducer(destination_process);	            	
	            
	        //creating message consumer
	        consumer_process = session_local.createConsumer(source_process);  
	        
	        Destination destination_middleware = session_local.createQueue("to Middleware from " + this.replicated_workflow_id);
	        Destination source_middleware = session_local.createQueue("from Middleware to " + this.replicated_workflow_id);
	        
	        producer_middleware = session_local.createProducer(destination_middleware);
	        
	        consumer_middleware = session_local.createConsumer(source_middleware);
    	}
    	
    	/**
    	 * Method that writes a String ArrayList to a log file. File gets created if it does not exists.
    	 * 
    	 * @param text
    	 */
    	public void writeToLogFile(ArrayList<String> text) {
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
    			FileWriter fWriter = new FileWriter(file.getAbsoluteFile(), true);
    			BufferedWriter bWriter = new BufferedWriter(fWriter);
    			for(int i = 0; i < text.size(); i++) {
    				bWriter.write((String) text.get(i));
    				bWriter.newLine();
    			}
    			bWriter.close();
    		}
    		catch(IOException e) {
    			
    		}
    	}
    	
    	/**
    	 * A method that returns a message received from the SynchronizationMiddleware via the Replication Middleware.
    	 * The parameter specifies the time to wait for the message. 0 means to wait indefinitely.
    	 * 
    	 * @param time
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
    	 * A method that sends a SynchronizationMessage to the Replication Middleware via the SynchronizationMiddleware.
    	 * 
    	 * @param message
    	 * @throws JMSException
    	 */
    	public void sendMessageToMiddleware(SynchronizationMessage message) throws JMSException{
    		ObjectMessage object_message = session_local.createObjectMessage(message);
    		producer_middleware.send(object_message);
    	}
    	
    	/**
    	 * A method that randomly chooses the master for the first activity and sends that information to the other replicas.
    	 * 
    	 * @throws Exception
    	 */
    	public void chooseFirstActivityMaster() throws Exception {
            if(planning_master){
            	printLine("Choosing next activity master.");       	
				activity_master_number = random_generator.nextInt(rep_grade) + 1;
				if(activity_master_number == engine_number){
					activity_master = true;
					master_status = "--M ";
				}
				else{
					activity_master = false;
					master_status = "--F ";
				}
				printLine("Next activity master: " + activity_master_number);
				//empty queues
				Serializable aObject = null;
				aObject = receiveMessageFromMiddleware(10);
				while (aObject != null) {
					aObject = receiveMessageFromMiddleware(10);
				}
				//send information about the first activity master to all replicas
				SynchronizationMessage activity_master_message = new SynchronizationMessage("Master", true, engine_number, activity_master_number, replicated_workflow_id);
				sendMessageToMiddleware(activity_master_message);
				planning_master = false;
            }
    	}
    	
    	/**
    	 * A method that receives and processes the information by the planning master
    	 * regarding the first activity master.
    	 * 
    	 * @throws Exception
    	 */
    	public void waitForActivityMasterMessage() throws Exception {
    		printLine("Waiting for the information about the next activity master.");
            while(activity_master_number == 0) {           	
            	Serializable aObject = null;
            	aObject = receiveMessageFromMiddleware(10);

				if(aObject instanceof SynchronizationMessage) {	
					printLine("Received a SynchronizationMessage.");
					SynchronizationMessage get_master = (SynchronizationMessage) aObject;
					if(get_master.get_message().equals("Master")){
						printLine("Received the information about the next activity master.");
						activity_master_number = get_master.get_next_master();
						if(activity_master_number == engine_number) {
							activity_master = true;
							master_status = "--M ";
						}
						else {
							activity_master = false;
							master_status = "--F ";
						}
					}
				}
				//for debugging purposes
				else if(aObject != null) {
					printLine(aObject.toString());
				}
            }
    	}
    	
    	/**
    	 * A method that sends a heartbeat to the other replicas.
    	 * 
    	 * @throws Exception
    	 */
    	public void sendHeartbeat() throws Exception {
			SynchronizationMessage heartbeat = new SynchronizationMessage("Heartbeat", true, engine_number, replicated_workflow_id);
			sendMessageToMiddleware(heartbeat);
    	}
  	
    	/**
    	 * A function that returns a message it receives from the local workflow.
    	 * 
    	 * @return
    	 * @throws Exception
    	 */
    	public Serializable receiveMessageFromWorkflow(int time) throws Exception {
    		Message message = null;
    		if(time == 0) {
    			message = consumer_process.receive();
    		}
    		else {
    			message = consumer_process.receive(time);
    		}
    		if(message != null) {
    			ObjectMessage message_middleware = (ObjectMessage) message;
    			Serializable message_object = message_middleware.getObject();
    			if(message_object instanceof Activity_Ready) {
    				logs.add(replicated_workflow_id + " * " + master_status + " * " + "next activity ready" + " * " + (new Date()).getTime());
    				//writeToLogFile("next activity ready");
    			}
    			return message_object;
    		}
    		return null;
    	}
    	
    	/**
    	 * @param activity
    	 */
    	public void compensateActivity(long scope, int index) throws Exception{
    		
			Compensate_Scope compensate_message = new Compensate_Scope();
			compensate_message.setScopeID(scope);
			compensate_message.setProcessID(((Activity) activity_list.get(index)).get_process_id());
		
			ObjectMessage message_producer_local = session_local.createObjectMessage(compensate_message);
			producer_process.send(message_producer_local);  
    		
			int j = 0;
			
    		for(int i = 0; i < activity_list.size(); i++) {
    			if(((Activity) activity_list.get(i)).get_scope_id() == scope) {
    				j = i;
    				break;
    			}
    		}
    		
    		local_object = null;
    		
    		for(int i = j; i <= index; i++) {
    			local_object = receiveMessageFromWorkflow(0);
    			if(local_object instanceof Activity_Ready){
    				
    			}
    		}
    		
    	}
    	
    	/**
    	 * A method that processes the status update received from the activity master.
    	 * 
    	 * @throws Exception
    	 */
    	public void processStatusUpdate() throws Exception {
			
			int test_view = -1;
			int new_view = -1;
			int index = -1;
				
			boolean do_nothing = false;
			boolean not_ready = false;
			
			ArrayList<?> message_list = ((SynchronizationMessage) remote_object).get_status_update();
								
			Activity new_activity = new Activity();
				
			//working through the status update
			for(int i = 0; i < message_list.size(); i++) {
				//
				if(message_list.get(i) instanceof Activity) {
					new_activity = (Activity) message_list.get(i);
					printLine("Type of the Activity in the Status Update is:" + new_activity.get_activity_type());
					for(int j = 0; j < activity_list.size(); j++) {
						if(new_activity.get_xpath().equals(((Activity) activity_list.get(j)).get_xpath())) {
							test_view = ((Activity) activity_list.get(j)).get_view();
							index = j;
						}
					}
					if(index == -1) {
						printLine("Workflow not ready for Status Update.");
						not_ready = true;
					}
					else {
						new_view = new_activity.get_view();
							
						//do nothing
						if((new_view < current_view) && (new_view == last_committed_view)) {
							printLine("Update received.Option 1: We do nothing.");
							do_nothing = true;
						}
						//reply with reject
						else if((new_view < current_view) && (new_view < last_committed_view) && (test_view != new_view)) {
							printLine("Update received.Option 2: We reply with reject.");								
							SynchronizationMessage reject_message = new SynchronizationMessage("Reject", false, engine_number, new_activity.get_engine_number(), replicated_workflow_id);
							sendMessageToMiddleware(reject_message);
							do_nothing = true;
						}
						//reply with OK (case 1) other cases are being handled below
						else {
							if((new_view > current_view) || (new_view > last_committed_view)) {
								current_view = new_view;
								last_committed_view = new_view;
								printLine("Update received. Option 3: Everything is okay.");	
									
								Activity activity = (Activity) activity_list.get(index);
								if((activity.get_status()).equals("Uncommitted")) {
								//if((activityHandler.getActivityStatus(activity.get_xpath(), activity.get_process_id())).equals(Activity_Status.ActivityStatus.uncommitted)){
									//compensate the activity
									//TODO compensation logic
									printLine("Update received. Option 3.a: We do need to compensate.");
									compensateActivity(activity.get_scope_id(), index);
								}
							}
							//if any subsequent updates have been applied
							if(index != (activity_list.size() - 1)) {
								do_nothing = true;
							}
							Activity activity = (Activity) activity_list.get(index);
							activity.set_status("UncommittedSkipped");
							//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.uncommittedskipped);
							activity.set_view(new_view);
						}
					}
				}
				//
				else if((message_list.get(i) instanceof Write_Variable) && !do_nothing && !not_ready) {
					printLine("Update received. We have to update a variable.");
					Write_Variable old_write_message = (Write_Variable) message_list.get(i);
					Write_Variable write_message = new Write_Variable();
						
					write_message.setProcessID(((Activity)activity_list.get(activity_list.size() - 1)).get_process_id());
					write_message.setScopeID(((Activity)activity_list.get(activity_list.size() - 1)).get_scope_id());
					write_message.setVariableName(old_write_message.getVariableName());
					write_message.setChanges(old_write_message.getChanges());
															
					//update state list
					boolean found = false;
					for(int j = 0; j < state_list.size(); j++) {
						if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())) {
							((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
							found = true;
						}
					}
					if(!found) {
						state_list.add(write_message);
					}
						
					ObjectMessage message_producer_local = session_local.createObjectMessage(write_message);
					producer_process.send(message_producer_local);		        							
				}
				//
				else if((message_list.get(i) instanceof Skip_Activity) && !do_nothing && !not_ready) {	
					Activity activity = (Activity) activity_list.get(activity_list.size() - 1);
					if((activity.get_activity_type()).equals("invoke")) {
						printLine("Update received. We are sending our complete message.");
						Complete_Activity complete_message = new Complete_Activity();
						complete_message.setReplyToMsgID(activity.get_message_id());
						
						ObjectMessage message_producer_local = session_local.createObjectMessage(complete_message);
						producer_process.send(message_producer_local); 
					}
					else{
						printLine("Update received. We are sending our skip message.");
						Skip_Activity skip_message = new Skip_Activity();
						skip_message.setReplyToMsgID(activity.get_message_id());
					
						ObjectMessage message_producer_local = session_local.createObjectMessage(skip_message);
						producer_process.send(message_producer_local);   
					}
				}
			}      							
			if(!not_ready) {
				//send ok to master
				SynchronizationMessage ok_message = new SynchronizationMessage("OK", false, engine_number, new_activity.get_engine_number(), replicated_workflow_id);
				ok_message.set_activity(new_activity);
				sendMessageToMiddleware(ok_message);
				activity_master_number = new_activity.get_engine_number();
				printLine("OK send.");	
				remote_object = null;
			}
    	}
    	
    	/**
    	 * A method that initiates a viewchange vote.
    	 * 
    	 * @throws Exception
    	 */
    	public void initiateViewchange() throws Exception {
			current_view++;
			SynchronizationMessage viewchange_message = new SynchronizationMessage("Viewchange", true, engine_number, replicated_workflow_id);
			viewchange_message.set_view(current_view);
			engine_number_list = new ArrayList<Integer>();
			vote_list = new ArrayList<SynchronizationMessage>();
			counter = 1;
			viewchange_in_progress = true;
			sendMessageToMiddleware(viewchange_message);
    	}
    	
    	/**
    	 * A method for starting a blocked activity.
    	 * 
    	 * @throws Exception
    	 */
    	public void startActivity() throws Exception {
			Start_Activity start_message = new Start_Activity();
			start_message.setReplyToMsgID(((Activity_Ready) local_object).getMessageID());
		
			ObjectMessage message_producer_local = session_local.createObjectMessage(start_message);
			producer_process.send(message_producer_local);
    	}
    	
    	/**
    	 * A method that processes the Activity_Ready message.
    	 * 
    	 * @throws Exception
    	 */
    	public void processActivityReadyEvent() throws Exception {
			printLine(getActivityType(local_object));
			printLine(((Activity_Ready)local_object).getScopeID().toString());
			if(processID == ((Activity_Ready)local_object).getProcessID()){		        						   
				if((getActivityType(local_object)).equals("process") || (getActivityType(local_object)).equals("sequence") || (getActivityType(local_object)).equals("receive")){
					firstActivities = true;
					
					//adding the new activity to our activity list
					Activity_Ready activity_status = (Activity_Ready) local_object;
					Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", engine_number);
					activity_list.add(activity);
					
					printLine("Executing: " + ((Activity_Ready)local_object).getActivityName());
					//executing the new activity
					logs.add(replicated_workflow_id + " * " + master_status + " * " + "master starting activity" + " * " + (new Date()).getTime());
					//writeToLogFile("master starting activity");
					startActivity();       						
					local_object = null;
				}
				else{
					firstActivities = false;
					printLine(((Activity)activity_list.get(activity_list.size() - 1)).get_process_id().toString());
					printLine(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath());
					//activity master starts new activity
					if((activity_master && (activity_list.size() == 0)) || 
						(activity_master && ((((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped") ||
						//(activity_master && ((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.skipped) ||
						(((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Executed")))){
						//(activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.executed)))) {
						if((getActivityType(local_object)).equals("invoke")){
							invoke = true;
						}
						printLine("Executing: " + ((Activity_Ready)local_object).getActivityName());
						//executing the new activity
						startActivity();  
					
						//adding the new activity to our activity list
						Activity_Ready activity_status = (Activity_Ready) local_object;
						Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", engine_number);
						activity_list.add(activity);
						//adding the new activity to the start of our status update message
						if(rep_grade > 1) {
							message_list = new ArrayList<Serializable>();
							message_list.add(activity);
						}
						local_object = null;
					
					}
					//activity master has yet to get commit messages from the majority of the other replicas
					//send status update again if necessary (send only to the engines who have not responded yet)
					else if(activity_master && (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Uncommitted")){
					//else if(activity_master && (((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.uncommitted)))){
						printLine("*time" + (new Date()).getTime() + "*old" + old_time + "*counter" + counter + "*majority" + majority);
						if(((new Date()).getTime() - old_time >= 10000) && counter < majority){
							boolean skip_update = false;
							for(int i = 1; i <= rep_grade; i++){
								for(int j = 0; j < engine_number_list.size(); j++){
									if(((Integer) engine_number_list.get(j)) == i) {
										skip_update = true;
									}
								}
								if(!skip_update) {
									SynchronizationMessage update_message = new SynchronizationMessage("Status Update", false, engine_number, i, replicated_workflow_id);
									update_message.set_status_update(message_list);
									sendMessageToMiddleware(update_message);
								}
								skip_update = false;
							}
							old_time = new Date().getTime();
							printLine("Status Update send again.");
						}
						else if(majority == 1) {
							((Activity)activity_list.get(activity_list.size() - 1)).set_status("Executed");
						}
					}
					else if((getActivityType(local_object)).equals("invoke") && (((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Ready")){
					//else if((getActivityType(local_object)).equals("invoke") && (((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.ready)))){
						startActivity();  
						local_object = null;
					}
					//activity ready event for other replicas
					else if(!activity_master && (((((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped"))|| (((Activity)activity_list.get(activity_list.size() - 1)).get_status()).equals("Executed"))){
					//else if(!activity_master && ((((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.skipped))) || 
					//(((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.executed))))){
						//adding the new activity to our activity list
						Activity_Ready activity_status = (Activity_Ready) local_object;
						Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), current_view, "Ready", engine_number);
						if(activity_list.size() == 0 || !((activity_status.getActivityXPath()).equals(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath()))){
							activity_list.add(activity);
						}
						//start view change countdown
						old_time = (new Date()).getTime();
						hold_object = local_object;
						local_object = null;
					}
					else {
						printLine(((Activity) activity_list.get(activity_list.size() - 1)).get_status());
					}
				}
			}
			else {
				printLine("Executing activity from different process: " + ((Activity_Ready)local_object).getActivityName());		
				startActivity();			
				local_object = null;
			}
    	}
    	
    	/**
    	 * A method that processes the Variable_Modification message.
    	 */
    	public void processVariableModificationEvent() {
    		if(!firstActivities && (processID == ((Variable_Modification)local_object).getProcessID())) {
				Variable_Modification a = (Variable_Modification) local_object;
				
				Write_Variable write_message = new Write_Variable();
				write_message.setVariableName(a.getVariableName());
				write_message.setChanges(a.getValue());
				
				//update state list
				boolean found = false;
				for(int j = 0; j < state_list.size(); j++){
					if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())){
						((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
						found = true;
					}
				}
				if(!found){
					state_list.add(write_message);
				}
				if(rep_grade > 1) {
					message_list.add(write_message);
				}
			}
    	}
    	
    	/**
    	 * A method that processes the Variable_Modification_At_Assign message.
    	 */
    	public void processVariableModificationAtAssignEvent() {
			if(!firstActivities && (processID == ((Variable_Modification_At_Assign)local_object).getProcessID())){
				Variable_Modification_At_Assign a = (Variable_Modification_At_Assign) local_object;
				
				Write_Variable write_message = new Write_Variable();
				write_message.setVariableName(a.getVariableName());
				write_message.setChanges(a.getValue());
			
				//update state list
				boolean found = false;
				for(int j = 0; j < state_list.size(); j++){
					if((write_message.getVariableName()).equals(((Write_Variable) state_list.get(j)).getVariableName())){
						((Write_Variable) state_list.get(j)).setChanges(write_message.getChanges());
						found = true;
						}
					}
				if(!found){
					state_list.add(write_message);
				}		
				if(rep_grade > 1) {
					message_list.add(write_message);
				}
			}
    	}
    	
    	/**
    	 * A method that processes the ActivityComplete message.
    	 * 
    	 * @throws Exception
    	 */
    	public void processActivityCompleteEvent() throws Exception {
    		Activity activity = ((Activity) activity_list.get(activity_list.size() - 1));
			if(activity_master && activity.get_status().equals("Ready")){  			       					
				if((getActivityType(local_object)).equals("invoke") && (processID == ((Activity_Complete)local_object).getProcessID())){
					invoke = false;
				}
				if(firstActivities || rep_grade <= 1 || !(processID == ((Activity_Complete)local_object).getProcessID())){
					//do nothing
					if(firstActivities || rep_grade <= 1){
						((Activity) activity_list.get(activity_list.size() - 1)).set_status("Executed");
						//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.executed);
						firstActivities = false;
					}
					local_object = null;
				}
				else if((((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Ready")){      						
					((Activity) activity_list.get(activity_list.size() - 1)).set_status("Uncommitted");
					//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.uncommitted);
					
					Skip_Activity skip_message = new Skip_Activity();
					message_list.add(skip_message);
					//send status update
					printLine("Status Update send");
					SynchronizationMessage update_message = new SynchronizationMessage("Status Update", true, engine_number, replicated_workflow_id);
					update_message.set_status_update(message_list);
					sendMessageToMiddleware(update_message);
							
					engine_number_list = new ArrayList<Integer>();
					counter = 1;
					local_object = null;	 
					old_time = (new Date()).getTime();
				}
			}
			else{
					if(firstActivities){
						((Activity) activity_list.get(activity_list.size() - 1)).set_status("Executed");
						//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.executed);
						firstActivities = false;
					}
					local_object = null;
			}
    	}
    	
    	/**
    	 * A method that processes the OK message received from a replica.
    	 * 
    	 * @throws Exception
    	 */
    	public void processOKFromReplica() throws Exception {
    		Boolean added = false;
			int get_engine_number = get_message.get_engine_number();
			for(int i = 0; i < engine_number_list.size(); i++){
				if(get_engine_number == (Integer) engine_number_list.get(i)){
					added = true;
				}
			}
			//add all the ip's of the OK messages
			if(!added){
				engine_number_list.add(get_engine_number);
				counter += 1;
				printLine("OK from engine " + get_engine_number + " received.");
			}
			//send Commit to the other EE's
			if(counter >= majority){
				//calculate next master out of the received ip's 
				int next_activity_master = random_generator.nextInt(engine_number_list.size() + 1);
				if(next_activity_master == engine_number_list.size()){
					activity_master_number = engine_number;
					activity_master = true;
					master_status = "--M ";
				}
				else{
					activity_master_number = (Integer) engine_number_list.get(next_activity_master);
					activity_master = false;
					master_status = "--F ";
				}
				//update activity status to executed
				printLine("All OK's received.");
				((Activity) activity_list.get(activity_list.size() - 1)).set_status("Committed");
				//Activity activity = ((Activity) activity_list.get(activity_list.size() - 1));
				//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.committed);
				
				//inform all replicas about the new activity master
				SynchronizationMessage commit_message = new SynchronizationMessage("Commit", true, engine_number, activity_master_number, replicated_workflow_id);									
				sendMessageToMiddleware(commit_message);
				printLine("Commit send.");
			}
    	}
    	
    	/**
    	 * A method that processes the Commit message a replica receives from the master.
    	 * 
    	 * @throws Exception
    	 */
    	public void processCommitFromMaster() throws Exception {
			((Activity) activity_list.get(activity_list.size() - 1)).set_status("Skipped");
    		//Activity activity = ((Activity) activity_list.get(activity_list.size() - 1));
    		//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.skipped);
			
			int get_engine_number = get_message.get_engine_number();
			SynchronizationMessage ack_message = new SynchronizationMessage("Ack", false, engine_number, get_engine_number, replicated_workflow_id);	
			sendMessageToMiddleware(ack_message);
			printLine("Ack send.");
			
			activity_master_number = get_message.get_next_master();
			// (maybe) starting with execution of next activity
			if(activity_master_number == engine_number){
				activity_master = true;
				master_status = "--M ";
			}

			//message from master so reset timer
			old_time = (new Date()).getTime();
    	}
    	
    	/**
    	 * A method that processes the Ack's the master receives from the replicas.
    	 */
    	public void processAcksFromReplicas() {
			printLine("Ack received.");
			counter--;
			if(counter == 1 ){
				((Activity)activity_list.get(activity_list.size() - 1)).set_status("Executed");
				//Activity activity = ((Activity) activity_list.get(activity_list.size() - 1));
	    		//activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.executed);
			}
    	}
    	
    	/**
    	 * A method for processing a viewchange request from another replica.
    	 * 
    	 * @throws Exception
    	 */
    	public void processViewchange() throws Exception {
    		//reply with vote
			if(current_view < get_message.get_view()){
				current_view = get_message.get_view();
				
				int get_engine_number = get_message.get_engine_number();
				SynchronizationMessage vote_message = new SynchronizationMessage("Vote for Viewchange", false, engine_number, get_engine_number, replicated_workflow_id);
				vote_message.set_state_list(state_list);
				//getting the last executed activity
				
				vote_message.set_activity(null);
				for (int i = activity_list.size() - 1; i > 0; i--){
					if((((Activity) activity_list.get(i)).get_status()).equals("Executed")) {						
					//}
					//if(activityHandler.getActivityStatus((((Activity) activity_list.get(i))).get_xpath(),(((Activity) activity_list.get(i))).get_process_id()).equals(Activity_Status.ActivityStatus.skipped) || 
					   //activityHandler.getActivityStatus((((Activity) activity_list.get(i))).get_xpath(),(((Activity) activity_list.get(i))).get_process_id()).equals(Activity_Status.ActivityStatus.executed)){
						vote_message.set_activity((Activity) activity_list.get(i));
					}
				}
				vote_message.set_last_committed_view(last_committed_view);
					
				sendMessageToMiddleware(vote_message);
				
			}
			//reply with reject
			else{
				int get_engine_number = get_message.get_engine_number();
				SynchronizationMessage reject_message = new SynchronizationMessage("Reject Viewchange", false, engine_number, get_engine_number, replicated_workflow_id);
				sendMessageToMiddleware(reject_message);
			}
			old_time = (new Date()).getTime();
    	}
    	
    	/**
    	 * A method for processing a vote for the viewchange.
    	 * 
    	 * @throws Exception
    	 */
    	public void processVoteForViewchange() throws Exception {
    		vote_list.add(get_message);
			Boolean added = false;
			int get_engine_number = get_message.get_engine_number();
			for(int i = 0; i < engine_number_list.size(); i++){
				if(get_engine_number == (Integer) engine_number_list.get(i)){
					added = true;
				}
			}
			//add all the ip's of the Vote messages
			if(!added){
				engine_number_list.add(get_engine_number);
				counter += 1;
				printLine("Viewchange Vote from engine " + get_engine_number + " received.");
			}
			//Viewchange is successful
			//voting starts in several stages
			if(counter >= majority){
				printLine("Viewchange successful.");
				viewchange_in_progress = false;
				planning_master = true;
				int number = last_committed_view;
				//boolean enter_next_voting_stage = false;
				SynchronizationMessage vote_winner = new SynchronizationMessage();
				Activity test_activity = new Activity();
				//first voting stage
				for(int i = 0; i < vote_list.size(); i++) {
					if(((SynchronizationMessage)vote_list.get(i)).get_last_committed_view() > number){
						//enter_next_voting_stage = false;
						number = ((SynchronizationMessage)vote_list.get(i)).get_last_committed_view();
					}
					else if(((SynchronizationMessage)vote_list.get(i)).get_last_committed_view() == number){
						//enter_next_voting_stage = true;
					}
				}
				Iterator<SynchronizationMessage> itr = vote_list.iterator();
				while(itr.hasNext()) {
					vote_winner = itr.next();			
					if(vote_winner.get_last_committed_view() != number) {
						itr.remove();
					}
				}
				//adding ourselves to the vote list if necessary
				if(last_committed_view == number) {
					vote_winner = new SynchronizationMessage("Vote for Viewchange", false, engine_number, engine_number, replicated_workflow_id);
					vote_winner.set_activity(null);
					for (int i = activity_list.size() - 1; i > 0; i--){
						if((((Activity) activity_list.get(i)).get_status()).equals("Executed")) {
							
						//}
						//if(activityHandler.getActivityStatus((((Activity) activity_list.get(i))).get_xpath(),(((Activity) activity_list.get(i))).get_process_id()).equals(Activity_Status.ActivityStatus.skipped) || 
						   //activityHandler.getActivityStatus((((Activity) activity_list.get(i))).get_xpath(),(((Activity) activity_list.get(i))).get_process_id()).equals(Activity_Status.ActivityStatus.executed)){
							vote_winner.set_activity((Activity) activity_list.get(i));
							i = 0;
						}
					}
					vote_winner.set_last_committed_view(last_committed_view);
				}
				//second voting stage
				number = -1;
				for(int i = 0; i < vote_list.size(); i++) {
					test_activity = ((SynchronizationMessage) vote_list.get(i)).get_activity();
					for(int j = 0; j < activity_list.size(); j++) {
						if((test_activity.get_xpath()).equals(((Activity) activity_list.get(j)).get_xpath())) {
							if(number < j) {
								number = j;
								vote_winner = (SynchronizationMessage) vote_list.get(i);
							}
						}
					}
				}
				vote_winner.set_message("Viewchange Update");
				vote_winner.set_all(true);
				vote_winner.set_engine_number(engine_number);

				remote_object = vote_winner;
			}
			old_time = (new Date()).getTime();
    	}
    	
    	/**
    	 * A method for processing the update the replicas receive after a successful viewchange.
    	 * 
    	 * @throws Exception
    	 */
    	public void processViewchangeUpdate() throws Exception {
			Activity activity = (Activity) activity_list.get(activity_list.size() - 1);

		    //activityHandler.updateActivityStatus(activity.get_process_id(), activity.get_xpath(), Activity_Status.ActivityStatus.skipped);
			last_committed_view = get_message.get_last_committed_view();
			for(int i = 0; i < get_message.get_state_list().size(); i++){
				for(int j = 0; j < state_list.size(); j++){
					if(((Write_Variable) state_list.get(j)).getVariableName().equals(((Write_Variable) get_message.get_state_list().get(i)).getVariableName())){
						if(!((Write_Variable) state_list.get(j)).getChanges().equals(((Write_Variable) get_message.get_state_list().get(i)).getChanges())){
							((Write_Variable) state_list.get(j)).setChanges(((Write_Variable) get_message.get_state_list().get(i)).getChanges());
									
							Write_Variable write_message = new Write_Variable();
								
							write_message.setProcessID(activity.get_process_id());
							write_message.setScopeID(activity.get_scope_id());
							write_message.setVariableName(((Write_Variable) state_list.get(j)).getVariableName());
							write_message.setChanges(((Write_Variable) state_list.get(j)).getChanges());									
						}
					}
				}
			}			        							  
				
			old_time = (new Date()).getTime();

			//send OK
			if(!planning_master) {
				SynchronizationMessage ok_message = new SynchronizationMessage("Viewchange OK", false, engine_number, get_message.get_engine_number(), replicated_workflow_id);
				sendMessageToMiddleware(ok_message);
			}
			else {
				engine_number_list = new ArrayList<Integer>();
			}
			get_message = null;
    	}
    	
    	/**
    	 * @throws Exception
    	 */
    	public void processViewchangeOK() throws Exception {
    		Boolean added = false;
			int get_engine_number = get_message.get_engine_number();
			for(int i = 0; i < engine_number_list.size(); i++){
				if(get_engine_number == (Integer) engine_number_list.get(i)){
					added = true;
				}
			}
			//add all the ip's of the OK messages
			if(!added){
				engine_number_list.add(get_engine_number);
				counter += 1;
				printLine("Viewchange OK from engine " + get_engine_number + " received.");
			}
			
			if(counter >= majority) {
				printLine("All Viewchange OKs received.");
				planning_master = false;
				int next_activity_master = random_generator.nextInt(engine_number_list.size() + 1);
				if(next_activity_master == engine_number_list.size()){
					activity_master_number = engine_number;
					activity_master = true;
					master_status = "--M ";
					logs.add(replicated_workflow_id + " * " + master_status + " * " + "new master selected" + " * " + (new Date()).getTime());
					//writeToLogFile("new master selected");
					//
					activity_list.remove(activity_list.size() - 1);
					local_object = hold_object;
				}
				else{
					activity_master_number = (Integer) engine_number_list.get(next_activity_master);
					activity_master = false;
					master_status = "--F ";
				}
				
				SynchronizationMessage commit_message = new SynchronizationMessage("Viewchange Commit", true, engine_number, activity_master_number, replicated_workflow_id);									
				sendMessageToMiddleware(commit_message);
				
				last_committed_view = last_committed_view + 1;
			}
    	}
    	
    	/**
    	 * @throws Exception
    	 */
    	public void processViewchangeCommit() throws Exception {
			if(get_message.get_next_master() == engine_number){
				activity_master = true;
				activity_master_number = engine_number;
				master_status = "--M ";
				logs.add(replicated_workflow_id + " * " + master_status + " * " + "new master selected" + " * " + (new Date()).getTime());
				//writeToLogFile("new master selected");
				//
				activity_list.remove(activity_list.size() - 1);
				local_object = hold_object;
			}
			else{
				activity_master_number = get_message.get_next_master();
			}
			last_committed_view = last_committed_view + 1;
    	}
    	
    	/**
    	 * A method for closing the connections to the SynchronizationMiddleware and the replicas.
    	 * 
    	 * @throws Exception
    	 */
    	public void closeConnections() throws Exception {
            // Clean up
            session_local.close();
            connection_local.close();
    	}
    	
    	/**
    	 * A method that simulates an error, where all messages from the other engines get dropped.
    	 * Additionally if parameter suspend is true, the engine suspends all execution and enters and endless loop.
    	 * 
    	 * @param suspend
    	 */
    	public void simulateError(boolean suspend) throws Exception {
    		remote_object = new Boolean(suspend);
    		while(suspend) {
    			//do nothing
    			remote_object = receiveMessageFromMiddleware(0);
    		}
    	}
    	
    	/* (non-Javadoc)
    	 * @see java.lang.Runnable#run()
    	 */
    	public void run() {

    		synchronized(this) {
    			try {
    				
    				//setup connection to the ode and the other replicas
    				printLine("Setting up connections to other replicas.");
    				setupConnections();
    				
    	            //compute the majority of the replicas
    	            //majority = (ee_list.size() + 1) / 2;
    	            majority = (rep_grade + 1) / 2;
    	            printLine("Majority is: " + majority);
    	            
    	            //choose the master for the first activity
    	            random_generator = new Random();   
    	            old_time = (new Date()).getTime();
    	            
    	            printLine("Choosing First Activity Master.");
    	            chooseFirstActivityMaster();
    	            printLine("Waiting for information about first Activity Master.");
    	            waitForActivityMasterMessage(); 	                      	                
	                
    	            //used to get the status of any activity
    	            //activityHandler = ActivityEventHandler.getInstance();
    	            
    	            //this loop handles the synchronized execution of the replicated workflow
    	            boolean finished = false;
    	            boolean x = true;	            
    	            while(x) {
    	            	try{
    	            		if(activity_list.size() > 0) {
    	            			printLine("Status of last Activity is:" + ((Activity) activity_list.get(activity_list.size() - 1)).get_status() + ".");
    	            		}
    	            		//dropping all messages from other engines
    	            		if(remote_object instanceof Boolean) {
    	            			printLine("Message is a Boolean.");
    	            			get_message = null;
    	            			Boolean helper = (Boolean) remote_object;
    	            			remote_object = receiveMessageFromMiddleware(10);
    	            			remote_object = helper;
    	            		}
	    	            	//the activity master sends a heartbeat at the start of every cycle
	    	            	if(activity_master){
	    	            		if((new Date()).getTime() - heartbeat >= 10000 && !finished){
	    	            			printLine("Sending a Heartbeat.");
	    	            			sendHeartbeat();
	    	            			heartbeat = (new Date()).getTime();
	    	            		}	
	    	            	}
	    	            	//a new message from the other replicas is received, when there is no message to process left
	    	            	if(remote_object == null){
	    	            		printLine("Remote Message is null. Receiving new Message from Replicas.");
	    	            		remote_object = receiveMessageFromMiddleware(10);
	    	            	}
	    	            	//the message from the replicas is an instance of SynchronizationMessage
							if(remote_object instanceof  SynchronizationMessage){
								if(((SynchronizationMessage) remote_object).get_message().equals("Status Update")) {
									processStatusUpdate();
									old_time = (new Date()).getTime();
								}
								else if(!(((SynchronizationMessage) remote_object).get_message().equals("Status Update")) && get_message == null){
									get_message = (SynchronizationMessage) remote_object;
									printLine("Remote Message is:" + get_message.get_message() + ".");
									old_time = (new Date()).getTime();
									remote_object = null;
								}
							}
							//the message from the replicas is an ArrayList (the status update)
							/*else if(remote_object instanceof ArrayList){
								printLine("Remote Message is Status Update.");
								processStatusUpdate();
								old_time = (new Date()).getTime();
							}*/
							//if there are no new messages from the activity master after a certain time, it's time for a viewchange
							else if(!activity_master && (activity_list.size() == 0 || (((Activity) activity_list.get(activity_list.size() - 1)).get_status().equals("Ready")))) {
							//else if(!activity_master && (activity_list.size() == 0 || (activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.ready))){
								if((new Date()).getTime() - old_time >= 60*1000 + random_generator.nextInt(5000)){
									printLine("We are initiating a Viewchange.");
									logs.add(replicated_workflow_id + " * " + master_status + " * " + "master has failed" + " * " + (new Date()).getTime());
									//writeToLogFile("master has failed");
									initiateViewchange();
									old_time = (new Date()).getTime();
								}
							}
		                	//a new message from the SynchronizationMiddleware is received
							if(local_object == null) {
								local_object = receiveMessageFromWorkflow(10);
							}
							//the message from the SynchronizationMiddleware is an Activity_Ready message
							if(local_object instanceof Activity_Ready) {  
								printLine("Workflow Message is Activity_Ready;");
								processActivityReadyEvent();
							}
							//the message from the SynchronizationMiddleware is a Variable_Modification message
							else if((local_object instanceof Variable_Modification) && activity_master) {
								printLine("Workflow Message is Variable_Modification and we are master.");
								processVariableModificationEvent();
								local_object = null;
							}
							//the message from the SynchronizationMiddleware is a Variable_Modification_At_Assign message
							else if((local_object instanceof Variable_Modification_At_Assign) && activity_master){
								printLine("Workflow Message is Variable_Modification_At_Assign and we are master.");
								processVariableModificationAtAssignEvent();
								local_object = null;
							}
							//the message from the SynchronizationMiddleware is an Activity_Complete message
							else if(local_object instanceof Activity_Complete){
								printLine("Workflow Message is Activity_Complete.");
								processActivityCompleteEvent();
							}
							//the message from the SynchronizationMiddleware is an Instance_Suspended message
							else if(local_object instanceof Instance_Suspended){								
								printLine("Workflow Message is Instance_Suspended.");
								finished = true;
							}
							//other messages from the SynchronizationMiddleware are ignored
							else{
								printLine("Workflow Message is of no interest to us.");
								local_object = null;
							}
							//this part of the program processes the synchronization messages from the other replicas
							if(get_message != null){
								//activity master gets OK message from a replica
								if(get_message.get_message().equals("OK") && activity_master && (((Activity) activity_list.get(activity_list.size() - 1)).get_status().equals("Uncommitted"))) {
								//if(get_message.get_message().equals("OK") && activity_master && (activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.uncommitted)){
									printLine("We received an OK from one of the replicas.");
									processOKFromReplica();
									get_message = null;
								}
								//follower receives commit and sends Ack
								else if(get_message.get_message().equals("Commit") && (get_message.get_engine_number() == activity_master_number) && (((Activity) activity_list.get(activity_list.size() - 1)).get_status().equals("UncommittedSkipped"))) {
								//else if(get_message.get_message().equals("Commit") && get_message.get_ip().equals(activity_master_ip) && !((activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.skipped))){
									printLine("We received the Commit from the master and are ready to commit.");
									processCommitFromMaster();
									get_message = null;
								}
								//activity master gets all the Ack's from the replicas
								else if(get_message.get_message().equals("Ack") && (((Activity) activity_list.get(activity_list.size() - 1)).get_status().equals("Committed"))) {
								//else if(get_message.get_message().equals("Ack") && (activityHandler.getActivityStatus(((Activity)activity_list.get(activity_list.size() - 1)).get_xpath(), ((Activity)activity_list.get(activity_list.size() - 1)).get_process_id())).equals(Activity_Status.ActivityStatus.committed)){ 
									printLine("We received an Ack from one of the replicas and are acknowledging it.");
									processAcksFromReplicas();
									get_message = null;
								}
								else if(get_message.get_message().equals("Reject") && (((Activity) activity_list.get(activity_list.size() - 1)).get_status().equals("Uncommitted"))){
									compensateActivity(((Activity) activity_list.get(activity_list.size() - 1)).get_scope_id(), activity_list.size() - 1);
									engine_number_list = null;
									get_message = null;
								}
								else if(get_message.get_message().equals("Viewchange")){
									processViewchange();
									get_message = null;
								}
								//viewchange initiator gets a positive reply from a replica
								else if(get_message.get_message().equals("Vote for Viewchange") && viewchange_in_progress){
									processVoteForViewchange();
									get_message = null;
								}
								//viewchange initiator gets a negative reply from a replica
								else if(get_message.get_message().equals("Reject Viewchange") && viewchange_in_progress){
									vote_list = null;
									viewchange_in_progress = false;
			       					old_time = (new Date()).getTime();
			       					get_message = null;
								}
								//replica receives the viewchange update
								else if(get_message.get_message().equals("Viewchange Update")){
									processViewchangeUpdate();
								}
								else if(get_message.get_message().equals("Viewchange OK") && planning_master) {
									processViewchangeOK();
									get_message = null;
								}
								else if(get_message.get_message().equals("Viewchange Commit")) {
									processViewchangeCommit();
									get_message = null;
								}
								//replica receives a heartbeat from the activity master
								else if(get_message.get_message().equals("Heartbeat")){
									printLine("We received the Heartbeat of the master.");
			       					old_time = (new Date()).getTime();
			       					get_message = null;
								}
								//catches other cases
								else {
			       					if(get_message.get_message().equals("OK")){
			       						printLine("We received an OK from one of the replicas, but already have a majority.");
										get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Reject")) {
			       						get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Ack")) {
			       						printLine("We received an Ack from one of the replicas, but already have the majority.");
			       						get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Reject Viewchange")) {
			       						get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Vote for Viewchange")) {
			       						get_message = null;
			       					}
			       					else if(get_message.get_message().equals("Viewchange OK")) {
			       						get_message = null;
			       					}
								}
							}
							if(finished) {
								if((((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Executed") ||
										(((Activity) activity_list.get(activity_list.size() - 1)).get_status()).equals("Skipped")) {
									x = false;
								}
							}
    	            	}
    	            	catch(JMSException e){
	        				System.out.println("");
	        				printLine("Unable to handle an incoming/outgoing Message.");
	        				System.out.println("");
	        				e.printStackTrace();
    	            	}
    	            }
    	            //the thread closes all connections
    	            printLine("We are closing all connections.");
    	            SynchronizationMessage finish_message = new SynchronizationMessage();
    	            finish_message.set_message("Finish");
    	            finish_message.setProcessID(processID);
    	            finish_message.set_replicated_workflow_id(replicated_workflow_id);
    	            sendMessageToMiddleware(finish_message);
					for(int i = 0; i < state_list.size(); i++){
						printLine(((Write_Variable) state_list.get(i)).getVariableName());
						printLine(((Write_Variable) state_list.get(i)).getChanges());
					}
					writeToLogFile(logs);
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
