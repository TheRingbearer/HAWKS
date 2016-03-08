package org.apache.ode.bpel.extensions.sync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.jms.ObjectMessage;

import org.apache.commons.lang.SystemUtils;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Suspend_Instance;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;

/**
* A thread in which the execution of a replicated workflow execution is controlled. 
*/
public class SynchronizationUnitThread implements Runnable {
        
	boolean finishThread = false;
	private HashMap<String,SyncUnitWorkflowInstance> workflowInstanceMap 
							= new HashMap<String,SyncUnitWorkflowInstance>();
	
//	public SynchronizationMessage syncMessage = null;
//	public InstanceEventMessage workflowMessage = null;

	public ArrayList<Object> inputQueue = new ArrayList<Object>();
	
	boolean isSimulateFailure = false;
	
	String myIP = "-";
	int replicationDegree = 0;
	
	int waitTimeForInputQueueToContainMessage = 0;
	int viewchangeCheckInterval = 25;
	
	/**
	 * Instantiates a new SynchronizationUnitThread, 
	 * which is responsible to execute all incoming messages of the inputQueue.
	 * 
	 * @param inputQueue The input queue on which the thread is working.
	 */
	public SynchronizationUnitThread(ArrayList<Object> inputQueue, int waitTimeForInputQueueToContainMessage, int viewchangeCheckInterval) {
		this.inputQueue = inputQueue;
		this.waitTimeForInputQueueToContainMessage = waitTimeForInputQueueToContainMessage;
		this.viewchangeCheckInterval = viewchangeCheckInterval;
	}
	
