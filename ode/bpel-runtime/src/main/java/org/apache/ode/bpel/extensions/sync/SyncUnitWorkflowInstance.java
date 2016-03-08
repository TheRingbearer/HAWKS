package org.apache.ode.bpel.extensions.sync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.commons.lang.SystemUtils;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Compensate_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Complete_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ActivityEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Skipped;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;

public class SyncUnitWorkflowInstance {
	
	String replicatedWorkflowID;
    long processID = 0;
	
	boolean oneMasterForAllActivities = true;
	
	Session sessionLocal = null;
	Session sessionHAWKSController = null;
	
	MessageConsumer consumerInputQueue = null;
	
	MessageProducer producerProcess = null;
	//MessageConsumer consumerProcess = null;
	
	MessageProducer producerHAWKSController = null;
	//MessageConsumer consumerMiddleware = null;
	
	String myIP;
	String middlewareIP;
	
	int activityMasterNumber = 0;
	
	ArrayList<Activity> activityList = new ArrayList<Activity>();    	
    //state_list contains all variables and their values
    ArrayList<Write_Variable> stateList = new ArrayList<Write_Variable>();
    ArrayList<Serializable> messageList = new ArrayList<Serializable>();
    
    Set<Integer> engineNumberSet = new HashSet<Integer>();
    ArrayList<SynchronizationMessage> voteList = new ArrayList<SynchronizationMessage>();
    
    InstanceEventMessage messageToProcess = null;
    InstanceEventMessage holdMessage = null;
    
	int majority = 0;
	int currentView = 0;
    int lastCommittedView = 0;
	
    boolean invoke = false;
    boolean firstActivities = false;

    int repDegree = 0;
    int engineNumber = 0;
    
    boolean viewchangeInProgress = false;
    
	boolean planningMaster = false;
	boolean activityMaster = false;
	
	String masterStatus = "--F ";
	
	Random randomGenerator = new Random();
	
    Serializable remoteObject = null;
    //SynchronizationMessage getMessage = null;
    
    //Serializable localObject = null;
    //Serializable holdObject = null;
    
    long oldTime = (new Date()).getTime();
    long heartbeat = (new Date()).getTime();
    long heartbeatRate = 0;
    long timeout = 0;
    long okTime = 0;
    
    ArrayList<String> logs = new ArrayList<String>();
    
    boolean finished = false;
	
    boolean instanceNotFinished = true;	        
    long finishTime = 0;
    
    boolean enginesEqualToReplicationDegree = false;
    boolean error = false;
    
    boolean masterMessageProcessed = false;
    
   
    
    /**
     * Constructor for registering a process id to a SynchronizationObject
     * 
     * @param id
     */
    public SyncUnitWorkflowInstance(String myIP, String middlewareIP, long id, String replicatedWorkflowID, int repDegree, int engineNumber, boolean planningMaster, Activity_Ready firstActivityReady, int heartbeatRate, int timeout, boolean enginesEqualToReplicationDegree, boolean error) {
    	this.myIP = myIP;
    	this.middlewareIP = middlewareIP;
    	this.processID = id;
    	this.repDegree = repDegree;
    	this.engineNumber = engineNumber;
    	this.planningMaster = planningMaster;
    	this.replicatedWorkflowID = replicatedWorkflowID;
    	//this.localObject = firstActivityReady;
    	this.heartbeatRate = heartbeatRate;
    	this.timeout = timeout;
    	this.enginesEqualToReplicationDegree = enginesEqualToReplicationDegree;
    	this.error = error;
    	if(this.repDegree == 1) {
    		this.masterStatus = "--M ";
    	}
    }
	
	/**
	 * A method to simplify console output.
	 * 
	 * @param s
	 */
	public void printLine(String s){
		if(activityList.size() == 0) {
			if(Constants.DEBUG_LEVEL > 0) {
				System.out.println(masterStatus + " - " + processID + " " + s);
			}	
		}
		else {
			if(Constants.DEBUG_LEVEL > 0) {
				System.out.println(masterStatus + " " + (activityList.get(activityList.size() - 1)).get_activity_type() + " " + processID + " " + s);
			}
		}
	}
	
