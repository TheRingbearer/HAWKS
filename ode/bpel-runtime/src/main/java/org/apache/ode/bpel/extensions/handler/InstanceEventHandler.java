package org.apache.ode.bpel.extensions.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.engine.iteration.ChannelRegistry;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Completed;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Faulted;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Iteration_Prepared;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_JumpTo_Prepared;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Reexecution_Prepared;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Running;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Suspended;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Instance_Terminated;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Instantiated;
import org.apache.ode.bpel.extensions.processes.Active_Process;

//@stmz: handles events for process instances
//stores a list of running instances
public class InstanceEventHandler {
	private static InstanceEventHandler instance;
	private List ActProc = Collections
			.synchronizedList(new ArrayList<Active_Process>());
	public static Logger logger = Logger.getLogger("Log-XML");
	private static ActivityEventHandler aeh;
	private static Communication comm;
	private static GenericController genCon;

	private InstanceEventHandler() {
		comm = Communication.getInstance();
		aeh = ActivityEventHandler.getInstance();
		genCon = GenericController.getInstance();
		System.out.println("InstanceEventHandler instantiated.");
	}

	public static InstanceEventHandler getInstance() {
		if (instance == null) {
			instance = new InstanceEventHandler();
		}
		return instance;
	}

	public void addActiveProcess(Active_Process actProc) {
		synchronized (ActProc) {
			ActProc.add(actProc);
		}
	}

	public void removeActiveProcess(QName name, Long ID) {
		synchronized (ActProc) {
			Active_Process remove = null;
			Iterator<Active_Process> itr = ActProc.iterator();
			while (itr.hasNext()) {
				Active_Process tmp = itr.next();
				if (tmp.getID().equals(ID) && tmp.getName().equals(name)) {
					remove = tmp;
				}
			}
			if (remove != null) {
				ActProc.remove(remove);
			}
		}
	}

	public void Process_Instantiated(QName name, Long ID, Long version) {
		Active_Process act = new Active_Process(name, ID, version);
		addActiveProcess(act);
		
		// @hahnml: Reset the ProcessModelChangeRegistry if a new instance is started
		ProcessModelChangeRegistry.getRegistry().clearAll(ID);

		logger.fine("Process_Instantiated!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		BlockingManager block = BlockingManager.getInstance();
		block.addBlockingEventsInstance(procName, ID, version);

		Process_Instantiated message = new Process_Instantiated();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		message.setVersion(version);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Running(QName name, Long ID) {
		logger.fine("Instance_Running!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_Running message = new Instance_Running();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Resumed(QName name, Long ID) {
		logger.fine("Instance_Running!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_Running message = new Instance_Running();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Suspended(QName name, Long ID) {
		logger.fine("Instance_Suspended!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_Suspended message = new Instance_Suspended();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		comm.sendMessageToTopic(message);
	}

	// @hahnml: New method to propagate iterations in instances
	public void Instance_Iteration_Prepared(QName name, Long ID, String xPath) {
		logger.fine("Instance_Iteration_Prepared!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_Iteration_Prepared message = new Instance_Iteration_Prepared();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		message.setDetails(xPath);
		comm.sendMessageToTopic(message);
	}

	// @hahnml: New method to propagate reexecution of activities in instances
	public void Instance_Reexecution_Prepared(QName name, Long ID, String xPath) {
		logger.fine("Instance_Reexecuted!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_Reexecution_Prepared message = new Instance_Reexecution_Prepared();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		message.setDetails(xPath);
		comm.sendMessageToTopic(message);
	}

	// @hahnml: New method to propagate jumpTo in instances
	public void Instance_JumpTo_Prepared(QName name, Long ID, String xPath) {
		logger.fine("Instance_Iteration_Prepared!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		Instance_JumpTo_Prepared message = new Instance_JumpTo_Prepared();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		message.setDetails(xPath);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Terminated(QName name, Long ID) {
		removeActiveProcess(name, ID);
		aeh.removeCompensationHandlers(ID);

		aeh.removeRunningScopes(ID);
		aeh.removeRunningActivities(ID);
		
		//@hahnml: Remove all buffered Activity_Status
		aeh.removeActivityStatus(ID);
		
		//@hahnml: Remove all registered channels
		ChannelRegistry.getRegistry().removeInstanceFromRegistry(ID);

		logger.fine("Instance_Terminated!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		BlockingManager block = BlockingManager.getInstance();
		block.removeBlockingEventsInstance(procName, ID);

		Instance_Terminated message = new Instance_Terminated();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Faulted(QName name, Long ID, QName fault_name,
			QName element_type, QName message_type, String fault_msg) {
		removeActiveProcess(name, ID);

		aeh.removeCompensationHandlers(ID);
		
		//@hahnml: Remove all buffered Activity_Status
		aeh.removeActivityStatus(ID);
		
		//@hahnml: Remove all registered channels
		ChannelRegistry.getRegistry().removeInstanceFromRegistry(ID);

		logger.fine("Instance_Faulted!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		BlockingManager block = BlockingManager.getInstance();
		block.removeBlockingEventsInstance(procName, ID);

		Instance_Faulted message = new Instance_Faulted();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		message.setFaultName(fault_name);
		message.setElementType(element_type);
		message.setMessageType(message_type);
		message.setFaultMsg(fault_msg);
		comm.sendMessageToTopic(message);
	}

	public void Instance_Completed(QName name, Long ID) {
		removeActiveProcess(name, ID);

		aeh.removeCompensationHandlers(ID);
		
		//@hahnml: Remove all buffered Activity_Status
		aeh.removeActivityStatus(ID);
		
		//@hahnml: Remove all registered channels
		ChannelRegistry.getRegistry().removeInstanceFromRegistry(ID);

		logger.fine("Instance_Completed!?%&$!" + name + "!?%&$!" + ID);

		QName procName = comm.cropQName(name);

		BlockingManager block = BlockingManager.getInstance();
		block.removeBlockingEventsInstance(procName, ID);

		Instance_Completed message = new Instance_Completed();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				ID);
		comm.sendMessageToTopic(message);
	}

	public Long getVersion(Long processID) {
		Long version = 0L;
		synchronized (ActProc) {
			Iterator<Active_Process> iter = ActProc.iterator();
			while (iter.hasNext()) {
				Active_Process tmp = iter.next();
				if (tmp.getID().equals(processID)) {
					version = tmp.getVersion();
					break;
				}
			}
		}
		return version;
	}

	public QName getQName(Long processID) {
		QName name = null;
		synchronized (ActProc) {
			Iterator<Active_Process> iter = ActProc.iterator();
			while (iter.hasNext()) {
				Active_Process tmp = iter.next();
				if (tmp.getID().equals(processID)) {
					name = tmp.getName();
					break;
				}
			}
		}
		return name;
	}

	public List getActProc() {
		return ActProc;
	}
}
