package org.apache.ode.bpel.engine.iteration;

import java.util.List;

import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.CompensationHandler;
import org.apache.ode.bpel.runtime.ORDEREDCOMPENSATOR;
import org.apache.ode.jacob.SynchChannel;
import org.apache.ode.jacob.SynchChannelListener;

/**
 * This class is used to compensate all activities calculated in
 * ReexecutionHandler.calculateCompensations() and to instantiate the iteration
 * runnable.
 * 
 * @author hahnml
 * 
 */
public class ReexecutionContainerRunnable extends BpelJacobRunnable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private BpelJacobRunnable _iterationRunnable = null;
	private List<CompensationHandler> _compensationChannels = null;

	public ReexecutionContainerRunnable(BpelJacobRunnable iterationActivity,
			List<CompensationHandler> compensationChannels) {
		_iterationRunnable = iterationActivity;
		_compensationChannels = compensationChannels;
	}

	@Override
	public void run() {
		// Do the compensation
		SynchChannel ret = newChannel(SynchChannel.class);
		instance(new ORDEREDCOMPENSATOR(_compensationChannels, ret));

		// Register a listener on the SynchChannel to inject the iteration
		// runnable synchronously after the compensation is done.
		object(new SynchChannelListener(ret) {
			private static final long serialVersionUID = 3763991229748926216L;

			public void ret() {
				// Instantiate the iteration runnable
				instance(_iterationRunnable);
			}
		});

	}

}