	public void writeToLogFile(String text) {
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
			bWriter.write(text);
			bWriter.newLine();
			bWriter.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Receive message from the input queue.
	 * @return The first message in the queue. If the queue is empty return null.
	 * @throws Exception
	 */
	public Serializable receiveMessageFromInputQueue() {
		Serializable message = null;
		if(inputQueue.size() > 0) {
			message = (Serializable) inputQueue.get(0);
			inputQueue.remove(0);
		}
		return message;
	}
	
	/**
	 * Adds a new workflow instance that is managed by this Thread.
	 * 
	 * @param myIP
	 * @param middlewareIP
	 * @param id
	 * @param replicatedWorkflowID
	 * @param repDegree
	 * @param engineNumber
	 * @param planningMaster
	 * @param firstActivityReady
	 * @param heartbeatRate
	 * @param timeout
	 * @param enginesEqualToReplicationDegree
	 * @param error
	 * @throws Exception
	 */
	public synchronized void addNewWorkflowInstance(String myIP, 
			String middlewareIP, 
			long id, 
			String replicatedWorkflowID, 
			int repDegree, 
			int engineNumber, 
			boolean planningMaster, 
			Activity_Ready firstActivityReady, 
			int heartbeatRate, 
			int timeout, 
			boolean enginesEqualToReplicationDegree, 
			boolean error) throws Exception {
		this.myIP = myIP;
		this.replicationDegree = repDegree;
		SyncUnitWorkflowInstance workflowInstance = new SyncUnitWorkflowInstance(myIP, 
				middlewareIP, id, replicatedWorkflowID, repDegree, engineNumber, 
				planningMaster, firstActivityReady, heartbeatRate, timeout, enginesEqualToReplicationDegree, error);
		workflowInstance.setUpWorkflowExecution(firstActivityReady);
		synchronized(workflowInstanceMap) {
			workflowInstanceMap.put(replicatedWorkflowID,workflowInstance);
			workflowInstanceMap.put(String.valueOf(firstActivityReady.getProcessID()), workflowInstance);
		}

		/*try {
			workflowInstance.setupConnections();
		} catch (Exception e) {
			e.printStackTrace();
		}*/   					
	}
	
	/**
	 * Returns the workflow instance to which the dequeuedMessage correlates.
	 * 
	 * @param dequeuedMessage The message which needs to be correlated.
	 * @return Correlated workflow instance.
	 * @throws Exception
	 */
	//TODO what happens when workflow instance is not found
	public SyncUnitWorkflowInstance getWorkflowInstance(Serializable dequeuedMessage) throws Exception {
		SyncUnitWorkflowInstance workflowInstance = null;
		
		if(dequeuedMessage instanceof SynchronizationMessage) {
			SynchronizationMessage syncMessage = (SynchronizationMessage) dequeuedMessage;
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SCT - SynchronizationMessage - We are responsible for " + workflowInstanceMap.size() + " workflow instances.");
			}
			workflowInstance = workflowInstanceMap.get(syncMessage.get_replicated_workflow_id());
		}
		else if (dequeuedMessage instanceof InstanceEventMessage) {
			InstanceEventMessage workflowMessage = (InstanceEventMessage) dequeuedMessage;
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("SCT - InstanceEventMessage - We are responsible for " + workflowInstanceMap.size() + " workflow instances.");
			}
			workflowInstance = workflowInstanceMap.get(String.valueOf(workflowMessage.getProcessID()));
		}
		return workflowInstance;
	}
	
	/**
	 * Process the Synchronization Message
	 * @param syncMessage
	 * @param workflowInstance
	 * @throws Exception 
	 */
	public void processSynchronizationMessage(SynchronizationMessage syncMessage, 
			SyncUnitWorkflowInstance workflowInstance) throws Exception {
		workflowInstance.checkObjectFromOtherEngines(syncMessage);
		//check if we can do something with a workflow message, now that we have received a message from the middleware
		if(workflowInstance.messageToProcess != null && !(syncMessage.get_message().equals("Heartbeat"))) {
			InstanceEventMessage workflowMessage = workflowInstance.messageToProcess;
			workflowInstance.messageToProcess = null;			
			processWorkflowMessage(workflowMessage, workflowInstance);
		}
	}	
	
	/**
	 * @param workflowMessage
	 * @param workflowInstance
	 * @throws Exception 
	 */
	public void processWorkflowMessage(InstanceEventMessage workflowMessage, SyncUnitWorkflowInstance workflowInstance) throws Exception {
		//in case we have more than 1 engine
		if(workflowInstance.repDegree > 1) {
            workflowInstance.checkObjectFromWorkflow(workflowMessage);
		}
		//in case we are working with only 1 engine
		else {
            if(workflowMessage instanceof Activity_Ready) {
            	workflowInstance.logs.add(workflowInstance.myIP + "|" + workflowInstance.replicatedWorkflowID + "|" + workflowInstance.repDegree + "|" + workflowInstance.masterStatus + "|" + "activity_ready" + "|" + workflowInstance.getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + workflowInstance.enginesEqualToReplicationDegree);
            	workflowInstance.startActivity(workflowMessage);
            }
            if(workflowMessage instanceof Activity_Complete) {
            	workflowInstance.logs.add(workflowInstance.myIP + "|" + workflowInstance.replicatedWorkflowID + "|" + workflowInstance.repDegree + "|" + workflowInstance.masterStatus + "|" + "activity_complete" + "|" + workflowInstance.getActivityType(workflowMessage) + "|" + (new Date()).getTime() + "|" + workflowInstance.enginesEqualToReplicationDegree);
            }
            if(workflowMessage instanceof Instance_Suspended) {
            	workflowInstance.instanceNotFinished = false;
				SynchronizationMessage finish_message = new SynchronizationMessage("Finish", true, workflowInstance.engineNumber, workflowInstance.replicatedWorkflowID);
				workflowInstance.sendMessageToMiddleware(finish_message);
            }
            if(!workflowInstance.instanceNotFinished) {
            	workflowInstance.finishWorkflowExecution();
            	synchronized (workflowInstanceMap) {
            		workflowInstanceMap.remove(workflowInstance.replicatedWorkflowID);
            	}
            }
		}	
	}	
		
	/**
	 * @param dequeuedMessage 
	 * @param workflowInstance
	 * @throws Exception 
	 */
	public void processQueueMessage(Serializable dequeuedMessage, SyncUnitWorkflowInstance workflowInstance) throws Exception {
		//in case we have more than 1 engine
		SynchronizationMessage synchronizationMessage = null;
		InstanceEventMessage workflowEventMessage = null;
		if (dequeuedMessage instanceof SynchronizationMessage) {
			synchronizationMessage = (SynchronizationMessage) dequeuedMessage;
		} else if (dequeuedMessage instanceof InstanceEventMessage) {
			workflowEventMessage = (InstanceEventMessage) dequeuedMessage;
		} else if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("SyncUnitThread::processQueueMessage - Received unknown message type: " + dequeuedMessage);
		}
		 
		if(workflowInstance.repDegree > 1) {		
			if(workflowInstance.instanceNotFinished) {
		    	//the activity master sends a heartbeat at the start of every cycle
		    	if(workflowInstance.activityMaster){
		    		if((new Date()).getTime() - workflowInstance.heartbeat >= workflowInstance.heartbeatRate && !workflowInstance.finished){
		    			workflowInstance.sendHeartbeat();
		    			workflowInstance.heartbeat = (new Date()).getTime();
		    		}	
		    	}
				if(synchronizationMessage != null) {
					if(synchronizationMessage instanceof SynchronizationMessage) {
						workflowInstance.printLine("Received the following message from the HAWKS Controller: " + ((SynchronizationMessage )synchronizationMessage).toString());	
					}
					processSynchronizationMessage(synchronizationMessage, workflowInstance);
					//TODO check if this is the right place for this assignment
					workflowInstance.oldTime = (new Date()).getTime();
				}
				else if(workflowEventMessage != null) {	
					workflowInstance.printLine("Received the following message from the workflow: " + workflowInstance.getActivityType(workflowEventMessage));
					processWorkflowMessage(workflowEventMessage, workflowInstance);
				}
				if(!workflowInstance.instanceNotFinished && !workflowInstance.finished) {
					workflowInstance.printLine("Suspending Workflow Instance " + workflowInstance.processID);
					Suspend_Instance suspendWorkflowInstance = new Suspend_Instance();
					suspendWorkflowInstance.setProcessID(workflowInstance.processID);
					ObjectMessage suspendMessage = workflowInstance.sessionLocal.createObjectMessage(suspendWorkflowInstance);
					workflowInstance.producerProcess.send(suspendMessage);  
					//break;
					//TODO check if this is correct
	            	workflowInstance.finishWorkflowExecution();
	            	synchronized (workflowInstanceMap) {
	            		workflowInstanceMap.remove(workflowInstance.replicatedWorkflowID);
	            	}
				}
				else if(!workflowInstance.instanceNotFinished) {
	            	workflowInstance.finishWorkflowExecution();
	            	synchronized (workflowInstanceMap) {
	            		workflowInstanceMap.remove(workflowInstance.replicatedWorkflowID);
	            	}
				}
				if(workflowInstance.finished && workflowInstance.instanceNotFinished) {
					if((((Activity) workflowInstance.activityList.get(workflowInstance.activityList.size() - 1)).get_status()).equals("Executed") ||
					(((Activity) workflowInstance.activityList.get(workflowInstance.activityList.size() - 1)).get_status()).equals("Skipped")) {
						if((new Date()).getTime() - workflowInstance.finishTime >= 60*1000){
							SynchronizationMessage finish_message = new SynchronizationMessage("Finish", true, workflowInstance.engineNumber, workflowInstance.replicatedWorkflowID);
							workflowInstance.sendMessageToMiddleware(finish_message);
							workflowInstance.finishTime = (new Date()).getTime();
							workflowInstance.printLine("Sending finish message to middleware.");
						}
					}
				}
			}
            else {
            	workflowInstance.finishWorkflowExecution();
            	synchronized (workflowInstanceMap) {
            		workflowInstanceMap.remove(workflowInstance.replicatedWorkflowID);
            	}
            }
		}
		//in case we are working with only 1 engine
		else {
			if(workflowEventMessage != null) {				
				processWorkflowMessage(workflowEventMessage, workflowInstance);
			}
		}
		synchronizationMessage = null;
		workflowEventMessage = null;
	}
		
	/**
	 * Getting the workflow instance and then processing the message.
	 * @throws Exception
	 */
	public void getWorkflowInstanceAndProcessQueueMessage(Serializable dequeuedMessage) throws Exception {
		SyncUnitWorkflowInstance workflowInstance = getWorkflowInstance(dequeuedMessage);
		if(workflowInstance != null) {
			processQueueMessage(dequeuedMessage, workflowInstance);
		}
	}
	
	/* (non-Javadoc)
	 * * @see java.lang.Runnable#run()
	 * */
	public void run() {
		//thread for checking if the master of a replicated workflow instance has failed
		Thread thread = new Thread(){
			public void run(){
				while(!finishThread && replicationDegree > 1) {
					/*while(isSimulateFailure) {
						long time = (new Date()).getTime();
						writeToLogFile(myIP + "|-|" + replicationDegree + "|-|error_occured|-|" + (new Date()).getTime() + "|-");
						isSimulateFailure = false;
						while((new Date()).getTime() - time < 10000) {
							synchronized(this) {
								try {	
									wait(10000 - ((new Date()).getTime() - time));
								}
								catch (Exception e) {
									if(Constants.DEBUG_LEVEL > 0) {
										System.out.println("Failure wait time made no sense.");
									}
								}
							}
						}
					}*/
					synchronized (workflowInstanceMap) {
						for(SyncUnitWorkflowInstance workflowInstance : workflowInstanceMap.values()) {
							try {
								workflowInstance.checkIfViewchangeIsNecessary();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					try {
						sleep(viewchangeCheckInterval);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		thread.start();
		while(!finishThread) {
			try {
				if(isSimulateFailure) {
					if(replicationDegree != 0) {
						//ArrayList<String> writeToLogArray = new ArrayList<String>();
						//writeToLogArray.add(myIP + "|-|" + replicationDegree + "|-|error_occured|-|" + (new Date()).getTime() + "|-");
						writeToLogFile(myIP + "|-|" + replicationDegree + "|-|engine_failed|-|" + (new Date()).getTime() + "|-");
					}
					while(isSimulateFailure) {
						if(replicationDegree != 0) {
							//ArrayList<String> writeToLogArray = new ArrayList<String>();
							//writeToLogArray.add(myIP + "|-|" + replicationDegree + "|-|error_occured|-|" + (new Date()).getTime() + "|-");
							writeToLogFile(myIP + "|-|" + replicationDegree + "|-|error_occured|-|" + (new Date()).getTime() + "|-");
						}
						long time = (new Date()).getTime();
						isSimulateFailure = false;
						while((new Date()).getTime() - time < 10000) {
							synchronized(this) {
								try {	
									wait(10000 - ((new Date()).getTime() - time));
								}
								catch (Exception e) {
									if(Constants.DEBUG_LEVEL > 0) {
										System.out.println("Failure wait time made no sense.");
									}
								}
							}
						}
					}
					writeToLogFile(myIP + "|-|" + replicationDegree + "|-|engine_recovered|-|" + (new Date()).getTime() + "|-");
				}
				Serializable currentMessage = null;
				synchronized(inputQueue) {
					if(inputQueue.size() < 1) {
						if(waitTimeForInputQueueToContainMessage == 0) {
							inputQueue.wait();
						}
						else {
							inputQueue.wait(waitTimeForInputQueueToContainMessage); //TODO Remove Time because of busy waiting
						}
					}
					currentMessage = receiveMessageFromInputQueue();
				}
				if(currentMessage != null) {
					getWorkflowInstanceAndProcessQueueMessage(currentMessage);
				}
				currentMessage = null;
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}    						  	 								
	}
}