package krawczls.deploymentManagement;

import java.util.ArrayList;

import krawczls.deploymentManagement.ReplicationDeploymentManager;
import krawczls.executionEngineRegistry.WorkflowEngine;
import krawczls.executionEngineRegistry.WorkflowEngineRegistry;
import krawczls.messages.ProcessDeploymentMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import constants.Constants;

public class ReplicationDeploymentProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    ReplicationDeploymentManager deployer = new ReplicationDeploymentManager();

    public void process(Exchange exchange) throws Exception {
    	if(Constants.DEBUG_LEVEL > 0) {
        	System.out.println("DeployingStart");
    	}
        ProcessDeploymentMessage message = (ProcessDeploymentMessage)exchange.getIn().getBody();
        ArrayList<WorkflowEngine> engines = this.registry.getAllActiveEngines();
        if (engines.size() == 0) {
        	if(Constants.DEBUG_LEVEL > 0) {
            	System.out.println("There are no engines in the registry.");
        	}
        } else {
            String processName = message.getProcess().getProcessName();
            int i = 0;
            while (i < engines.size()) {
                String workflowEngineIp = ((WorkflowEngine)engines.get(i)).getWorkflowEngineIp();
                if (this.registry.checkIfDeployed(workflowEngineIp, processName) == 0) {
                	if(message.isDeploy()) {
                		if(Constants.DEBUG_LEVEL > 0) {
                			System.out.println("Trying to deploy.");
                		}
                		try {;
                			this.deployer.setUp(message.getProcess().getProcessName(), message.getProcess().getProcessFileName(), workflowEngineIp);
                		}
                		catch(Exception e) {
                			if(Constants.DEBUG_LEVEL > 0) {
                				e.printStackTrace();
                			}
                		}
                	}
                    this.registry.addNewProcess(workflowEngineIp, message.getProcess());
                }
                ++i;
            }
            if(Constants.DEBUG_LEVEL > 0) {
            	System.out.println("DeployingEnd");
            }
        }
    }
}