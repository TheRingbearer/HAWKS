package krawczls.messages;

import java.io.Serializable;

public class Process
implements Serializable {
    static final long serialVersionUID = 1;
    protected String processName;
    protected String processModelID;
    protected String processFileName;
    protected byte[] processFolderZip;
    protected Boolean deployed;

    public String getProcessName() {
        return this.processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getProcessModelID() {
        return this.processModelID;
    }

    public void setProcessModelID(String value) {
        this.processModelID = value;
    }

    public String getProcessFileName() {
        return this.processFileName;
    }

    public void setProcessFileName(String value) {
        this.processFileName = value;
    }

    public byte[] getProcessFolderZip() {
        return this.processFolderZip;
    }

    public void setProcessFolderZip(byte[] value) {
        this.processFolderZip = value;
    }

    public Boolean isDeployed() {
        return this.deployed;
    }

    public void setDeployed(Boolean value) {
        this.deployed = value;
    }
}