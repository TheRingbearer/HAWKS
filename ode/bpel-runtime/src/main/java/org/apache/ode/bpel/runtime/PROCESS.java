/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.runtime;

import org.apache.ode.bpel.evt.ProcessInstanceStartedEvent;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OFailureHandling;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.jacob.SynchChannel;

import java.util.Set;
import org.w3c.dom.Element;

public class PROCESS extends BpelJacobRunnable {
	private static final long serialVersionUID = 1L;
	private OProcess _oprocess;

	public PROCESS(OProcess process) {
		_oprocess = process;
	}

	public void run() {
		BpelRuntimeContext ntive = getBpelRuntimeContext();
		Long scopeInstanceId = ntive.createScopeInstance(null,
				_oprocess.procesScope);

		ProcessInstanceStartedEvent evt = new ProcessInstanceStartedEvent();
		evt.setRootScopeId(scopeInstanceId);
		evt.setScopeDeclarationId(_oprocess.procesScope.getId());
		ntive.sendEvent(evt);

		// @hahnml: We have to pause the execution of the process for some time,
		// that we can register our blocking events for debugging.
		try {
			Thread.sleep(GenericController.getInstance().getWaitingTime());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ActivityInfo child = new ActivityInfo(genMonotonic(),
				_oprocess.procesScope, newChannel(TerminationChannel.class),
				newChannel(ParentScopeChannel.class, _oprocess.toString()));
		ScopeFrame processFrame = new ScopeFrame(_oprocess.procesScope,
				scopeInstanceId, null, null);
		
		// @hahnml: Buffer the scope to register it at the ActivityEventHandler
		SCOPE processScope = new SCOPE(child, processFrame, new LinkFrame(null));
		instance(processScope);

		// @hahnml: Register the process SCOPE at the ActivityEventHandler
		ActivityEventHandler.getInstance().addRunningProcessSCOPE(processScope);
		
		object(new ParentScopeChannelListener(child.parent) {
			private static final long serialVersionUID = -8564969578471906493L;

			public void compensate(OScope scope, SynchChannel ret) {
				assert false;
			}

			public void completed(FaultData fault,
					Set<CompensationHandler> compensations) {
				BpelRuntimeContext nativeAPI = (BpelRuntimeContext) getExtension(BpelRuntimeContext.class);
				if (fault == null) {
					nativeAPI.completedOk();
				} else {
					nativeAPI.completedFault(fault);
				}
				
				// @hahnml: Remove the process SCOPE from the ActivityEventHandler
				ActivityEventHandler.getInstance().removeRunningProcessSCOPE(nativeAPI.getPid());
			}

			public void cancelled() {
				this.completed(null, CompensationHandler.emptySet());
			}

			public void failure(String reason, Element data) {
				FaultData faultData = createFault(
						OFailureHandling.FAILURE_FAULT_NAME, _oprocess, reason);
				this.completed(faultData, CompensationHandler.emptySet());
				
				// @hahnml: Remove the process SCOPE from the ActivityEventHandler
				BpelRuntimeContext nativeAPI = (BpelRuntimeContext) getExtension(BpelRuntimeContext.class);
				ActivityEventHandler.getInstance().removeRunningProcessSCOPE(nativeAPI.getPid());
			}
		});
	}
}
