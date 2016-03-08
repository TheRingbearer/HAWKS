package krawczls.executionEngineRegistry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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
public class WorkflowEngine implements Serializable {
    static final long serialVersionUID = 789;
    @Id
    private String workflowEngineIp;

    // Process instances & necessary information
    private HashSet<String> activeProcessIDs = new HashSet<String>();
    private HashMap<String,Integer> processIDToRole = new HashMap<String,Integer>();
    private HashSet<String> processIDTOFinishedProcesses = new HashSet<String>();
    
    // Process models
    private ArrayList<Process> deployedProcessModels = new ArrayList<Process>();
    
    // Time of the last heartbeat
    private long timeOfLastHeartbeat;
    
    //private long timeOfFirstHeartbeat;

    

	public void addProcessInstance(String replicatedWorkflowID, int role) {
		activeProcessIDs.add(replicatedWorkflowID);
		processIDToRole.put(replicatedWorkflowID, role);
	}

	public void updateFinishProcess(String replicatedWorkflowID) {
		processIDTOFinishedProcesses.add(replicatedWorkflowID);
	}
	
    public void finishProcess(String replicatedWorkflowID) {
    	processIDTOFinishedProcesses.add(replicatedWorkflowID);
    	activeProcessIDs.remove(replicatedWorkflowID);
    	processIDToRole.remove(replicatedWorkflowID);
    }
    
    public boolean isProcessFinished(String replicatedWorkflowID) {
    	return processIDTOFinishedProcesses.contains(replicatedWorkflowID);
    }
	
    public String getWorkflowEngineIp() {
        return this.workflowEngineIp;
    }

    public void setWorkflowEngineIp(String workflowEngineIp) {
        this.workflowEngineIp = workflowEngineIp;
    }
    
    public int numberOfActiveProcesses() {
    	return activeProcessIDs.size();
    }
    
    public boolean isCurrentlyExecutingWorkflow(String replicatedWorkflowID) {
    	return activeProcessIDs.contains(replicatedWorkflowID);
    }
    
    public int getRoleForWorkflow(String replicatedWorkflowID) {
        return processIDToRole.get(replicatedWorkflowID);
    }

    @ManyToMany
    @JoinTable(name="BELONGS_TO", joinColumns={@JoinColumn(name="workflowEngineIp", referencedColumnName="workflowEngineIp")}, inverseJoinColumns={@JoinColumn(name="processModelId", referencedColumnName="modelid")})
    public ArrayList<Process> getProcesses() {
        return this.deployedProcessModels;
    }

    public void setProcesses(ArrayList<Process> processes) {
        this.deployedProcessModels = processes;
    }

    public long getTimeOfLastHeartbeat() {
        return this.timeOfLastHeartbeat;
    }

    public void setTimeOfLastHeartbeat(long timeOfHeartbeat) {
        this.timeOfLastHeartbeat = timeOfHeartbeat;
    }

	/*public long getTimeOfFirstHeartbeat() {
		return this.timeOfFirstHeartbeat;
	}*/
    
	/*public void setTimeOfFirstHeartbeat(long timeOfFirstHeartbeat) {
		this.timeOfFirstHeartbeat = timeOfFirstHeartbeat;
	}*/

//    public void setRoleInActiveProcesses(ArrayList<Integer> roleInActiveProcesses) {
//        this.processIDToRole = roleInActiveProcesses;
//    }
//
//    public ArrayList<Integer> getRoleInActiveProcesses() {
//        return this.processIDToRole;
//    }

//	public void setFinishedProcesses(ArrayList<Boolean> finishedProcesses) {
//		this.processIDTOFinishedProcesses = finishedProcesses;
//	}
//
//	public ArrayList<Boolean> getFinishedProcesses() {
//		return processIDTOFinishedProcesses;
//	}

}