package krawczls.eventManagement;

import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ProcessEventMessage;

public class ReplicationEventManager {
    private static ReplicationEventManager _instance = null;

    public static ReplicationEventManager getInstance() {
        if (_instance == null) {
            _instance = new ReplicationEventManager();
        }
        return _instance;
    }

    public void handleEventMessage(InstanceEventMessage message) {
    }

    public void handleEventMessage(ProcessEventMessage message) {
    }

    public void startup() {
    }

    public void shutdown() {
    }
}