package krawczls.workflowEngineManagement;

import java.io.Serializable;
import java.util.ArrayList;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import krawczls.workflowEngineRegistry.WorkflowEngine;
import krawczls.workflowEngineRegistry.WorkflowEngineRegistry;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.ode.bpel.extensions.sync.SynchronizationMessage;

public class WorkflowEngineProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();

    public void process(Exchange exchange) throws Exception {
        System.out.println("Workflow Engine Processor started.");
        if (exchange.getIn().getBody() instanceof SynchronizationMessage) {
            SynchronizationMessage message = (SynchronizationMessage)exchange.getIn().getBody();
            System.out.println("Its a synchronization message.");
            System.out.println("With the message content: " + message.get_message());
            Boolean sendToAll = message.get_all();
            ArrayList<WorkflowEngine> engines = this.registry.getAllActiveEngines();
            if (message.get_all()) {
                System.out.println("And we are sending this message to potentially all " + engines.size() + " engines.");
            } else {
                System.out.println("And we are sending this message to one engine.");
            }
            System.out.println("Getting the actual engines");
            ArrayList<WorkflowEngine> actualEngines = new ArrayList<WorkflowEngine>();
            ArrayList<String> replicatedProcesses = new ArrayList<String>();
            ArrayList<Integer> roles = new ArrayList<Integer>();
            int i = 0;
            while (i < engines.size()) {
                replicatedProcesses = ((WorkflowEngine)engines.get(i)).getActiveProcessInstances();
                roles = ((WorkflowEngine)engines.get(i)).getRoleInActiveProcesses();
                System.out.println("The size of the active processes of engine " + ((WorkflowEngine)engines.get(i)).getWorkflowEngineIp() + " is " + replicatedProcesses.size());
                int j = 0;
                while (j < replicatedProcesses.size()) {
                    if (((String)replicatedProcesses.get(j)).equals(message.get_replicated_workflow_id())) {
                        if (sendToAll.booleanValue()) {
                            if (((Integer)roles.get(j)).intValue() != message.get_engine_number()) {
                                actualEngines.add((WorkflowEngine)engines.get(i));
                            }
                        } else if (((Integer)roles.get(j)).intValue() == message.get_next_master()) {
                            actualEngines.add((WorkflowEngine)engines.get(i));
                        }
                    }
                    ++j;
                }
                ++i;
            }
            System.out.println("This is the number of engines we are sending the message to: " + actualEngines.size());
            i = 0;
            while (i < actualEngines.size()) {
                ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
                Connection connection = connection_factory.createConnection();
                connection.start();
                Session session = connection.createSession(false, 1);
                Queue destination = session.createQueue("de.unistuttgart.rep." + ((WorkflowEngine)actualEngines.get(i)).getWorkflowEngineIp());
                MessageProducer producer = session.createProducer((Destination)destination);
                ObjectMessage objectMessage = session.createObjectMessage((Serializable)message);
                producer.send((javax.jms.Message)objectMessage);
                System.out.println("Sending message " + message.get_message() + " to engine " + ((WorkflowEngine)actualEngines.get(i)).getWorkflowEngineIp());
                ++i;
            }
        } else if (exchange.getIn().getBody() instanceof String) {
            System.out.println("It's a string.");
            String message = (String)exchange.getIn().getBody();
            if (message.contains("finished")) {
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
                this.registry.deleteActiveProcessInstanceAndRole(workflowEngineIp, replicatedWorkflowID);
                System.out.println("Finished: " + replicatedWorkflowID + " at engine " + workflowEngineIp);
            } else {
                this.registry.updateWorkflowEngineTimeOfLastHeartbeat(message);
                System.out.println("Heartbeat updated in registry.");
            }
        }
    }
}