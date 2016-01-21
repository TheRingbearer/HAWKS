package krawczls.messages;

import java.io.Serializable;

public class DeleteEngineMessage
implements Serializable {
    static final long serialVersionUID = 143;
    private String workflowEngineIp;

    public void setWorkflowEngineIp(String workflowEngineIp) {
        this.workflowEngineIp = workflowEngineIp;
    }

    public String getWorkflowEngineIp() {
        return this.workflowEngineIp;
    }
}