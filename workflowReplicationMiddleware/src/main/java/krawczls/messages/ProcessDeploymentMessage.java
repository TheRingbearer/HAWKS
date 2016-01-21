package krawczls.messages;

import java.io.Serializable;
import java.util.ArrayList;
import krawczls.messages.Process;
import krawczls.workflowEngineRegistry.WorkflowEngine;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class ProcessDeploymentMessage
implements Serializable {
    static final long serialVersionUID = 123;
    private Process process;
    private ArrayList<WorkflowEngine> workflowEngines;

    public Process getProcess() {
        return this.process;
    }

    public ArrayList<WorkflowEngine> getWorkflowEngines() {
        return this.workflowEngines;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public void setWorkflowEngines(ArrayList<WorkflowEngine> workflowEngines) {
        this.workflowEngines = workflowEngines;
    }
}