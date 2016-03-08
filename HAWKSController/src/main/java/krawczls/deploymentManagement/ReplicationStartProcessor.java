package krawczls.deploymentManagement;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

//import java.util.UUID;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
//import javax.persistence.EntityManagerFactory;
//import javax.persistence.Persistence;

import krawczls.deploymentManagement.ReplicationStartContext;
import krawczls.deploymentManagement.ReplicationDeploymentManager;
import krawczls.executionEngineRegistry.WorkflowEngine;
import krawczls.executionEngineRegistry.WorkflowEngineRegistry;
import krawczls.messages.ProcessStartMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.ode.bpel.extensions.sync.SynchronizationMessage;

import constants.Constants;

public class ReplicationStartProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    ReplicationDeploymentManager deployer = new ReplicationDeploymentManager();

    private String generateReplicatedWorkflowUniqueID() {
    	Random rand = new Random();
    	int randInt = rand.nextInt();
    	long nanoTime = System.nanoTime();
    	long date = (new java.util.Date()).getTime();
    	String id = String.valueOf(date).concat(String.valueOf(nanoTime).concat(String.valueOf(randInt)));
        return id;
    }

    public void process(Exchange exchange) throws Exception {
        //System.out.println("Starting");
    	ReplicationStartContext.counter++;
    	//TODO check if this is necessary
    	if(ReplicationStartContext.counter < 2) {
    		ReplicationStartContext.startFailureNow = true;
    	}
    	if(Constants.DEBUG_LEVEL > 0) {
    		System.out.println("~~~~" + ReplicationStartContext.counter);
    	}
    	try {
	        ProcessStartMessage message = (ProcessStartMessage)exchange.getIn().getBody();
	        
	        ArrayList<WorkflowEngine> engines = this.registry.getAllActiveEngines();
	        ArrayList<WorkflowEngine> actualEngines = new ArrayList<WorkflowEngine>();
	        ArrayList<String> actualEnginesIPs = new ArrayList<String>();
	        
	        String processName = message.getProcessName();
	        for(int i = 0; i < engines.size(); i++) {
	            String workflowEngineIp = ((WorkflowEngine)engines.get(i)).getWorkflowEngineIp();
	            if (this.registry.checkIfDeployed(workflowEngineIp, processName) == 1) {
	            	actualEngines.add(((WorkflowEngine)engines.get(i)));
	            	actualEnginesIPs.add(workflowEngineIp);
	            }
	        }
	        
	        //TODO @lukas Round-robin
	        //round robin is only correct for 1 process model on all engines
	        int master = 0;
	        int replicationDegree = message.getReplicationGrade();
	        if (replicationDegree < actualEnginesIPs.size()) {
	        	if(ReplicationStartContext.withRoundRobin) {
		        	synchronized(ReplicationStartContext.roundRobinEngineList) {
		        		synchronized(ReplicationStartContext.roundRobinIndex) {
				        	if(ReplicationStartContext.roundRobinEngineList.size() >= actualEnginesIPs.size()) {
				        		ReplicationStartContext.roundRobinEngineList = actualEnginesIPs;
				        		master = ReplicationStartContext.roundRobinIndex;
				        		ReplicationStartContext.roundRobinIndex++;
				        		if(master >= actualEnginesIPs.size()) {
				        			master = 0;
				        		}
				        	}
				        	else if(ReplicationStartContext.roundRobinEngineList.size() < actualEnginesIPs.size()) {
				        		ReplicationStartContext.roundRobinEngineList = actualEnginesIPs;
				        		master = ReplicationStartContext.roundRobinIndex;
				        		ReplicationStartContext.roundRobinIndex++;
				        	}
		        		}
		        		int actualEnginesSize = actualEnginesIPs.size();
		        		actualEnginesIPs = new ArrayList<String>();
		        		for(int i = 0; i < replicationDegree; i++) {
		        			actualEnginesIPs.add(ReplicationStartContext.roundRobinEngineList.get((master + i) % actualEnginesSize));
		        		}
		        	}
	        	}
	        	else {
		        	for(int i = actualEngines.size(); i > 1; i--) {
		        		for(int j = 0; j < i - 1; j++) {
		        			if(actualEngines.get(j).numberOfActiveProcesses() > actualEngines.get(j+1).numberOfActiveProcesses()) {
		        				WorkflowEngine bufferEngine = actualEngines.get(j);
		        				actualEngines.set(j, actualEngines.get(j+1));
		        				actualEngines.set(j+1, bufferEngine);
		        				String bufferIP = actualEnginesIPs.get(j);
		        				actualEnginesIPs.set(j, actualEnginesIPs.get(j+1));
		        				actualEnginesIPs.set(j+1, bufferIP);
		        			}
		        		}
		        	}
	        	}
	        }
	        System.out.println(replicationDegree);
	        System.out.println(actualEnginesIPs.size());
	        if (replicationDegree <= actualEnginesIPs.size()) {
	            final String replicatedWorkflowID = this.generateReplicatedWorkflowUniqueID();
	            if(Constants.DEBUG_LEVEL > 0) {
	            	System.out.println("+++ Starting: replicated workflow " + replicatedWorkflowID + " +++");
	            }
	            
	            startAndInformEngines(replicatedWorkflowID, replicationDegree, message, engines, actualEnginesIPs);
	                      
	            final ArrayList<WorkflowEngine> replicas = this.registry.getAllEnginesOfCertainProcess(replicatedWorkflowID, ReplicationStartContext.emf);
	            
	            addRoute(replicatedWorkflowID, exchange, replicas);
	            
	            loggingNewReplicatedWorkflowExecutionStarted(replicatedWorkflowID, message, engines, replicationDegree, actualEnginesIPs);
	        } else {
	        	if(Constants.DEBUG_LEVEL > 0) {
	            	System.out.println("Found less than " + message.getReplicationGrade() + " Engines.");
	        	}
	        }
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        //System.out.println("Starting");
    }

    private void startAndInformEngines(String replicatedWorkflowID, 
    		int replicationDegree, 
    		ProcessStartMessage message, 
    		ArrayList<WorkflowEngine> engines, 
    		ArrayList<String> actualEnginesIPs) throws JMSException {
    	for(int j = 0; j < replicationDegree; j++) {
        	this.registry.addNewActiveProcessInstanceRoleAndFinish(actualEnginesIPs.get(j), replicatedWorkflowID, j + 1, false);
        	ReplicationStartContext.connection.start();
            Session session = ReplicationStartContext.connection.createSession(false, 1);
            Destination destination = session.createQueue("de.unistuttgart.rep." + actualEnginesIPs.get(j));
            MessageProducer producer = session.createProducer(destination);
            ArrayList<Object> listMessage = new ArrayList<Object>();
            //add name of the process model to instantiate
            listMessage.add(String.valueOf(message.getNamespace()) + message.getProcessName());
            //add the id of the replicated workflow execution of this process instance
            listMessage.add(replicatedWorkflowID);
            //add the replication degree of this replicated workflow execution
            listMessage.add(replicationDegree);
            //add the number assigned to the engine in this replicated workflow execution
            listMessage.add(j + 1);
            //decide on the master for a replicated workflow execution using round robin
            //and add a flag for the master to the list
            if(ReplicationStartContext.withRoundRobin) {
                if (j == 0) {
                    listMessage.add("true");
                } else {
                    listMessage.add("false");
                }        	
            }
            else {
	            if(ReplicationStartContext.master >= replicationDegree) {
	            	ReplicationStartContext.master = 0;
	            }
	            if (j == ReplicationStartContext.master) {
	                listMessage.add("true");
	            } else {
	                listMessage.add("false");
	            }
            }
            //add the heartbeat rate of the master to the list
            listMessage.add(ReplicationStartContext.heartbeatRate);
            //add the timeout for the engines to the list
            /*if(ReplicationStartContext.withFailure) {
                if(replicationDegree == 9) {
                	listMessage.add(ReplicationStartContext.timeout * 2);
                }
                else if(replicationDegree == 17) {
                	listMessage.add(ReplicationStartContext.timeout * 4);
                }
                else {
                	listMessage.add(ReplicationStartContext.timeout);
                }
            }
            else {*/
            	listMessage.add(ReplicationStartContext.timeout);
            //}
            //TODO krawczls check if this works
            //adding the start message
            //listMessage.add(message.getMessage());
            //TODO krawczls
            //adding the name of the process/service to address
            //listMessage.add(message.getProcessServiceName());
            //add a flag to the list, that tells us if we are using unfair measuring
            if(replicationDegree == engines.size()) {
            	listMessage.add(new Boolean(true));
            }
            else {
            	listMessage.add(new Boolean(false));
            }
            //add a flag for the error to the list
            //TODO delete this list entry from middleware and engines, it is obsolete engines log each failure themselves
            listMessage.add(new Boolean(false));
            ObjectMessage objectMessage = session.createObjectMessage(listMessage);
            producer.send(objectMessage);
            session.close();
        }
    	//TODO check if new round robin works
    	if(!ReplicationStartContext.withRoundRobin) {
        	ReplicationStartContext.master++;
    	}
	}
    
	//TODO check if this works
	//starting process over the SynchronizationUnit
	private void loggingNewReplicatedWorkflowExecutionStarted(String replicatedWorkflowID, 
    		ProcessStartMessage message, 
    		ArrayList<WorkflowEngine> engines, 
    		int replicationDegree, 
    		ArrayList<String> actualEnginesIPs) throws Exception {
    	for(int j = 0; j < replicationDegree; j++) {
            this.deployer.sendSOAPToService(message.getMessage(), message.getProcessServiceName(), actualEnginesIPs.get(j));
        }   
        if(replicationDegree == engines.size() && replicationDegree != 17) {
        	ReplicationStartContext.writeToLogFile("middleware" + "|" + replicatedWorkflowID + "|" + replicationDegree + "|--M |" + "instance_started" + "|-|" + (new Date()).getTime() + "|" + true);
        	ReplicationStartContext.writeToLogFile("middleware" + "|" + replicatedWorkflowID + "|" + replicationDegree + "|--M |" + "activity_ready" + "|-|" + (new Date()).getTime() + "|" + true);
        }
        else {
        	ReplicationStartContext.writeToLogFile("middleware" + "|" + replicatedWorkflowID + "|" + replicationDegree + "|--M |" + "instance_started" + "|-|" + (new Date()).getTime() + "|" + false);
        	ReplicationStartContext.writeToLogFile("middleware" + "|" + replicatedWorkflowID + "|" + replicationDegree + "|--M |" + "activity_ready" + "|-|" + (new Date()).getTime() + "|" + false);
        }
	}

	/**
     * Adding a new Route for the replicatedWorkflowID.
     * 
     * @param replicatedWorkflowID
     * @param exchange
     * @param replicas
     */
    private void addRoute(final String replicatedWorkflowID, Exchange exchange, final ArrayList<WorkflowEngine> replicas) {
    	try {
    		ReplicationStartContext.connection.start();
    		Session session = ReplicationStartContext.connection.createSession(false, 1);
    		session.createQueue(replicatedWorkflowID);
    		exchange.getContext().addRoutes(new RouteBuilder() {

    			@Override
    			public void configure() throws Exception {
    				from("activemq:queue:" + replicatedWorkflowID)
    				.routeId(replicatedWorkflowID + " Route")
    				//.processRef("workflowEngineProcessor");
    				//the processor used for the routing
    				.process(new Processor() {
    					WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    					//EntityManagerFactory myEMF = Persistence.createEntityManagerFactory("workflowEngineRegistry");
    					ArrayList<WorkflowEngine> myEngines = replicas;

    					Thread stop;

    					public void process(final Exchange exchange) throws Exception {
    						//System.out.println("Workflow Engine Processor started.");
    						String route_finished = routeMessages(exchange);
    						
    						//remove the route
    						if(!route_finished.equals("false")) {        			            
    							if(stop == null) {
    								stop = new Thread() {
    									@Override
    									public void run() {
    										try {
    											exchange.getContext().stopRoute(replicatedWorkflowID + " Route");
    											if(Constants.DEBUG_LEVEL > 0) {
    												System.out.println("Finishing route " + replicatedWorkflowID);
    											}
    											exchange.getContext().removeRoute(replicatedWorkflowID + " Route");
    											if(Constants.DEBUG_LEVEL > 0) {
    												System.out.println("Finishing route " + replicatedWorkflowID);
    											}
    											//myEMF.close();
    											List<Route> listRoute = exchange.getContext().getRoutes();
    											for(int i = 0; i < listRoute.size(); i++) {
    												if(i == (listRoute.size() - 1)) {
    													if(Constants.DEBUG_LEVEL > 0) {
    														System.out.println(listRoute.get(i).getId());
    													}
    												}
    											}
    										}
    										catch(Exception e) {
    											e.printStackTrace();
    										}
    									}
    								};
    							}        			            
    							stop.start();           			            
    						}
    					}

						private String routeMessages(Exchange exchange) throws JMSException, Exception {
							String route_finished = "false";
							if (exchange.getIn().getBody() instanceof SynchronizationMessage) {
    							final SynchronizationMessage message = (SynchronizationMessage)exchange.getIn().getBody();
    							if(Constants.DEBUG_LEVEL > 0) {
    								System.out.println(message);
    							}
    							
    							if(message.get_message().equals("Finish")) {   
    								//we get a finish message from one of the engines
    								if (handleFinishMassage(message)) {
										route_finished = replicatedWorkflowID;
    								}
    							} else { 
    								//we get a regular synchronization message (not a finish message) from the engines
    								handleOtherSyncMessage(message);
    							}
    						} else if (exchange.getIn().getBody() instanceof String) {			        	
    							String message = (String)exchange.getIn().getBody();
    							this.registry.updateWorkflowEngineTimeOfLastHeartbeat(message);
    						}
							return route_finished;
						}

						private void handleOtherSyncMessage(SynchronizationMessage message) throws JMSException {
							for(int i = 0; i < myEngines.size(); i++) {
								WorkflowEngine workflowEngine = myEngines.get(i);
								// Get the instance to which the message is routed
								if (message.get_all()) {
									if (workflowEngine.isCurrentlyExecutingWorkflow(replicatedWorkflowID)
											&& workflowEngine.getRoleForWorkflow(replicatedWorkflowID) != message.get_engine_number()) {
										sendMessage(workflowEngine, message);
									}
								} else if (workflowEngine.isCurrentlyExecutingWorkflow(replicatedWorkflowID)
										&& workflowEngine.getRoleForWorkflow(replicatedWorkflowID) == message.get_next_master()) {
									sendMessage(workflowEngine, message);
								}
							}
						}

						private void sendMessage(WorkflowEngine workflowEngine,
								SynchronizationMessage message) throws JMSException {
							ReplicationStartContext.connection.start();
							Session session = ReplicationStartContext.connection.createSession(false, 1);
							//Queue destination = session.createQueue(replicatedWorkflowID + " to " + ((WorkflowEngine)actualEngines.get(i)).getWorkflowEngineIp());
							Queue destination = session.createQueue("de.unistuttgart.rep." + workflowEngine.getWorkflowEngineIp());
							MessageProducer producer = session.createProducer((Destination)destination);
							SynchronizationMessage newMessage = new SynchronizationMessage(message);
							if(Constants.DEBUG_LEVEL > 0) {
								System.out.println("---o Sending message " + message.get_message() + " for " + replicatedWorkflowID + " to " + workflowEngine.getWorkflowEngineIp() + " ---");
							}
							ObjectMessage objectMessage = session.createObjectMessage(newMessage);
							producer.send(objectMessage);

							session.close();
						}

						private boolean handleFinishMassage(SynchronizationMessage syncMessage) throws JMSException, Exception {
							String engineIp = null;
							boolean alreadyFinished = false;
							boolean isSetRouteFinished = false;
							if(myEngines.size() > 0) {
								for(int i = 0; i < myEngines.size(); i++) {
									WorkflowEngine workflowEngine = myEngines.get(i);
									if (workflowEngine.isCurrentlyExecutingWorkflow(replicatedWorkflowID) 
											&& workflowEngine.getRoleForWorkflow(replicatedWorkflowID) == syncMessage.get_engine_number()) {
										engineIp = workflowEngine.getWorkflowEngineIp();
									}
								}
								alreadyFinished = this.registry.updateFinish(engineIp, replicatedWorkflowID, true, ReplicationStartContext.emf);
								if(!alreadyFinished) {
									if(this.registry.checkIfMajorityFinished(replicatedWorkflowID, ReplicationStartContext.emf)) {
										for(int i = 0; i < myEngines.size(); i++) {
											ReplicationStartContext.connection.start();
											Session session = ReplicationStartContext.connection.createSession(false, 1);
											Queue destination = session.createQueue("de.unistuttgart.rep." + ((WorkflowEngine)myEngines.get(i)).getWorkflowEngineIp());
											//Queue destination = session.createQueue(replicatedWorkflowID + " to " + ((WorkflowEngine)myEngines.get(i)).getWorkflowEngineIp());
											MessageProducer producer = session.createProducer((Destination)destination);
											SynchronizationMessage newMessage = new SynchronizationMessage(syncMessage);
											if(Constants.DEBUG_LEVEL > 0) {
												System.out.println("---x Sending message " + syncMessage.get_message() + " for " + replicatedWorkflowID + " to " + ((WorkflowEngine)myEngines.get(i)).getWorkflowEngineIp() + " ---");
											}
											ObjectMessage objectMessage = session.createObjectMessage(newMessage);
											producer.send(objectMessage);

											this.registry.deleteActiveProcessInstanceRoleAndFinish(((WorkflowEngine)myEngines.get(i)).getWorkflowEngineIp(), replicatedWorkflowID, ReplicationStartContext.emf);          				        	                	

											session.close();
										}
										if(Constants.DEBUG_LEVEL > 0) {
											System.out.println("******* Finished " + replicatedWorkflowID + " *******");
										}
										isSetRouteFinished = true;
									}
								} else {
									if(Constants.DEBUG_LEVEL > 0) {
										System.out.println("******* Already finished " + replicatedWorkflowID + " *******");
									}
								}
							} else {
								if(Constants.DEBUG_LEVEL > 0) {
									System.out.println("******* Already finished " + replicatedWorkflowID + " *******");
								}
							}
							return isSetRouteFinished;
						}
    				})
    				.end();
    			}
    		});
    		session.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}

    }
}