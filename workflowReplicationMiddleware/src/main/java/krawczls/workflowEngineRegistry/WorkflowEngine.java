package krawczls.workflowEngineRegistry;

import java.io.Serializable;
import java.util.ArrayList;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import krawczls.messages.Process;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@Entity
public class WorkflowEngine
implements Serializable {
    static final long serialVersionUID = 789;
    @Id
    private String workflowEngineIp;
    private String workflowEngineQueue;
    private ArrayList<String> activeProcessInstances;
    private ArrayList<Integer> roleInActiveProcesses;
    private ArrayList<Process> processes = new ArrayList();
    private long timeOfLastHeartbeat;

    public String getWorkflowEngineQueue() {
        return this.workflowEngineQueue;
    }

    public void setWorkflowEngineQueue(String workflowEngineQueue) {
        this.workflowEngineQueue = workflowEngineQueue;
    }

    public String getWorkflowEngineIp() {
        return this.workflowEngineIp;
    }

    public void setWorkflowEngineIp(String workflowEngineIp) {
        this.workflowEngineIp = workflowEngineIp;
    }

    @ManyToMany
    @JoinTable(name="BELONGS_TO", joinColumns={@JoinColumn(name="workflowEngineIp", referencedColumnName="workflowEngineIp")}, inverseJoinColumns={@JoinColumn(name="processModelId", referencedColumnName="modelid")})
    public ArrayList<Process> getProcesses() {
        return this.processes;
    }

    public void setProcesses(ArrayList<Process> processes) {
        this.processes = processes;
    }

    public long getTimeOfLastHeartbeat() {
        return this.timeOfLastHeartbeat;
    }

    public void setTimeOfLastHeartbeat(long timeOfHeartbeat) {
        this.timeOfLastHeartbeat = timeOfHeartbeat;
    }

    public void setActiveProcessInstances(ArrayList<String> activeProcessInstances) {
        this.activeProcessInstances = activeProcessInstances;
    }

    public ArrayList<String> getActiveProcessInstances() {
        return this.activeProcessInstances;
    }

    public void setRoleInActiveProcesses(ArrayList<Integer> roleInActiveProcesses) {
        this.roleInActiveProcesses = roleInActiveProcesses;
    }

    public ArrayList<Integer> getRoleInActiveProcesses() {
        return this.roleInActiveProcesses;
    }
}