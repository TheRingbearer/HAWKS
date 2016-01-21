package org.apache.ode.bpel.engine.iteration;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.ode.bpel.dao.CorrelatorDAO;
import org.apache.ode.bpel.dao.ProcessDAO;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.extensions.processes.Running_Activity;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.runtime.ACTIVITY.Key;
import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.PICK;
import org.apache.ode.bpel.runtime.channels.PickResponseChannel;
import org.apache.ode.jacob.IndexedObject;
import org.apache.ode.jacob.vpu.ExecutionQueueImpl;

/**
 * This class is used to cancel the PickResponseChannels of any RUNNING
 * PickReceive activities at the IMAManager and to delete their corresponding
 * MessageRouteDAOs. The real IterationRunnable (SEQUENCE|SCOPE) is injected
 * directly after the corrections.
 * 
 * @author hahnml
 * 
 */
public class IterationContainerRunnable extends BpelJacobRunnable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private BpelJacobRunnable _iterationRunnable = null;

	public IterationContainerRunnable(BpelJacobRunnable iterationActivity) {
		_iterationRunnable = iterationActivity;
	}

	@Override
	public void run() {
		// Remember all running PickReceive activities
		Set<OActivity> pickReceives = new HashSet<OActivity>();

		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();

		List<Running_Activity> activities = evtHandler.getRunningActivities();

		// Loop through all running activities
		for (Running_Activity run_Act : activities) {
			// Check if the activity belongs to our process instance
			if (run_Act.getProcessID().equals(getBpelRuntimeContext().getPid())) {
				// Get the activity over its xpath
				OActivity act = ReexecutionHandler.getActivity(run_Act
						.getXPath(), getBpelRuntimeContext().getBpelProcess()
						.getOProcess());

				if (act != null) {
					if (act instanceof OPickReceive) {
						pickReceives.add(act);
					}
				}
			}
		}

		if (!pickReceives.isEmpty()) {
			ExecutionQueueImpl soup = (ExecutionQueueImpl) getBpelRuntimeContext()
					.getVPU()._executionQueue;
			/*
			 * Check the IMAManager for conflicting PickResponseChannels:
			 */
			// Query the PickResponseChannels of all PICK's over the
			// execution queue
			// index list
			Set<PickResponseChannel> pickResponseChannels = new HashSet<PickResponseChannel>();
			if (!soup.getIndex().keySet().isEmpty()) {
				LinkedList<IndexedObject> list = null;

				Iterator<Object> keys = soup.getIndex().keySet().iterator();
				while (keys.hasNext()) {
					Key key = (Key) keys.next();
					if (key.getType().getClass() == OPickReceive.class) {
						if (pickReceives.contains(key.getType())) {
							list = soup.getIndex().get(key);
						} else {
							list = null;
						}
					}

					if (list != null) {
						Iterator<IndexedObject> iter = list.iterator();

						while (iter.hasNext()) {
							IndexedObject obj = iter.next();
							if (obj instanceof PICK) {
								pickResponseChannels.add(((PICK) obj)
										.getPickResponseChannel());
							}
						}
					}
				}
			}

			for (PickResponseChannel channel : pickResponseChannels) {
				String channelStr = channel.export();
				// Remove the PickResponseChannel from the
				// IMAManager, if it is registered
				if (getBpelRuntimeContext().isIMAChannelRegistered(channelStr)) {
					getBpelRuntimeContext().cancelOutstandingRequests(
							channelStr);

					// Delete the corresponding outdated MessageRoute
					String correlatorStr = ChannelRegistry.getRegistry()
							.getPickResponseChannelCorrelator(
									getBpelRuntimeContext().getPid(),
									channelStr);
					ProcessDAO process = getBpelRuntimeContext()
							.getBpelProcess().getProcessDAO();
					CorrelatorDAO correlator = process
							.getCorrelator(correlatorStr);
					correlator.removeRoutes(channelStr, process
							.getInstance(getBpelRuntimeContext().getPid()));
				}
			}
		}

		instance(_iterationRunnable);
	}

}
