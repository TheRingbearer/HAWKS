package org.apache.ode.bpel.extensions.comm.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.jms.Destination;
import javax.xml.namespace.QName;

import org.apache.ode.bpel.engine.DebuggerSupport;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.TestConnection;
import org.apache.ode.bpel.extensions.sync.Constants;
import org.apache.ode.bpel.o.OExpression;
import org.w3c.dom.Node;

//@stmz: handles everything that has to do with blocking and unblocking of events
public class BlockingManager {

	private static BlockingManager instance;

	private List blockingEvents;

	// @stmz: what event types are blocked on a global basis
	private Blocking_Events globalBlockingEvents;

	// @stmz: what event types are blocked on a process model basis
	private List processModelBlockingEvents;

	// @stmz: what event types are blocked on a process instance basis
	private List instanceBlockingEvents;

	private List registeredController;

	private Map<QName, Boolean> processes;

	private Map<QName, DebuggerSupport> debuggers;

	private BlockingManager() {
		blockingEvents = Collections
				.synchronizedList(new LinkedList<BlockingEvent>());
		globalBlockingEvents = new Blocking_Events();
		processModelBlockingEvents = Collections
				.synchronizedList(new LinkedList<BlockingEventsProcessModel>());
		instanceBlockingEvents = Collections
				.synchronizedList(new LinkedList<BlockingEventsInstance>());
		registeredController = Collections
				.synchronizedList(new LinkedList<regController>());
		processes = Collections.synchronizedMap(new HashMap<QName, Boolean>());
		debuggers = Collections
				.synchronizedMap(new HashMap<QName, DebuggerSupport>());
		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("BlockingManager instantiated.");
		}
	}

	public static BlockingManager getInstance() {
		if (instance == null) {
			instance = new BlockingManager();
		}
		return instance;
	}

	// @hahnml: Create a Blocking_Events object from outside
	public Blocking_Events createBlockingEvent() {
		return new Blocking_Events();
	}

	public void addBlockingEvent(BlockingEvent evt) {
		synchronized (blockingEvents) {
			blockingEvents.add(evt);
		}
	}

	public void removeBlockingEvent(String path, Long scopeid, Long prcID) {
		synchronized (blockingEvents) {
			BlockingEvent tmp = null;
			Iterator<BlockingEvent> itr = blockingEvents.iterator();
			while (itr.hasNext()) {
				BlockingEvent act_event = itr.next();
				if (act_event.getIsActivityEvent()
						&& act_event.getXpath() != null) {
					if (path.equals(act_event.getXpath())
							&& scopeid.equals(act_event.getScopeID())
							&& prcID.equals(act_event.getProcessID())) {
						tmp = act_event;
						break;
					}
				}
			}
			if (tmp != null) {
				blockingEvents.remove(tmp);
			}
		}
	}

	public void addBlockingEventsProcessModel(QName processName, Long version) {
		BlockingEventsProcessModel tmp = new BlockingEventsProcessModel();
		tmp.processName = processName;
		tmp.version = version;
		Blocking_Events tmp_blck = new Blocking_Events();
		fillBlockingEvents(tmp_blck, globalBlockingEvents);
		tmp.blockEvents = tmp_blck;

		synchronized (processModelBlockingEvents) {
			processModelBlockingEvents.add(tmp);
		}
	}

	public void removeBlockingEventsProcessModel(QName prName, Long vers) {
		BlockingEventsProcessModel tmp = null;
		synchronized (processModelBlockingEvents) {
			Iterator<BlockingEventsProcessModel> itr = processModelBlockingEvents
					.iterator();
			while (itr.hasNext()) {
				BlockingEventsProcessModel tmp2 = itr.next();
				if (tmp2.processName.equals(prName)
						&& tmp2.version.equals(vers)) {
					tmp = tmp2;
				}
			}

			if (tmp != null) {
				processModelBlockingEvents.remove(tmp);
			}
		}
	}

	public void addBlockingEventsInstance(QName pr_name, Long pr_id, Long vers) {
		BlockingEventsInstance tmp = new BlockingEventsInstance();
		tmp.ID = pr_id;
		tmp.processName = pr_name;
		tmp.version = vers;
		Blocking_Events tmp_blck = new Blocking_Events();

		BlockingEventsProcessModel temp = null;

		synchronized (processModelBlockingEvents) {
			Iterator<BlockingEventsProcessModel> itr = processModelBlockingEvents
					.iterator();
			while (itr.hasNext()) {
				BlockingEventsProcessModel tmp2 = itr.next();
				if (tmp2.processName.equals(pr_name)
						&& tmp2.version.equals(vers)) {
					temp = tmp2;
				}
			}

			fillBlockingEvents(tmp_blck, temp.blockEvents);
			tmp.blockEvents = tmp_blck;

			synchronized (instanceBlockingEvents) {
				instanceBlockingEvents.add(tmp);
			}
		}
	}

	public void removeBlockingEventsInstance(QName pr_name, Long pr_id) {
		synchronized (instanceBlockingEvents) {
			BlockingEventsInstance tmp = null;

			Iterator<BlockingEventsInstance> itr = instanceBlockingEvents
					.iterator();
			while (itr.hasNext()) {
				BlockingEventsInstance tmp2 = itr.next();
				if (tmp2.processName.equals(pr_name) && tmp2.ID.equals(pr_id)) {
					tmp = tmp2;
				}
			}

			if (tmp != null) {
				instanceBlockingEvents.remove(tmp);
			}
		}
	}

	public void fillBlockingEvents(Blocking_Events new_instance,
			Blocking_Events old) {
		if (old != null) {
			new_instance.Activity_Ready = old.Activity_Ready;
			new_instance.Activity_Executed = old.Activity_Executed;
			new_instance.Activity_Faulted = old.Activity_Faulted;
			new_instance.Evaluating_TransitionCondition_Faulted = old.Evaluating_TransitionCondition_Faulted;
			new_instance.Scope_Compensating = old.Scope_Compensating;
			new_instance.Scope_Handling_Termination = old.Scope_Handling_Termination;
			new_instance.Scope_Complete_With_Fault = old.Scope_Complete_With_Fault;
			new_instance.Scope_Handling_Fault = old.Scope_Handling_Fault;
			new_instance.Loop_Condition_False = old.Loop_Condition_False;
			new_instance.Loop_Condition_True = old.Loop_Condition_True;
			new_instance.Loop_Iteration_Complete = old.Loop_Iteration_Complete;
			new_instance.Link_Evaluated = old.Link_Evaluated;
		}
	}

	// @stmz: represents a registered controller.
	// check every 100s, if this controller is still available
	public class regController {
		public class Task extends TimerTask {
			public void run() {
				Communication comm = Communication.getInstance();
				GenericController genCon = GenericController.getInstance();
				TestConnection message = new TestConnection();
				comm.fillMessageBase(message, genCon.getTimestamp());
				comm.sendMessageToDestination(dest, message);
			}

		}

		public Destination dest;
		public RegisterRequestMessage message;
		public Timer timer;

		public regController() {
			timer = new Timer();
			timer.schedule(new Task(), 100000, 100000);
		}
	}

	public void addNewRegController(Destination dest, RegisterRequestMessage msg) {
		regController tmp = new regController();
		tmp.dest = dest;
		tmp.message = msg;
		synchronized (registeredController) {
			registeredController.add(tmp);
		}
	}

	public regController getRegController(Destination dest) {
		regController tmp = null;
		synchronized (registeredController) {
			Iterator<regController> iter = registeredController.iterator();
			while (iter.hasNext()) {
				regController temp = iter.next();
				if (temp.dest.equals(dest)) {
					tmp = temp;
					break;
				}
			}
			if (tmp != null) {
				registeredController.remove(tmp);
			}
		}
		return tmp;
	}

	public List getBlockingEvents() {
		return blockingEvents;
	}

	public Blocking_Events getGlobalBlockingEvents() {
		return globalBlockingEvents;
	}

	public List getProcessModelBlockingEvents() {
		return processModelBlockingEvents;
	}

	public List getInstanceBlockingEvents() {
		return instanceBlockingEvents;
	}

	public List getRegisteredController() {
		return registeredController;
	}

	public Map<QName, Boolean> getProcesses() {
		return processes;
	}

	public Map<QName, DebuggerSupport> getDebuggers() {
		return debuggers;
	}

	public class Blocking_Events {
		public Boolean Activity_Ready;
		public Boolean Activity_Executed;
		public Boolean Activity_Faulted;
		public Boolean Evaluating_TransitionCondition_Faulted;
		public Boolean Scope_Compensating;
		public Boolean Scope_Handling_Termination;
		public Boolean Scope_Complete_With_Fault;
		public Boolean Scope_Handling_Fault;
		public Boolean Loop_Condition_True;
		public Boolean Loop_Condition_False;
		public Boolean Loop_Iteration_Complete;
		public Boolean Link_Evaluated;
		
		public Blocking_Events() {
			Activity_Ready = false;
			Activity_Executed = false;
			Activity_Faulted = false;
			Evaluating_TransitionCondition_Faulted = false;
			Scope_Compensating = false;
			Scope_Handling_Termination = false;
			Scope_Complete_With_Fault = false;
			Scope_Handling_Fault = false;
			Loop_Condition_True = false;
			Loop_Condition_False = false;
			Loop_Iteration_Complete = false;
			Link_Evaluated = false;
		}
	}

	public class BlockingEventsInstance {
		private QName processName;
		private Long version;
		private Long ID;
		private Blocking_Events blockEvents;

		// @hahnml: Set of activity <-> blocking event mappings
		private HashMap<String, Blocking_Events> activityBlockEvents = new HashMap<String, Blocking_Events>();
		// @hahnml: Set of activity <-> condition mappings
		private HashMap<String, OExpression> activityEventConditions = new HashMap<String, OExpression>();

		public QName getProcessName() {
			return processName;
		}

		public Long getVersion() {
			return version;
		}

		public Long getID() {
			return ID;
		}

		public Blocking_Events getBlockEvents() {
			return blockEvents;
		}

		public void setProcessName(QName processName) {
			this.processName = processName;
		}

		public void setVersion(Long version) {
			this.version = version;
		}

		public void setID(Long id) {
			ID = id;
		}

		public void setBlockEvents(Blocking_Events blockEvents) {
			this.blockEvents = blockEvents;
		}

		public HashMap<String, Blocking_Events> getActivityBlockEvents() {
			return this.activityBlockEvents;
		}

		public void setActivityBlockEvents(
				HashMap<String, Blocking_Events> activityEvents) {
			this.activityBlockEvents = activityEvents;
		}
		
		public HashMap<String, OExpression> getActivityEventConditions() {
			return activityEventConditions;
		}

		public void setActivityEventConditions(
				HashMap<String, OExpression> activityEventConditions) {
			this.activityEventConditions = activityEventConditions;
		}
	}

	public class BlockingEventsProcessModel {
		private QName processName;
		private Long version;
		private Blocking_Events blockEvents;

		public QName getProcessName() {
			return processName;
		}

		public Long getVersion() {
			return version;
		}

		public Blocking_Events getBlockEvents() {
			return blockEvents;
		}

		public void setProcessName(QName processName) {
			this.processName = processName;
		}

		public void setVersion(Long version) {
			this.version = version;
		}

		public void setBlockEvents(Blocking_Events blockEvents) {
			this.blockEvents = blockEvents;
		}
	}

}
