package org.apache.ode.bpel.extensions.listener;

import org.apache.ode.bpel.extensions.handler.DeploymentEventHandler;
import org.apache.ode.bpel.iapi.ProcessStoreEvent;
import org.apache.ode.bpel.iapi.ProcessStoreListener;

//@stmz: listens to store events
public class StoreEventListenerImpl implements ProcessStoreListener {

	DeploymentEventHandler deh = DeploymentEventHandler.getInstance();

	public void onProcessStoreEvent(ProcessStoreEvent event) {
		switch (event.type) {
		case ACTIVATED:
			deh.Set_Process_State(event.pid, event.version, event.state);
			break;
		case RETIRED:
			deh.Set_Process_State(event.pid, event.version, event.state);
			break;
		case DISABLED:
			deh.Set_Process_State(event.pid, event.version, event.state);
			break;
		case UNDEPLOYED:
			deh.Process_Undeployed(event.pid, event.version);
			break;
		case DEPLOYED:
			deh.Process_Deployed(event.bpelFile, event.wsdlFiles, event.pid,
					event.version);
			break;
		case REDEPLOYED:
			deh.Process_Redeployed(event.bpelFile, event.wsdlFiles, event.pid,
					event.version);
			break;
		}
	}

}
