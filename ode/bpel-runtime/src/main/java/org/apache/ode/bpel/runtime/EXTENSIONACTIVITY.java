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

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.extension.ExtensionOperation;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OExtensionActivity;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.runtime.common.extension.AbstractSyncExtensionOperation;
import org.apache.ode.bpel.runtime.common.extension.ExtensibilityQNames;
import org.apache.ode.bpel.runtime.common.extension.ExtensionContext;

/**
 * JacobRunnable that delegates the work of the <code>extensionActivity</code>
 * activity to a registered extension implementation.
 * 
 * @author Tammo van Lessen (University of Stuttgart)
 */
public class EXTENSIONACTIVITY extends ACTIVITY {
	private static final long serialVersionUID = 1L;
	private static final Log __log = LogFactory.getLog(EXTENSIONACTIVITY.class);
	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	public EXTENSIONACTIVITY(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public final void run() {
		// State of the Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373711188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					EXTENSIONACTIVITY.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					EXTENSIONACTIVITY.this.Activity_Complete(null);
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 154656756L;

			public void terminate() {
				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;
				_self.parent.completed(null, CompensationHandler.emptySet());
			}
			
			//krwczk: TODO -implement skip
			public void skip() {
				
			}

		};

		object(false, (termChan).or(receiver));

		// Event Activity_Ready
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(), _self.o.getXpath(),
				_self.aId, sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
				process_name, process_ID, _self.o.getArt(), false, signal,
				_self.self, EXTENSIONACTIVITY.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		final ExtensionContext context = new ExtensionContextImpl(this, getBpelRuntimeContext());
		final OExtensionActivity oea = (OExtensionActivity) _self.o;

		try {
			ExtensionOperation ea = getBpelRuntimeContext()
					.createExtensionActivityImplementation(oea.extensionName);
			if (ea == null) {
				for (OProcess.OExtension oe : oea.getOwner().mustUnderstandExtensions) {
					if (oea.extensionName.getNamespaceURI().equals(
							oe.namespaceURI)) {
						__log.warn("Lookup of extension activity "
								+ oea.extensionName + " failed.");
						throw new FaultException(
								ExtensibilityQNames.UNKNOWN_EA_FAULT_NAME,
								"Lookup of extension activity "
										+ oea.extensionName
										+ " failed. No implementation found.");
					}
				}

				Activity_Complete(context);

				return;
			}

			ea.run(context, oea.nestedElement.getElement());
			
			//@sonntamo: the asynchronous EA must control its completeness itself
			if (ea instanceof AbstractSyncExtensionOperation)
				Activity_Complete(context);

		} catch (FaultException fault) {
			__log.error(fault);
			Activity_Faulted(context, fault);
		}

	}

	//@sonntamo: TODO this must be moved to the ExtensionContextImpl
	public void Activity_Complete(final ExtensionContext context) {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 102342132855L;

			public void linkStatus(boolean value) {

				// Event Activity_Complete
				ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);

				if (context != null) {
					context.complete();
				} else {
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 1569976844562L;

			public void terminate() {

				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;

				if (context != null) {
					context.complete();
				} else {
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
			}
			
			//krwczk: TODO -implement skip
			public void skip() {
				
			}

		};

		object(false, (termChan).or(receiver));

		// Event Activity_Executed
		ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	public void Activity_Faulted(final ExtensionContext context,
			final FaultException e) {
		// @hahnml: Set the message of the exception to the FaultData object
		final FaultData tmp = createFault(e.getQName(), _self.o, e.getMessage());

		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373771353L;

			public void linkStatus(boolean value) {
				if (value) // continue received
				{
					if (context != null) {
						context.completeWithFault(e);
					} else {
						_self.parent.completed(tmp,
								CompensationHandler.emptySet());
					}
				} else // suppress_fault received
				{
					_terminatedActivity = true;
					if (context != null) {
						context.complete();
					} else {
						_self.parent.completed(null,
								CompensationHandler.emptySet());
					}
				}

			}

		};
		object(false, receiver);

		// Event Activity_Faulted
		ActivityFaulted evt2 = new ActivityFaulted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal, tmp);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}
}
