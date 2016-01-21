package krawczls.deploymentManagement;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import krawczls.deploymentManagement.ReplicationDeploymentManager;
import krawczls.messages.ProcessStartMessage;
import krawczls.workflowEngineRegistry.WorkflowEngine;
import krawczls.workflowEngineRegistry.WorkflowEngineRegistry;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ReplicationStartProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    ReplicationDeploymentManager deployer = new ReplicationDeploymentManager();

    private String generateReplicatedWorkflowUniqueID() {
        return UUID.randomUUID().toString();
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println("Starting");
        ProcessStartMessage message = (ProcessStartMessage)exchange.getIn().getBody();
        ArrayList engines = this.registry.getAllActiveEngines();
        ArrayList<String> actualEngines = new ArrayList<String>();
        String processName = message.getProcessName();
        int i = 0;
        while (i < engines.size()) {
            String workflowEngineIp = ((WorkflowEngine)engines.get(i)).getWorkflowEngineIp();
            System.out.println("Ip: " + workflowEngineIp + ";");
            System.out.println(this.registry.checkIfDeployed(workflowEngineIp, processName));
            if (this.registry.checkIfDeployed(workflowEngineIp, processName) == 1 && message.getReplicationGrade() > actualEngines.size()) {
                actualEngines.add(workflowEngineIp);
            }
            ++i;
        }
        if (message.getReplicationGrade() == actualEngines.size()) {
            String replicatedWorkflowID = this.generateReplicatedWorkflowUniqueID();
            System.out.println("Starting replicated Workflow " + replicatedWorkflowID);
            int i2 = 0;
            while (i2 < actualEngines.size()) {
                System.out.println((String)actualEngines.get(i2));
                this.registry.addNewActiveProcessInstance((String)actualEngines.get(i2), replicatedWorkflowID);
                this.registry.addNewRole((String)actualEngines.get(i2), i2 + 1);
                ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
                Connection connection = connection_factory.createConnection();
                connection.start();
                Session session = connection.createSession(false, 1);
                Queue destination = session.createQueue("de.unistuttgart.rep." + (String)actualEngines.get(i2));
                MessageProducer producer = session.createProducer((Destination)destination);
                ArrayList<Object> listMessage = new ArrayList<Object>();
                listMessage.add(String.valueOf(message.getNamespace()) + message.getProcessName());
                listMessage.add(replicatedWorkflowID);
                listMessage.add(actualEngines.size());
                listMessage.add(i2 + 1);
                if (i2 == 0) {
                    listMessage.add("true");
                } else {
                    listMessage.add("false");
                }
                ObjectMessage objectMessage = session.createObjectMessage(listMessage);
                producer.send((javax.jms.Message)objectMessage);
                this.deployer.sendSOAPToService(message.getMessage(), message.getProcessServiceName(), (String)actualEngines.get(i2));
                System.out.println("Sending start message to ODE.");
                ++i2;
            }
        } else {
            System.out.println("Found less than " + message.getReplicationGrade() + " Engines.");
        }
        System.out.println("Starting");
    }
}