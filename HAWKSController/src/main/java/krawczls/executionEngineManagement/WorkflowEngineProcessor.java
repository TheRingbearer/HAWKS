package krawczls.executionEngineManagement;

//import java.util.ArrayList;

//import javax.jms.Destination;
//import javax.jms.MessageProducer;
//import javax.jms.ObjectMessage;
//import javax.jms.Queue;
//import javax.jms.Session;

//import krawczls.deploymentManagement.ReplicationStartContext;
//import krawczls.workflowEngineRegistry.WorkflowEngine;
import krawczls.executionEngineRegistry.WorkflowEngineRegistry;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
//import org.apache.ode.bpel.extensions.sync.SynchronizationMessage;

import constants.Constants;

public class WorkflowEngineProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    
    public void process(Exchange exchange) throws Exception {
    	//String route_finished = "false";
        //System.out.println("Workflow Engine Processor started.");
        /*if (exchange.getIn().getBody() instanceof SynchronizationMessage) {
            final SynchronizationMessage message = (SynchronizationMessage)exchange.getIn().getBody();
            if(message.get_message().equals("Finish")) {
            	ArrayList<WorkflowEngine> list = new ArrayList<WorkflowEngine>();
            	list = this.registry.getAllEnginesOfCertainProcess(message.get_replicated_workflow_id());
            	String engineIp = null;
            	boolean alreadyFinished = false;
            	if(list.size() > 0) {
            		for(int i = 0; i < list.size(); i++) {
            			for(int j = 0; j < list.get(i).getActiveProcessInstances().size(); j++) {
            				if(list.get(i).getActiveProcessInstances().get(j).equals(message.get_replicated_workflow_id())) {
            					if(list.get(i).getRoleInActiveProcesses().get(j).equals(message.get_engine_number())) {
            						engineIp = list.get(i).getWorkflowEngineIp();
            					}				
            				}
            			}
            		}
                	alreadyFinished = this.registry.updateFinish(engineIp, message.get_replicated_workflow_id(), true);
                	if(!alreadyFinished) {
                		if(this.registry.checkIfFinished(message.get_replicated_workflow_id())) {
                			for(int i = 0; i < list.size(); i++) {
	        	                ReplicationStartContext.connection.start();
	        	                Session session = ReplicationStartContext.connection.createSession(false, 1);
	        	                Queue destination = session.createQueue("de.unistuttgart.rep." + ((WorkflowEngine)list.get(i)).getWorkflowEngineIp());
	        	                MessageProducer producer = session.createProducer((Destination)destination);
	        	                SynchronizationMessage newMessage = new SynchronizationMessage(message);
	        	                
	        	                System.out.println("--- Sending message " + message.get_message() + " for " + message.get_replicated_workflow_id() + " to " + ((WorkflowEngine)list.get(i)).getWorkflowEngineIp() + " ---");
	        	                
	        	                ObjectMessage objectMessage = session.createObjectMessage(newMessage);
	        	                producer.send(objectMessage);
	        	                
	        	                this.registry.deleteActiveProcessInstanceRoleAndFinish(((WorkflowEngine)list.get(i)).getWorkflowEngineIp(), message.get_replicated_workflow_id());
	        	                     	                
	        	                session.close();
	                		}
	                		System.out.println("******* Finished " + message.get_replicated_workflow_id() + " *******");
	                		
	                		route_finished = message.get_replicated_workflow_id();
	                	}
                	}
                	else {
                		System.out.println("******* Already finished " + message.get_replicated_workflow_id() + " *******");
                	}
            	}
            	else {
            		System.out.println("******* Already finished " + message.get_replicated_workflow_id() + " *******");
            	}
            }
            else {
	            //System.out.println("Its a synchronization message with the message content: " + message.get_message() + " for " + message.get_replicated_workflow_id());
	            Boolean sendToAll = message.get_all();
	            ArrayList<WorkflowEngine> engines = this.registry.getAllActiveEngines();
	            ArrayList<WorkflowEngine> actualEngines = new ArrayList<WorkflowEngine>();
	            ArrayList<String> replicatedProcesses = new ArrayList<String>();
	            ArrayList<Integer> roles = new ArrayList<Integer>();
	            for(int i = 0; i < engines.size(); i++) {
	                replicatedProcesses = (engines.get(i)).getActiveProcessInstances();
	                roles = (engines.get(i)).getRoleInActiveProcesses();
	                for(int j = 0; j < replicatedProcesses.size(); j++) {
	                    if (((String)replicatedProcesses.get(j)).equals(message.get_replicated_workflow_id())) {
	                        if (sendToAll.booleanValue()) {
	                            if ((roles.get(j)).intValue() != message.get_engine_number()) {
	                                actualEngines.add(engines.get(i));
	                            }
	                        } else if ((roles.get(j)).intValue() == message.get_next_master()) {
	                            actualEngines.add(engines.get(i));
	                        }
	                    }
	                }
	            }
	            for(int i = 0; i < actualEngines.size(); i++) {
	            	ReplicationStartContext.connection.start();
	                Session session = ReplicationStartContext.connection.createSession(false, 1);
	                Queue destination = session.createQueue("de.unistuttgart.rep." + ((WorkflowEngine)actualEngines.get(i)).getWorkflowEngineIp());
	                MessageProducer producer = session.createProducer((Destination)destination);
	                SynchronizationMessage newMessage = new SynchronizationMessage(message);
	                
	                System.out.println("--- Sending message " + message.get_message() + " for " + message.get_replicated_workflow_id() + " to " + ((WorkflowEngine)actualEngines.get(i)).getWorkflowEngineIp() + " ---");
	                
	                ObjectMessage objectMessage = session.createObjectMessage(newMessage);
	                producer.send(objectMessage);
	                
	                session.close();
	            }
            }
        }*/ 
    	//else if (exchange.getIn().getBody() instanceof String) {
    	if(exchange.getIn().getBody() instanceof String) { 
        	//TODO obsolete
            String message = (String)exchange.getIn().getBody();
            /*if (message.contains("finished")) {
                String workflowEngineIp = new String();
                String replicatedWorkflowID = new String();
                boolean ip = false;
                boolean id = false;
                int i = 0;
                while (i < message.length()) {
                    char c;
                    if (!ip) {
                        c = message.charAt(i);
                        if (c == '*') {
                            ip = true;
                        }
                    } else if (!id) {
                        c = message.charAt(i);
                        if (c == '*') {
                            id = true;
                        } else {
                            workflowEngineIp = String.valueOf(workflowEngineIp) + c;
                        }
                    } else {
                        c = message.charAt(i);
                        replicatedWorkflowID = String.valueOf(replicatedWorkflowID) + c;
                    }
                    ++i;
                }
                this.registry.deleteActiveProcessInstanceRoleAndFinish(workflowEngineIp, replicatedWorkflowID);
                System.out.println("*** Finished: " + replicatedWorkflowID + " at engine " + workflowEngineIp + " ***");
            }*/
            //TODO check if this is necessary
            boolean database_locked = true;
            while(database_locked) {
            	try {
                	this.registry.updateWorkflowEngineTimeOfLastHeartbeat(message);
                	database_locked = false;
            	}
            	catch(Exception e) {
            		e.printStackTrace();
            		if(Constants.DEBUG_LEVEL > 0) {
                		System.out.println("Database locked.");
            		}
                	this.wait(100);
            	}
            }
        }
        //remove the route
        /*if(!route_finished.equals("false")) {
        	System.out.println("Finishing route " + route_finished);
            //TODO remove route           
            System.out.println("d");
            exchange.getContext().stopRoute(route_finished + " Route");
            System.out.println("e");
            exchange.getContext().removeRoute(route_finished + " Route");
            System.out.println("f");
        }*/
    }
}