package krawczls.eventManagement;

import krawczls.eventManagement.ReplicationEventManager;
import org.apache.camel.Body;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.InstanceEventMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.ProcessEventMessage;

public class ReplicationEventMessageConsumer {
    public void initReplicationEventMessageConsumer() {
        ReplicationEventManager.getInstance().startup();
    }

    public void onMessage(@Body Object obj) {
        if (obj instanceof InstanceEventMessage) {
            InstanceEventMessage message = (InstanceEventMessage)obj;
            ReplicationEventManager.getInstance().handleEventMessage(message);
        } else if (obj instanceof ProcessEventMessage) {
            ProcessEventMessage message = (ProcessEventMessage)obj;
            ReplicationEventManager.getInstance().handleEventMessage(message);
        }
    }
}