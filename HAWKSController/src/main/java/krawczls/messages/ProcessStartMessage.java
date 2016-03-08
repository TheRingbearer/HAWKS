package krawczls.messages;

import java.io.Serializable;

public class ProcessStartMessage
implements Serializable {
    static final long serialVersionUID = 887;
    private String replicatedProcessID;
    private String processName;
    private String processServiceName;
    private int replicationGrade;
    private String message;
    private String namespace;

    public void setReplicatedProcessID(String replicatedProcessID) {
        this.replicatedProcessID = replicatedProcessID;
    }

    public String getReplicatedProcessID() {
        return this.replicatedProcessID;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getProcessName() {
        return this.processName;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public void setReplicationGrade(int replicationGrade) {
        this.replicationGrade = replicationGrade;
    }

    public int getReplicationGrade() {
        return this.replicationGrade;
    }

    public void setProcessServiceName(String processServiceName) {
        this.processServiceName = processServiceName;
    }

    public String getProcessServiceName() {
        return this.processServiceName;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return this.namespace;
    }
}