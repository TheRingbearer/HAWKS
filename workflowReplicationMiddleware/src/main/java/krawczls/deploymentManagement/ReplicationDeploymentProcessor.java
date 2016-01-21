package krawczls.deploymentManagement;

import java.util.ArrayList;
import krawczls.deploymentManagement.ReplicationDeploymentManager;
import krawczls.messages.ProcessDeploymentMessage;
import krawczls.workflowEngineRegistry.WorkflowEngine;
import krawczls.workflowEngineRegistry.WorkflowEngineRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class ReplicationDeploymentProcessor
implements Processor {
    WorkflowEngineRegistry registry = new WorkflowEngineRegistry();
    ReplicationDeploymentManager deployer = new ReplicationDeploymentManager();

    public void process(Exchange exchange) throws Exception {
        System.out.println("DeployingStart");
        ProcessDeploymentMessage message = (ProcessDeploymentMessage)exchange.getIn().getBody();
        ArrayList<WorkflowEngine> engines = this.registry.getAllActiveEngines();
        if (engines.size() == 0) {
            System.out.println("There are no engines in the registry.");
        } else {
            String processName = message.getProcess().getProcessName();
            int i = 0;
            while (i < engines.size()) {
                String workflowEngineIp = ((WorkflowEngine)engines.get(i)).getWorkflowEngineIp();
                if (this.registry.checkIfDeployed(workflowEngineIp, processName) == 0) {
                    this.deployer.setUp(message.getProcess().getProcessName(), message.getProcess().getProcessFileName(), workflowEngineIp);
                    this.registry.addNewProcess(workflowEngineIp, message.getProcess());
                }
                ++i;
            }
            System.out.println("DeployingEnd");
        }
    }
}