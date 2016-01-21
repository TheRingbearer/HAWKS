package org.apache.ode.bpel.runtime;

import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;

//@stmz: keep the BpelRuntimeContext alive for some time 
public class RUNNER extends BpelJacobRunnable {
	private static final long serialVersionUID = 555136346635L;

	public RUNNER() {

	}

	public void run() {
		BpelRuntimeContextImpl runTemp = (BpelRuntimeContextImpl) getBpelRuntimeContext();
		// @stmz: via runTemp.starting we make sure, that a process doesn't get
		// interrupted while on
		// its way to the instantiating activity (because in that case, the
		// incoming message is
		// not saved, that means, we would lose this information leading to a
		// Null Pointer Error)
		if (runTemp.getStarting()) {
			instance(RUNNER.this);
		}
	}

}
