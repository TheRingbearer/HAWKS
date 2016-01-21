package org.apache.ode.bpel.extensions.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Active;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Deployed;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Disabled;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Retired;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Process_Undeployed;
import org.apache.ode.bpel.extensions.processes.Deployed_Process;
import org.apache.ode.bpel.iapi.ProcessState;

//@stmz: handles deployment events
//stores a list of deployed processes
public class DeploymentEventHandler {

	private static DeploymentEventHandler instance;
	private List DepProc = Collections
			.synchronizedList(new ArrayList<Deployed_Process>());
	public static Logger logger = Logger.getLogger("Log-XML");
	private static Communication comm;
	private static GenericController genCon;

	private DeploymentEventHandler() {
		comm = Communication.getInstance();
		genCon = GenericController.getInstance();
		System.out.println("DeploymentEventHandler instantiated.");
	}

	public static DeploymentEventHandler getInstance() {
		if (instance == null) {
			instance = new DeploymentEventHandler();
		}
		return instance;
	}

	public void addDeployedProcess(Deployed_Process depProcess) {
		synchronized (DepProc) {
			DepProc.add(depProcess);
		}
	}

	public void removeDeployedProcess(QName pName) {
		synchronized (DepProc) {
			ArrayList<Deployed_Process> remove = new ArrayList<Deployed_Process>();
			Iterator<Deployed_Process> itr = DepProc.iterator();
			while (itr.hasNext()) {
				Deployed_Process tmp = itr.next();
				if (tmp.getProcessName().equals(pName)) {
					remove.add(tmp);
				}
			}
			if (remove.size() > 0) {
				DepProc.removeAll(remove);

				logger.fine("Process_Undeployed!?%&$!" + pName);
			}
		}
	}

	// @stmz: one process was deployed
	public void Process_Deployed(File bpel, ArrayList<File> wsdls,
			QName processName, Long version) {
		File bpelFile = bpel;
		ArrayList<File> wsdlFiles = wsdls;

		QName qname = processName;

		Deployed_Process depProcess = new Deployed_Process(bpelFile, wsdlFiles,
				qname, version);
		addDeployedProcess(depProcess);

		logger.fine("Process_Deployed!?%&$!" + processName);

		QName procName = comm.cropQName(processName);

		BlockingManager block = BlockingManager.getInstance();
		block.addBlockingEventsProcessModel(procName, version);

		Process_Deployed message = new Process_Deployed();
		comm.fillProcessEventMessage(message, genCon.getTimestamp(), procName);
		message.setVersion(version);
		message.setBpelFile(bpel);
		message.addWSDLFiles(wsdls);
		comm.sendMessageToTopic(message);
	}

	// @stmz: process was already deployed at startup, add it to list of
	// deployed processes
	public void Process_Redeployed(File bpel, ArrayList<File> wsdls,
			QName processName, Long version) {
		File bpelFile = bpel;
		ArrayList<File> wsdlFiles = wsdls;
		QName qname = processName;

		Deployed_Process depProcess = new Deployed_Process(bpelFile, wsdlFiles,
				qname, version);
		addDeployedProcess(depProcess);

		logger.fine("Process_Redeployed!?%&$!" + processName);

		QName procName = comm.cropQName(processName);

		BlockingManager block = BlockingManager.getInstance();
		block.addBlockingEventsProcessModel(procName, version);

		Process_Deployed message = new Process_Deployed();
		comm.fillProcessEventMessage(message, genCon.getTimestamp(), procName);
		message.setVersion(version);
		message.setBpelFile(bpel);
		message.addWSDLFiles(wsdls);
		comm.sendMessageToTopic(message);
	}

	// @stmz: one process was undeployed
	public void Process_Undeployed(QName pName, Long version) {
		removeDeployedProcess(pName);

		QName procName = comm.cropQName(pName);

		BlockingManager block = BlockingManager.getInstance();
		block.removeBlockingEventsProcessModel(procName, version);

		Process_Undeployed message = new Process_Undeployed();
		comm.fillProcessEventMessage(message, genCon.getTimestamp(), procName);
		message.setVersion(version);
		comm.sendMessageToTopic(message);
	}

	// @stmz: process state changed
	public void Set_Process_State(QName name, Long version, ProcessState state) {
		Deployed_Process stateChange = null;
		synchronized (DepProc) {
			Iterator<Deployed_Process> itr = DepProc.iterator();
			while (itr.hasNext()) {
				Deployed_Process tmp = itr.next();
				if (tmp.getProcessName().equals(name)
						&& tmp.getVersion().equals(version)) {
					tmp.setState(state);
					stateChange = tmp;
				}
			}
		}

		QName procName = comm.cropQName(name);

		if (stateChange != null) {
			if (stateChange.getState().equals("ACTIVE")) {
				logger.fine("Process_Active!?%&$!" + name);

				Process_Active message = new Process_Active();
				comm.fillProcessEventMessage(message, genCon.getTimestamp(),
						procName);
				message.setVersion(version);
				comm.sendMessageToTopic(message);

			} else if (stateChange.getState().equals("RETIRED")) {
				logger.fine("Process_Retired!?%&$!" + name);

				Process_Retired message = new Process_Retired();
				comm.fillProcessEventMessage(message, genCon.getTimestamp(),
						procName);
				message.setVersion(version);
				comm.sendMessageToTopic(message);
			} else if (stateChange.getState().equals("DISABLED")) {
				logger.fine("Process_Disabled!?%&$!" + name);

				Process_Disabled message = new Process_Disabled();
				comm.fillProcessEventMessage(message, genCon.getTimestamp(),
						procName);
				message.setVersion(version);
				comm.sendMessageToTopic(message);
			}

		}
	}

	public List getDepProc() {
		return DepProc;
	}

}
