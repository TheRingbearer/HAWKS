package org.apache.ode.bpel.extensions;

import java.util.Iterator;

import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.regController;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.extensions.handler.DeploymentEventHandler;
import org.apache.ode.bpel.extensions.handler.IncomingMessageHandler;
import org.apache.ode.bpel.extensions.handler.InstanceEventHandler;
import org.apache.ode.bpel.extensions.listener.StoreEventListenerImpl;
import org.apache.ode.bpel.extensions.log.Logging;
import org.apache.ode.bpel.extensions.sync.Constants;
import org.apache.ode.store.ProcessStoreImpl;
import org.apache.ode.utils.ZZBool;

//@stmz: center of the extension. invokes all the oder needed objects
public class GenericController {
	private static GenericController instance;
	private static Communication comm;
	private static DeploymentEventHandler deploymentEventHandler;
	private static InstanceEventHandler instanceEventHandler;
	private static ActivityEventHandler activityEventHandler;
	private static BlockingManager block;
	private static Logging log;
	private static IncomingMessageHandler incMsg;
	private static ZZBool zzbool;
	private Long timestamp;
	private long waitingTime = 0L;
	
	// @hahnml
	private String _activeMQURL = "tcp://localhost:61616";
	
	private GenericController() {
		// timestamp is ID of the GenericController
		timestamp = new Long(System.currentTimeMillis());

		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("Generic Controller instantiated.");
		}
	}

	public static GenericController getInstance() {
		if (instance == null) {
			instance = new GenericController();
		}
		return instance;
	}

	public void start(ProcessStoreImpl store) {
		log = new Logging();
		comm = Communication.getInstance();
		
		comm.startup(_activeMQURL);
		
		block = BlockingManager.getInstance();
		deploymentEventHandler = DeploymentEventHandler.getInstance();
		instanceEventHandler = InstanceEventHandler.getInstance();
		activityEventHandler = ActivityEventHandler.getInstance();
		incMsg = IncomingMessageHandler.getInstance();
		zzbool = ZZBool.getInstance();

		// register StoreEventListener
		store.registerListener(new StoreEventListenerImpl());

		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("Generic Controller initialized.");
		}
	}

	public void shutdown() {
		comm.shutdown();
		log.endLogging();
		synchronized (block.getRegisteredController()) {
			Iterator<regController> iter = block.getRegisteredController()
					.iterator();
			while (iter.hasNext()) {
				regController tmp = iter.next();
				tmp.timer.cancel();
			}
		}
	}

	public Long getTimestamp() {
		return timestamp;
	}

	// @hahnml
	public void setWaitingTime(long waitingTime) {
		this.waitingTime = waitingTime;
	}

	public long getWaitingTime() {
		return this.waitingTime;
	}

	// @hahnml: Sets the ActiveMQ URL to the value specified in the properties
	public void setActiveMQURL(String activeMQURL) {
		_activeMQURL = activeMQURL;
	}
	
	public String getActiveMQURL() {
		return _activeMQURL;
	}
}