	/**
	 * Method that writes a String ArrayList to a log file. File gets created if it does not exists.
	 * 
	 * @param text
	 */
	public synchronized void writeToLogFile(ArrayList<String> text) {
		try {
			File file;
			if(SystemUtils.IS_OS_UNIX) {
				file = new File("/home/ubuntu/logs/log.txt");
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
	 * A function that returns the type (invoke, reply, wait etc.) of an activity.
	 * 
	 * @param aObject
	 * @return
	 */
	public String getActivityType(Serializable aObject) {
		if(aObject instanceof ActivityEventMessage) {
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
		else {
			return "-";
		}
	}
	
	/**
	 * A method that sets up the execution of the replication/ synchronization protocol.
	 * It connects to the middleware, chooses the first activity master etc.
	 * 
	 * @throws Exception
	 */
	public void setUpWorkflowExecution(Activity_Ready firstActivityReady) throws Exception {
		//setup connection to the ode and the other replicas
		printLine("Setting up connections to other replicas.");

		setupConnections();
		if(repDegree > 1) {
        	//compute the majority of the replicas
        	majority = (repDegree/2) + 1 ;

        	printLine("Majority is: " + majority);
        	//choose the master for the first activity  
        	//oldTime = (new Date()).getTime();

        	printLine("Choosing First Activity Master.");
        	messageToProcess = firstActivityReady;
        	chooseFirstActivityMaster();
        	//printLine("Waiting for information about first Activity Master.");
        	//waitForActivityMasterMessage(aObject);
		}
		else {
			processActivityReadyEvent(firstActivityReady);
			majority = 1;
			masterStatus = "--M ";
		}
	}
		
	/**
	 * A method that sets up the connection to the outgoing queues in the HAWKS Controller.
	 * 
	 * @throws Exception
	 */
	public void setupConnections() throws Exception {	
        sessionLocal = SynchronizationUnit.connectionLocal.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        Destination destination_process = sessionLocal.createQueue("org.apache.ode.in");
        producerProcess = sessionLocal.createProducer(destination_process);	            	 
        
		String producerIP = SynchronizationUnit.hAWKSControllerMappingReplicatedWorkflowIDToIP.get(replicatedWorkflowID);
		sessionHAWKSController = SynchronizationUnit.hAWKSControllerIPMappingToSession.get(producerIP);
		producerHAWKSController = sessionHAWKSController.createProducer(sessionHAWKSController.createQueue(replicatedWorkflowID));

        //sessionHAWKSController = SynchronizationUnit.connectionMiddleware.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        //Destination destinationMiddleware = sessionHAWKSController.createQueue(replicatedWorkflowID);
        //producerHAWKSController = sessionHAWKSController.createProducer(destinationMiddleware);	            	
	}



	/**
	 * @param syncMessage
	 * @throws JMSException
	 */

	public void sendMessageToMiddleware(SynchronizationMessage syncMessage) throws JMSException {	
		printLine(syncMessage.toString());
		ObjectMessage objectMessage = sessionHAWKSController.createObjectMessage(syncMessage);
		producerHAWKSController.send(objectMessage);
	}

	/**
	 * A method that sends a heartbeat to the other replicas.
	 * 
	 * @throws Exception
	 */
	public void sendHeartbeat() throws Exception {

		printLine("Sending a Heartbeat.");
		SynchronizationMessage heartbeat = new SynchronizationMessage("Heartbeat", true, engineNumber, replicatedWorkflowID);
		sendMessageToMiddleware(heartbeat);
	}
		
	/**
	 * @param activity
	 */
	public void compensateActivity(long scope, int index) throws Exception{
		printLine("Compensating1");
		Compensate_Scope compensate_message = new Compensate_Scope();
		compensate_message.setScopeID(scope);
		compensate_message.setProcessID(((Activity) activityList.get(index)).get_process_id());
		printLine("Compensating2");
		ObjectMessage message_producer_local = sessionLocal.createObjectMessage(compensate_message);
		producerProcess.send(message_producer_local);  
		printLine("Compensating3");
		int j = 0;
		
		for(int i = activityList.size(); i > 0; i--) {
			printLine("Compensating4");
			if(((Activity) activityList.get(i)).get_scope_id() == scope) {
				printLine("Compensating5");
				j = i;
				//break;
			}
		}
		
		Serializable localObject = null;
		printLine("Compensating6");
		for(int i = j; i <= index; i++) {
			printLine("Compensating7");
			localObject = (0);
			if(localObject instanceof Activity_Ready){
				printLine("Compensating8");
				if((((Activity)activityList.get(i)).get_status()).equals("Executed")) {
					printLine("Compensating9");
					startActivity((Activity_Ready) localObject);
					printLine("Compensating9.5");
				}
				else {
					printLine("Compensating10");
					Skip_Activity skip_message = new Skip_Activity();
					skip_message.setReplyToMsgID(activityList.get(i).get_message_id());
				
					message_producer_local = sessionLocal.createObjectMessage(skip_message);
					producerProcess.send(message_producer_local); 
					printLine("Compensating10.5");
				}
			}
		}
		
	}

	/**
	 * A function that returns a message it receives from the local workflow.
	 * 
	 * @return
	 * @throws Exception
	 */
	/*public Serializable receiveMessageFromWorkflow(int time) throws Exception {
		boolean checkForMyMessage = true;
		while(checkForMyMessage) {
    		Message message = null;
    		if(time == 0) {
    			message = consumerProcess.receive();
    		}
    		else {
    			message = consumerProcess.receive(time);
    		}
    		if(message != null) {
    			ObjectMessage message_middleware = (ObjectMessage) message;
    			Serializable message_object = message_middleware.getObject();
    			if((message_object instanceof InstanceEventMessage) && ((InstanceEventMessage) message_object).getProcessID().equals(processID)) {
    				if(message_object instanceof Activity_Ready) {
    					logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_ready" + "|" + getActivityType(message_object) + "|" + (new Date()).getTime());
    				}
    				return message_object;
    			}
    		}
    		else{
    			return null;
    		}
		}
		return null;
	}*/
	
	/**
	 * A method that randomly chooses the master for the first activity and sends that information to the other replicas.
	 * 
	 * @throws Exception
	 */
	public void chooseFirstActivityMaster() throws Exception {
        if(planningMaster) {
        	if(oneMasterForAllActivities) {
        		activityMasterNumber = engineNumber;
    			activityMaster = true;
    			masterStatus = "--M ";
        	}
        	else {     	
        		activityMasterNumber = randomGenerator.nextInt(repDegree) + 1;
        		if(activityMasterNumber == engineNumber){
        			activityMaster = true;
        			masterStatus = "--M ";
        		}
        		else{
        			activityMaster = false;
        			masterStatus = "--F ";
        		}
        	}

        	printLine("Next activity master: " + activityMasterNumber);
        	//empty queues
        	/*Serializable aObject = null;
        	aObject = receiveMessageFromInputQueue();
        	while (aObject != null) {
        		aObject = receiveMessageFromMiddleware(10);
        	}*/
        	//send information about the first activity master to all replicas
        	SynchronizationMessage activity_master_message = new SynchronizationMessage("Master", true, engineNumber, activityMasterNumber, replicatedWorkflowID);
        	sendMessageToMiddleware(activity_master_message);
			planningMaster = false;
			processActivityReadyEvent(messageToProcess);
        }
	}

	/**
	 * A method that receives and processes the information by the planning master
	 * regarding the first activity master.
	 * 
	 * @throws Exception
	 */
	/*public void waitForActivityMasterMessage(Serializable aObject) throws Exception {
        while(activityMasterNumber == 0) {           	
			if(aObject instanceof SynchronizationMessage) {	
				SynchronizationMessage get_master = (SynchronizationMessage) aObject;
				printLine("Waiting for Master received " + get_master.get_message());
				if(get_master.get_message().equals("Master")){
					printLine("Received the information about the next activity master.");
					activityMasterNumber = get_master.get_next_master();
					if(activityMasterNumber == engineNumber) {
						activityMaster = true;
						masterStatus = "--M ";
					}
					else {
						activityMaster = false;
						masterStatus = "--F ";
					}
				}
			}
			else if(aObject instanceof InstanceEventMessage) {
				localObject = aObject;
			}
        }
	}*/
	
	/**
	 * A method for starting a blocked activity.
	 * 
	 * @throws Exception
	 */
	public void startActivity(InstanceEventMessage workflowMessage) throws Exception {
		Start_Activity start_message = new Start_Activity();
		start_message.setReplyToMsgID(((Activity_Ready) workflowMessage).getMessageID());

		printLine("Starting new activity as reply to " + start_message.getReplyToMsgID());
		ObjectMessage message_producer_local = sessionLocal.createObjectMessage(start_message);
		producerProcess.send(message_producer_local);
	}
	
	/**
	 * A method that decides what to do with the next message from the workflow.
	 * 
	 * @throws Exception
	 */
	public void checkObjectFromWorkflow(InstanceEventMessage workflowMessage) throws Exception {
		//the message from the SynchronizationMiddleware is an Activity_Ready message
		if(workflowMessage instanceof Activity_Ready) {  
			printLine("Workflow Message is Activity_Ready;");
			processActivityReadyEvent(workflowMessage);
		}
		//the message from the SynchronizationMiddleware is a Variable_Modification message
		else if((workflowMessage instanceof Variable_Modification) && activityMaster) {
			printLine("Workflow Message is Variable_Modification.");
			processVariableModificationEvent(workflowMessage);
		}
		//the message from the SynchronizationMiddleware is a Variable_Modification_At_Assign message
		else if((workflowMessage instanceof Variable_Modification_At_Assign) && activityMaster){
			printLine("Workflow Message is Variable_Modification_At_Assign and we are master.");
			processVariableModificationAtAssignEvent(workflowMessage);
		}
		//the message from the SynchronizationMiddleware is an Activity_Complete message
		else if(workflowMessage instanceof Activity_Complete) {
			printLine("Workflow Message is Activity_Complete.");
			processActivityCompleteEvent(workflowMessage);
		}
		else if(workflowMessage instanceof Activity_Skipped) {
			printLine("Workflow Message is Activity_Skipped.");
			processActivitySkippedEvent(workflowMessage);
		}
		//the message from the SynchronizationMiddleware is an Instance_Suspended message
		else if(workflowMessage instanceof Instance_Suspended){	
			printLine("Starting to finish process execution with the state of the last activity " + activityList.get(activityList.size() - 1).get_activity_type() + " being " + activityList.get(activityList.size() - 1).get_status());
			finished = true;
		}
	}
	
	/**
	 * A method that processes the Activity_Ready message.
	 * 
	 * @throws Exception
	 */
	//TODO changed for execution with thread pool, check if it works
	public void processActivityReadyEvent(InstanceEventMessage workflowMessage) throws Exception {
		if(processID == ((Activity_Ready)workflowMessage).getProcessID()){		        						   
			if((getActivityType(workflowMessage)).equals("process") || (getActivityType(workflowMessage)).equals("sequence") || (getActivityType(workflowMessage)).equals("receive")){
				firstActivities = true;
				
				//adding the new activity to our activity list
				Activity_Ready activity_status = (Activity_Ready) workflowMessage;
				Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), currentView, "Ready", engineNumber);
				activityList.add(activity);

				printLine("Executing: " + ((Activity_Ready)workflowMessage).getActivityName());
				//executing the new activity
				printLine("startActivity - " + ((Activity_Ready) workflowMessage).getMessageID());
				logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_ready" + "|" + getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
				startActivity(workflowMessage);       						
			}
			else{
				firstActivities = false;
				//activity master starts new activity
				if((activityMaster && (activityList.size() == 0)) || 
					(activityMaster && ((((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Skipped")
							|| (((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Executed")
							|| (((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Committed")))
					|| (activityMaster && ((((Activity)activityList.get(activityList.size() - 1)).get_activity_type().equals("process"))) || (((Activity)activityList.get(activityList.size() - 1)).get_activity_type().equals("sequence")))){
					if((getActivityType(workflowMessage)).equals("invoke")){
						invoke = true;
					}
					printLine("Executing: " + ((Activity_Ready)workflowMessage).getActivityName());
					//executing the new activity
					printLine("startActivity2 - " + ((Activity_Ready) workflowMessage).getMessageID());
					logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_ready" + "|" + getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
					startActivity(workflowMessage);  
				
					//adding the new activity to our activity list
					Activity_Ready activity_status = (Activity_Ready) workflowMessage;
					Activity activity = new Activity(activity_status.getActivityName(), activity_status.getActivityXPath(), activity_status.getScopeID(), activity_status.getProcessID(), activity_status.getMessageID(), currentView, "Ready", engineNumber);
					activityList.add(activity);
					//adding the new activity to the start of our status update message
					if(repDegree > 1) {
						messageList = new ArrayList<Serializable>();
						messageList.add(activity);
					}
				}
				else if(!masterMessageProcessed) {
					messageToProcess = workflowMessage;
				}
				//activity master has yet to get commit messages from the majority of the other replicas
				//send status update again if necessary (send only to the engines who have not responded yet)
				else if(activityMaster && (((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Uncommitted")){
						messageToProcess = workflowMessage;
					if(((new Date()).getTime() - okTime >= 10000) 
							&& engineNumberSet.size() + 1 < majority){
						for(int i = 1; i <= repDegree; i++){
							if(i != engineNumber 
									&& !engineNumberSet.contains(i)) {
								SynchronizationMessage update_message = new SynchronizationMessage("Status Update", false, engineNumber, i, replicatedWorkflowID);
								update_message.set_status_update(messageList);
								sendMessageToMiddleware(update_message);
							}
							//TODO @schaefdd What if the update does not reach all engines before continuing the execution
						}
						okTime = new Date().getTime();
						printLine("Status Update send again.");
					}
					//TODO check if this is necessary
					else if(majority == 1) {
						((Activity)activityList.get(activityList.size() - 1)).set_status("Executed");
					}
				}
				else if((getActivityType(workflowMessage)).equals("invoke") && (((Activity) activityList.get(activityList.size() - 1)).get_status()).equals("Ready")){
					logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_ready" + "|" + getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
					startActivity(workflowMessage);  
				}
				//activity ready event for other replicas
				else if(!activityMaster) {
					messageToProcess = workflowMessage;
					if(((((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Skipped")) 
						|| ((((Activity)activityList.get(activityList.size() - 1)).get_status()).equals("Executed"))){
						//adding the new activity to our activity list
						Activity_Ready activity_status = (Activity_Ready) workflowMessage;
						Activity activity = new Activity(activity_status.getActivityName(), 
								activity_status.getActivityXPath(), activity_status.getScopeID(), 
								activity_status.getProcessID(), activity_status.getMessageID(), 
								currentView, "Ready", engineNumber);
						if(activityList.size() == 0 || !((activity_status.getActivityXPath()).equals(((Activity)activityList.get(activityList.size() - 1)).get_xpath()))){
							activityList.add(activity);
							printLine("Activity Ready.");
							printLine(activity.get_activity_type());
							printLine(activity.get_message_id().toString());
						}
						//start view change countdown
						holdMessage = workflowMessage;
						oldTime = (new Date()).getTime();
					}
				}
				else {
					//printLine(((Activity) activity_list.get(activity_list.size() - 1)).get_status());
				}
			}
		}
		else {
			printLine("Executing activity from different process: " + ((Activity_Ready) workflowMessage).getActivityName());
			startActivity(workflowMessage);
		}
	}
	
	/**
	 * A method that processes the Variable_Modification message.
	 */
	//TODO changed for execution with thread pool, check if it works
	public void processVariableModificationEvent(InstanceEventMessage workflowMessage) {
		if(!firstActivities && (processID == ((Variable_Modification)workflowMessage).getProcessID())) {
			Variable_Modification a = (Variable_Modification) workflowMessage;
			
			Write_Variable write_message = new Write_Variable();
			write_message.setVariableName(a.getVariableName());
			write_message.setChanges(a.getValue());
			
			//update state list
			boolean found = false;
			printLine("Searching our state_list, because of VariableModification.");
			for(int j = 0; j < stateList.size(); j++){
				if((write_message.getVariableName()).equals(((Write_Variable) stateList.get(j)).getVariableName())){
					((Write_Variable) stateList.get(j)).setChanges(write_message.getChanges());
					found = true;
				}
			}
			if(!found){
				printLine("Adding stuff to our state_list, because of VariableModification.");
				stateList.add(write_message);
			}
			if(repDegree > 1) {
				messageList.add(write_message);
			}
		}
	}
	
	/**
	 * A method that processes the Variable_Modification_At_Assign message.
	 */
	//TODO changed for execution with thread pool, check if it works
	public void processVariableModificationAtAssignEvent(InstanceEventMessage workflowMessage) {
		if(!firstActivities && (processID == ((Variable_Modification_At_Assign)workflowMessage).getProcessID())){
			Variable_Modification_At_Assign a = (Variable_Modification_At_Assign) workflowMessage;
			
			Write_Variable write_message = new Write_Variable();
			write_message.setVariableName(a.getVariableName());
			write_message.setChanges(a.getValue());
		
			//update state list
			boolean found = false;
			for(int j = 0; j < stateList.size(); j++){
				if((write_message.getVariableName()).equals(((Write_Variable) stateList.get(j)).getVariableName())){
					((Write_Variable) stateList.get(j)).setChanges(write_message.getChanges());
					found = true;
					}
				}
			if(!found){
				stateList.add(write_message);
			}		
			if(repDegree > 1) {
				messageList.add(write_message);
			}
		}
	}
	
	/**
	 * A method that processes the ActivityComplete message.
	 * 
	 * @throws Exception
	 */
	//TODO changed for execution with thread pool, check if it works
	public void processActivityCompleteEvent(InstanceEventMessage workflowMessage) throws Exception {
		logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_complete" + "|" + getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
		Activity activity = ((Activity) activityList.get(activityList.size() - 1));
		if(activityMaster && activity.get_status().equals("Ready")){  			       					
			if((getActivityType(workflowMessage)).equals("invoke") && (processID == ((Activity_Complete)workflowMessage).getProcessID())){
				invoke = false;
			}
			if(firstActivities || repDegree <= 1 || !(processID == ((Activity_Complete)workflowMessage).getProcessID())){
				//do nothing
				if(firstActivities || repDegree <= 1){
					((Activity) activityList.get(activityList.size() - 1)).set_status("Executed");
					firstActivities = false;
				}
			}
			else if((((Activity) activityList.get(activityList.size() - 1)).get_status()).equals("Ready")){      						
				((Activity) activityList.get(activityList.size() - 1)).set_status("Uncommitted");
				
				Skip_Activity skip_message = new Skip_Activity();
				messageList.add(skip_message);
				//send status update

				printLine("Status Update send with from the engine " + engineNumber + " for the replicated Workflow " + replicatedWorkflowID);
				SynchronizationMessage update_message = new SynchronizationMessage("Status Update", true, engineNumber, replicatedWorkflowID);
				update_message.set_status_update(messageList);
				sendMessageToMiddleware(update_message);
						
				if (engineNumberSet != null) {
					engineNumberSet.clear();
				}
				engineNumberSet = new HashSet<Integer>();
				okTime = (new Date()).getTime();
			}
		}
		else{
			if(firstActivities){
				((Activity) activityList.get(activityList.size() - 1)).set_status("Executed");
				firstActivities = false;
			}
		}
	}
	
	/**
	 * 
	 */
	//TODO changed for execution with thread pool, check if it works
	public void processActivitySkippedEvent(InstanceEventMessage workflowMessage) {			
	}

	/**
	 * A method that decides what to do with the next message from the other engines.
	 * 
	 * @throws Exception
	 */
	
	public void checkObjectFromOtherEngines(SynchronizationMessage syncMessage) throws Exception {
		printLine("Processing a syncMessage of replicatedWorkflowID " + syncMessage.get_replicated_workflow_id());
		if(syncMessage.get_message().equals("Master") && !masterMessageProcessed) {
			printLine("Received the information about the next activity master.");
			activityMasterNumber = syncMessage.get_next_master();
			if(activityMasterNumber == engineNumber) {
				activityMaster = true;
				masterStatus = "--M ";
			}
			else {
				activityMaster = false;
				masterStatus = "--F ";
			}
			masterMessageProcessed = true;
			printLine("Processed master Message.");
		}
		//middleware send us the finish message
		else if(syncMessage.get_message().equals("Finish")) {
			printLine("We received the finish message.");
			instanceNotFinished = false;
			messageToProcess = null;
			finishTime = (new Date()).getTime();
		}
		//we got a status update from the master
		else if (syncMessage.get_message().equals("Status Update") && masterMessageProcessed) {
			printLine("We received a status update.");
			processStatusUpdate(syncMessage);
		}
		//activity master gets OK message from a replica
		else if(syncMessage.get_message().equals("OK") && activityMaster && (((Activity) activityList.get(activityList.size() - 1)).get_status().equals("Uncommitted"))) {
			printLine("We received an OK from one of the replicas.");
			processOKFromReplica(syncMessage);
		}
		//follower receives commit and sends Ack
		else if(syncMessage.get_message().equals("Commit") && masterMessageProcessed && (syncMessage.get_engine_number() == activityMasterNumber) && (((Activity) activityList.get(activityList.size() - 1)).get_status().equals("UncommittedSkipped"))) {

			printLine("We received the Commit from the master and are ready to commit.");
			processCommitFromMaster(syncMessage);
		}
		//activity master gets all the Ack's from the replicas
		else if(syncMessage.get_message().equals("Ack") && (((Activity) activityList.get(activityList.size() - 1)).get_status().equals("Committed"))) {
			printLine("We received an Ack from one of the replicas and are acknowledging it.");
			processAcksFromReplicas();
		}
		else if(syncMessage.get_message().equals("Reject") && (((Activity) activityList.get(activityList.size() - 1)).get_status().equals("Uncommitted"))){
			compensateActivity(((Activity) activityList.get(activityList.size() - 1)).get_scope_id(), activityList.size() - 1);
			if (engineNumberSet != null) {
				engineNumberSet.clear();
			}
			engineNumberSet = null;
		}
		else if(syncMessage.get_message().equals("Viewchange")){
			processViewchange(syncMessage);
		}
		//viewchange initiator gets a positive reply from a replica
		else if(syncMessage.get_message().equals("Vote for Viewchange") && viewchangeInProgress){
			processVoteForViewchange(syncMessage);
		}
		//viewchange initiator gets a negative reply from a replica
		else if(syncMessage.get_message().equals("Reject Viewchange") && viewchangeInProgress){
			voteList = null;
			viewchangeInProgress = false;
		}
		//replica receives the viewchange update
		else if(syncMessage.get_message().equals("Viewchange Update")){
			processViewchangeUpdate(syncMessage);
		}
		else if(syncMessage.get_message().equals("Viewchange OK") && planningMaster) {
			processViewchangeOK(syncMessage);
		}
		else if(syncMessage.get_message().equals("Viewchange Commit")) {
			processViewchangeCommit(syncMessage);
		}
		//replica receives a heartbeat from the activity master
		else if(syncMessage.get_message().equals("Heartbeat")){
			printLine("We received the Heartbeat of the master.");
			oldTime = (new Date()).getTime();
		}
		else {
			//TODO send message back to input queue -> SynchronizationUnit 
			Destination destinationBackToTheStart = sessionHAWKSController.createQueue("de.unistuttgart.rep." + myIP);
			MessageProducer producerBackToTheStart = sessionHAWKSController.createProducer(destinationBackToTheStart);
			ObjectMessage messageBackToTheStart = sessionHAWKSController.createObjectMessage(syncMessage); 
			producerBackToTheStart.send(messageBackToTheStart);
		}
	}
	 
	/**
	 * A method that processes the OK message received from a replica.
	 * 
	 * @throws Exception
	 */
	public void processOKFromReplica(SynchronizationMessage middlewareMessage) throws Exception {
		if(middlewareMessage.get_activity().get_xpath().equals(activityList.get(activityList.size() - 1).get_xpath())) {
			if (engineNumberSet.add(middlewareMessage.get_engine_number())) {
				printLine("OK from engine " + middlewareMessage.get_engine_number() + " received.");
			}

			//send Commit to the other EE's
			if(engineNumberSet.size() + 1 >= majority){
				if(!oneMasterForAllActivities) {
					//calculate next master out of the received ip's 
					int next_activity_master = randomGenerator.nextInt(engineNumberSet.size() + 1);
					if(next_activity_master == engineNumberSet.size()) {
						activityMasterNumber = engineNumber;
						activityMaster = true;
						masterStatus = "--M ";
					} else {
						activityMasterNumber = next_activity_master; //@schaefdd: Check wheather assignment is correct or could assign an invalid engine ID
						activityMaster = false;
						masterStatus = "--F ";
					}
				}
				//update activity status to executed
				printLine("All OK's received.");
				((Activity) activityList.get(activityList.size() - 1)).set_status("Committed"); 
			
				//inform all replicas about the new activity master
				SynchronizationMessage commit_message = new SynchronizationMessage("Commit", true, engineNumber, activityMasterNumber, replicatedWorkflowID);									
				sendMessageToMiddleware(commit_message);
				printLine("Commit send.");
			}
		}
		else {
			printLine("Received an OK message for an old activity.");
		}
	}

	/**
	 * A method that processes the Commit message a replica receives from the master.
	 * 
	 * @throws Exception
	 */
	public void processCommitFromMaster(SynchronizationMessage middlewareMessage) throws Exception {
		((Activity) activityList.get(activityList.size() - 1)).set_status("Skipped");
		
		//if(messagesToProcess.size() > 0) {
			//messagesToProcess = new ArrayList<InstanceEventMessage>();
		//}
		
		int get_engine_number = middlewareMessage.get_engine_number();
		SynchronizationMessage ack_message = new SynchronizationMessage("Ack", false, engineNumber, get_engine_number, replicatedWorkflowID);	
		sendMessageToMiddleware(ack_message);
		printLine("Ack send.");
		activityMasterNumber = middlewareMessage.get_next_master();
		// (maybe) starting with execution of next activity
		if(activityMasterNumber == engineNumber){
			activityMaster = true;
			masterStatus = "--M ";
		}
	}

	/**
	 * A method that processes the Ack's the master receives from the replicas.
	 */
	public void processAcksFromReplicas() {
		printLine("Ack received.");
		//TODO @schaefdd Ensure the the commits are sent again if the corresponding replica did not reply with an ACK
//		if (allEnginesAcknowledged) {
//			((Activity) activityList.get(activityList.size() - 1)).set_status("Executed"); 
//		}
	}
	
	/**
	 * A method that processes the status update received from the activity master.
	 * 
	 * @throws Exception
	 */
	public void processStatusUpdate(SynchronizationMessage updateMessage) throws Exception {	
		printLine("Received an update from Engine " + updateMessage.get_engine_number() + 
				  " to replicated workflow " + updateMessage.get_replicated_workflow_id());
		int test_view = -1;
		int new_view = -1;
		int index = -1;
			
		boolean do_nothing = false;
		boolean not_ready = false;
		boolean update_received = false;
		
		ArrayList<?> message_list = updateMessage.get_status_update();
							
		Activity new_activity = new Activity();
		
		//check if update has been already received
		if(activityList.size() > 0 && ((Activity) activityList.get(activityList.size() - 1)).get_status().equals("UncommittedSkipped")
				&& ((Activity) message_list.get(0)).get_xpath().equals(((Activity) activityList.get(activityList.size() - 1)).get_xpath())) {
			update_received = true;
		}
		if(!update_received) {	
			//working through the status update
			for(int i = 0; i < message_list.size(); i++) {
				//
				if(message_list.get(i) instanceof Activity) {
					new_activity = (Activity) message_list.get(i);
					for(int j = 0; j < activityList.size(); j++) {
						if(new_activity.get_xpath().equals(((Activity) activityList.get(j)).get_xpath())) {
							test_view = ((Activity) activityList.get(j)).get_view();
							index = j;
						}
					}
					if(index == -1) {
						not_ready = true;
					}
					else {
						new_view = new_activity.get_view();
							
						//do nothing
						if((new_view < currentView) && (new_view == lastCommittedView)) {
							printLine("Update received.Option 1: We do nothing.");
							do_nothing = true;
						}
						//reply with reject
						else if((new_view < currentView) && (new_view < lastCommittedView) && (test_view != new_view)) {
							printLine("Update received.Option 2: We reply with reject.");								
							SynchronizationMessage reject_message = new SynchronizationMessage("Reject", false, engineNumber, new_activity.get_engine_number(), replicatedWorkflowID);
							sendMessageToMiddleware(reject_message);
							do_nothing = true;
						}
						//reply with OK (case 1) other cases are being handled below
						else {
							if((new_view > currentView) || (new_view > lastCommittedView)) {
								currentView = new_view;
								lastCommittedView = new_view;
								printLine("Update received. Option 3: Everything is okay.");		
								Activity activity = (Activity) activityList.get(index);
								if((activity.get_status()).equals("Uncommitted")) {
									//compensate the activity
									printLine("Update received. Option 3.a: We do need to compensate.");
									compensateActivity(activity.get_scope_id(), index);
								}
							}
							//if any subsequent updates have been applied
							if(index != (activityList.size() - 1)) {
								do_nothing = true;
							}
							Activity activity = (Activity) activityList.get(index);
							activity.set_status("UncommittedSkipped");
							activity.set_view(new_view);
						}
					}
				}
				//
				else if((message_list.get(i) instanceof Write_Variable) && !do_nothing && !not_ready) {
					printLine("Update received. We have to update a variable.");
					Write_Variable old_write_message = (Write_Variable) message_list.get(i);
					Write_Variable write_message = new Write_Variable();
						
					write_message.setProcessID(((Activity)activityList.get(activityList.size() - 1)).get_process_id());
					write_message.setScopeID(((Activity)activityList.get(activityList.size() - 1)).get_scope_id());
					write_message.setVariableName(old_write_message.getVariableName());
					write_message.setChanges(old_write_message.getChanges());
															
					//update state list
					boolean found = false;
					printLine("Searching our state_list, because of Update.");
					for(int j = 0; j < stateList.size(); j++) {
						if((write_message.getVariableName()).equals(((Write_Variable) stateList.get(j)).getVariableName())) {
							((Write_Variable) stateList.get(j)).setChanges(write_message.getChanges());
							found = true;
						}
					}
					if(!found) {
						printLine("Adding stuff to our state_list, because of Update.");
						stateList.add(write_message);
					}
						
					ObjectMessage message_producer_local = sessionLocal.createObjectMessage(write_message);
					producerProcess.send(message_producer_local);		        							
				}
				//
				else if((message_list.get(i) instanceof Skip_Activity) && !do_nothing && !not_ready) {	
					Activity activity = (Activity) activityList.get(activityList.size() - 1);
					//if((activity.get_activity_type()).equals("invoke")) {
					printLine("Update received. We are sending our complete message with id " + activity.get_message_id());
					Complete_Activity complete_message = new Complete_Activity();
					complete_message.setReplyToMsgID(activity.get_message_id());
				
					ObjectMessage message_producer_local = sessionLocal.createObjectMessage(complete_message);
					producerProcess.send(message_producer_local);
					logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "activity_complete" + "|" + getActivityType(updateMessage) + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
					/*}
					else{
						printLine("Update received. We are sending our skip message.");
						printLine(activity.get_activity_type());
						printLine(activity.get_message_id().toString());
						printLine(activity.get_xpath());
						printLine(activity.get_process_id().toString());
						Skip_Activity skip_message = new Skip_Activity();						
						skip_message.setReplyToMsgID(activity.get_message_id());
					
						ObjectMessage message_producer_local = session_local.createObjectMessage(skip_message);
						producer_process.send(message_producer_local);   
					}*/
					printLine("Skipped activity " + activity.get_message_id() + " succesfully.");
				}
			}      							
			if(!not_ready) {
				//send ok to master
				SynchronizationMessage ok_message = new SynchronizationMessage("OK", false, engineNumber, new_activity.get_engine_number(), replicatedWorkflowID);
				ok_message.set_activity(new_activity);
				sendMessageToMiddleware(ok_message);
				activityMasterNumber = new_activity.get_engine_number();	
			}
		}
		else {
			printLine("Received the update a second time.");
			new_activity = (Activity) message_list.get(0);
			SynchronizationMessage ok_message = new SynchronizationMessage("OK", false, engineNumber, new_activity.get_engine_number(), replicatedWorkflowID);
			ok_message.set_activity(new_activity);
			sendMessageToMiddleware(ok_message);
			activityMasterNumber = new_activity.get_engine_number();
		}
	}
	
	/**
	 * A method that checks if a Viewchange is necessary, i.e. the master hasn't responded
	 * for a while.
	 * 
	 * @throws Exception
	 */
	//TODO set Viewchange lower
	public void checkIfViewchangeIsNecessary() throws Exception {
		if((new Date()).getTime() - oldTime >= timeout + randomGenerator.nextInt(200)){
			printLine("We are initiating a Viewchange.");
			logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "master_has_failed" + "|" + "-" + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
			initiateViewchange();
			oldTime = (new Date()).getTime();
		}
	}
	
	/**
	 * A method that initiates a viewchange vote.
	 * 
	 * @throws Exception
	 */
	public void initiateViewchange() throws Exception {
		System.out.println("Initiating a viewchange for workflow " + replicatedWorkflowID + ".");
		currentView++;
		SynchronizationMessage viewchange_message = new SynchronizationMessage("Viewchange", true, engineNumber, replicatedWorkflowID);
		viewchange_message.set_view(currentView);
		engineNumberSet = new HashSet<Integer>();
		voteList = new ArrayList<SynchronizationMessage>();
		viewchangeInProgress = true;
		sendMessageToMiddleware(viewchange_message);
	}
	
	/**
	 * A method for processing a viewchange request from another replica.
	 * 
	 * @throws Exception
	 */
	public void processViewchange(SynchronizationMessage middlewareMessage) throws Exception {
		//reply with vote
		if(currentView < middlewareMessage.get_view()){
			currentView = middlewareMessage.get_view();
			
			int get_engine_number = middlewareMessage.get_engine_number();
			SynchronizationMessage vote_message = new SynchronizationMessage("Vote for Viewchange", false, engineNumber, get_engine_number, replicatedWorkflowID);
			vote_message.set_state_list(stateList);
			
			//getting the last executed activity
			vote_message.set_activity(null);
			//krawczls: new code, checking if master crashed after execution and update
			if(activityList.size() > 0 && activityList.get(activityList.size() - 1).get_status().equals("UncommittedSkipped")) {
				((Activity) activityList.get(activityList.size() - 1)).set_status("Skipped");
				vote_message.set_activity((Activity) activityList.get(activityList.size() - 1));
			}
			else {
				for (int i = 0; i < activityList.size() - 1; i++){
					if((((Activity) activityList.get(i)).get_status()).equals("Executed") || (((Activity) activityList.get(i)).get_status()).equals("Skipped")) {						
						vote_message.set_activity((Activity) activityList.get(i));
					}
				}
			}
			vote_message.set_last_committed_view(lastCommittedView);
				
			sendMessageToMiddleware(vote_message);
		}
		//reply with reject
		else{
			int get_engine_number = middlewareMessage.get_engine_number();
			SynchronizationMessage reject_message = new SynchronizationMessage("Reject Viewchange", false, engineNumber, get_engine_number, replicatedWorkflowID);
			sendMessageToMiddleware(reject_message);
		}
	}
	
	/**
	 * A method for processing a vote for the viewchange.
	 * 
	 * @throws Exception
	 */
	public void processVoteForViewchange(SynchronizationMessage middlewareMessage) throws Exception {
		voteList.add(middlewareMessage);		
		if (engineNumberSet.add(middlewareMessage.get_engine_number())) {
			printLine("Viewchange Vote from engine " + middlewareMessage.get_engine_number() + " received.");
		}		
		//Viewchange is successful
		//voting starts in several stages
		if(engineNumberSet.size() + 1 >= majority){
			printLine("Viewchange successful.");
			viewchangeInProgress = false;
			planningMaster = true;
			int number = lastCommittedView;
			//boolean enter_next_voting_stage = false;
			SynchronizationMessage vote_winner = new SynchronizationMessage();
			Activity test_activity = new Activity();
			//first voting stage
			for(int i = 0; i < voteList.size(); i++) {
				if(((SynchronizationMessage)voteList.get(i)).get_last_committed_view() > number){
					//enter_next_voting_stage = false;
					number = ((SynchronizationMessage)voteList.get(i)).get_last_committed_view();
				}
				else if(((SynchronizationMessage)voteList.get(i)).get_last_committed_view() == number){
					//enter_next_voting_stage = true;
				}
			}
			Iterator<SynchronizationMessage> itr = voteList.iterator();
			while(itr.hasNext()) {
				vote_winner = itr.next();			
				if(vote_winner.get_last_committed_view() != number) {
					itr.remove();
				}
			}
			//adding ourselves to the vote list if necessary
			if(lastCommittedView == number) {
				vote_winner = new SynchronizationMessage("Vote for Viewchange", false, engineNumber, engineNumber, replicatedWorkflowID);
				vote_winner.set_activity(null);
				if(activityList.size() > 0 && activityList.get(activityList.size() - 1).get_status().equals("UncommittedSkipped")) {
					((Activity) activityList.get(activityList.size() - 1)).set_status("Skipped");
					vote_winner.set_activity((Activity) activityList.get(activityList.size() - 1));
				}
				else {
					for (int i = activityList.size() - 1; i > 0; i--){
						if((((Activity) activityList.get(i)).get_status()).equals("Executed") || (((Activity) activityList.get(i)).get_status()).equals("Skipped")) {
							vote_winner.set_activity((Activity) activityList.get(i));
							i = 0;
						}
					}
				}
				vote_winner.set_last_committed_view(lastCommittedView);
			}
			//second voting stage
			number = -1;
			for(int i = 0; i < voteList.size(); i++) {
				test_activity = ((SynchronizationMessage) voteList.get(i)).get_activity();
				for(int j = 0; j < activityList.size(); j++) {
					if((test_activity.get_xpath()).equals(((Activity) activityList.get(j)).get_xpath())) {
						if(number < j) {
							number = j;
							vote_winner = (SynchronizationMessage) voteList.get(i);
						}
					}
				}
			}
			vote_winner.set_message("Viewchange Update");
			vote_winner.set_all(true);
			vote_winner.set_engine_number(engineNumber);

			remoteObject = vote_winner;
			
			//TODO send viewchange update
			sendMessageToMiddleware(vote_winner);
		}
	}

	/**
	 * A method for processing the update the replicas receive after a successful viewchange.
	 * 
	 * @throws Exception
	 */
	public void processViewchangeUpdate(SynchronizationMessage middlewareMessage) throws Exception {

		lastCommittedView = middlewareMessage.get_last_committed_view();
		for(int i = 0; i < middlewareMessage.get_state_list().size(); i++){
			for(int j = 0; j < stateList.size(); j++){
				if(((Write_Variable) stateList.get(j)).getVariableName().equals(((Write_Variable) middlewareMessage.get_state_list().get(i)).getVariableName())){
					if(!((Write_Variable) stateList.get(j)).getChanges().equals(((Write_Variable) middlewareMessage.get_state_list().get(i)).getChanges())){
						((Write_Variable) stateList.get(j)).setChanges(((Write_Variable) middlewareMessage.get_state_list().get(i)).getChanges());
								
						Write_Variable write_message = new Write_Variable();
							
						write_message.setProcessID(((Write_Variable) stateList.get(j)).getProcessID());
						write_message.setScopeID(((Write_Variable) stateList.get(j)).getScopeID());
						write_message.setVariableName(((Write_Variable) stateList.get(j)).getVariableName());
						write_message.setChanges(((Write_Variable) stateList.get(j)).getChanges());	
						
						//TODO krawczls:send write message to middleware?
						ObjectMessage message_producer_local = sessionLocal.createObjectMessage(write_message);
						producerProcess.send(message_producer_local);
					}
				}
			}
		}			        							  
		//send OK
		if(!planningMaster) {
			SynchronizationMessage ok_message = new SynchronizationMessage("Viewchange OK", false, engineNumber, middlewareMessage.get_engine_number(), replicatedWorkflowID);
			sendMessageToMiddleware(ok_message);
		} else {
			if (engineNumberSet != null) {
				engineNumberSet.clear();
			}
			engineNumberSet = new HashSet<Integer>();
		}
	}

	/**
	 * A method for processing the viewchange OK the viewchange initiator
	 * receives from the other replicas.
	 * 
	 * @throws Exception
	 */
	public void processViewchangeOK(SynchronizationMessage middlewareMessage) throws Exception {
		
		if (engineNumberSet.add(middlewareMessage.get_engine_number())) {
			printLine("Viewchange OK from engine " + middlewareMessage.get_engine_number() + " received.");    			
		}
					
		if(engineNumberSet.size()+ 1 >= majority) {
			printLine("All Viewchange OKs received.");
			planningMaster = false;
			//int next_activity_master = randomGenerator.nextInt(engineNumberSet.size() + 1);
			//int next_activity_master = randomGenerator.nextInt(repDegree + 1);
			
			if(!oneMasterForAllActivities) {
				boolean masterFound = false;
				
				while (!masterFound) {
					int next_activity_master = randomGenerator.nextInt(repDegree + 1);
					if(next_activity_master == repDegree) {
						activityMasterNumber = engineNumber;
						activityMaster = true;
						masterStatus = "--M ";
						logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "new_master_selected" + "|" + "-" + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
							
						activityList.remove(activityList.size() - 1);
						messageToProcess = holdMessage;
					}
					else if(engineNumberSet.contains(next_activity_master)) {
						activityMasterNumber = next_activity_master; //@schaefdd: Check whether this assignment is correct or could assign an invalid Engine ID
						activityMaster = false;
						masterStatus = "--F ";
					}
				}
			} else {
				activityMasterNumber = engineNumber;
				activityMaster = true;
				masterStatus = "--M ";
				logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "new_master_selected" + "|" + "-" + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
				
				activityList.remove(activityList.size() - 1);
				messageToProcess = holdMessage;
			}
			
			SynchronizationMessage commit_message = new SynchronizationMessage("Viewchange Commit", true, engineNumber, activityMasterNumber, replicatedWorkflowID);									
			sendMessageToMiddleware(commit_message);
			
			lastCommittedView = lastCommittedView + 1;
		}
	}

	/**
	 * A method for processing a viewchnage commit, the replicas receive from the 
	 * viewchange initiator.
	 * 
	 * @throws Exception
	 */
	public void processViewchangeCommit(SynchronizationMessage middlewareMessage) throws Exception {
		if(middlewareMessage.get_next_master() == engineNumber){
			activityMaster = true;
			activityMasterNumber = engineNumber;
			masterStatus = "--M ";
			messageToProcess = holdMessage;
			holdMessage = null;
			logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "new_master_selected" + "|" + "-" + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);

			activityList.remove(activityList.size() - 1);
			messageToProcess = holdMessage;
		}
		else{
			activityMasterNumber = middlewareMessage.get_next_master();
		}
		lastCommittedView = lastCommittedView + 1;
	}

	/**
	 * A method that simulates an error, where all messages from the other engines get dropped.
	 * Additionally if parameter suspend is true, the engine suspends all execution and enters and endless loop.
	 * 
	 * @param suspend
	 */
	/*public void simulateError(boolean suspend) throws Exception {
		remoteObject = new Boolean(suspend);
		while(suspend) {
			//do nothing
			remoteObject = receiveMessageFromMiddleware(0);
		}
	}*/
	
	/**
	 * A method that simulates an error where no message arrive at the engine.
	 * 
	 * @throws Exception
	 */
	/*public void looseAllMessages() throws Exception {
		printLine("We are loosing all messages.");
		getMessage = null;
		Boolean helper = (Boolean) remoteObject;
		remoteObject = receiveMessageFromMiddleware(10);
		remoteObject = helper;
	}*/
	
	/**
	 * A method for closing the connections to the SynchronizationMiddleware and the replicas.
	 * 
	 * @throws Exception
	 */
	public void closeConnections() throws Exception {
        // Clean up
        sessionLocal.close();
        //connection_local.close();
        //sessionHAWKSController.close();
        //connectionMiddleware.close();
	}
	
	/**
	 * A method that properly finishes the execution of a replicated workflow instance.
	 * 
	 * @throws Exception
	 */
	public void finishWorkflowExecution() throws Exception {
        printLine("We are closing all connections.");
        SynchronizationMessage finish_message = new SynchronizationMessage();
        finish_message.set_message("Finish");
        finish_message.setProcessID(processID);
        //sendMessageToSynchronizationMiddleware(finish_message);
        printLine("State List size: " + stateList.size());
        if(finished) {
			for(int i = 0; i < stateList.size(); i++){
				printLine(((Write_Variable) stateList.get(i)).getVariableName());
				printLine(((Write_Variable) stateList.get(i)).getChanges());
			}
        }
        else {
        	printLine("Suspending Instance because of a majority consensus, although we didn't finish in time.");
        }
		logs.add(myIP + "|" + replicatedWorkflowID + "|" + repDegree + "|" + masterStatus + "|" + "instance_suspended" + "|" + "-" + "|" + (new Date()).getTime() + "|" + enginesEqualToReplicationDegree);
		writeToLogFile(logs);
        closeConnections();
	}
	
	@Override
	public int hashCode() {
		return replicatedWorkflowID.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		if (obj.getClass() == this.getClass()) {
			SyncUnitWorkflowInstance b = (SyncUnitWorkflowInstance)obj;
			if (this.replicatedWorkflowID.equals(b.replicatedWorkflowID)) {
				return true;
			}
		}
		return false;
	}
}