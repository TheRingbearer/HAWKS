package org.apache.ode.bpel.engine.fc;

import java.util.List;

import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.fcanalyzer.FragmentEntryExitFinder;

public class CleanUpChannelsTask implements Runnable {
	private BpelEngineImpl engine;
	private Long instanceId;
	private OProcess process;

	public CleanUpChannelsTask(OProcess process, BpelEngineImpl engine,
			Long instanceId) {
		this.engine = engine;
		this.process = process;
		this.instanceId = instanceId;
	}

	public void run() {
		FCManagementDAO dao = engine.getFCManagementDAO();
		List<Integer> flows = FragmentEntryExitFinder
				.findNeededContainers(process);
		dao.cleanUpChannels(instanceId, flows);
	}

}
