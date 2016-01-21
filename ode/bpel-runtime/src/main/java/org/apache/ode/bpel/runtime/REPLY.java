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

import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivitySkipped;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OReply;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class REPLY extends ACTIVITY {
	private static final long serialVersionUID = 3040651951885161304L;
	private static final Log __log = LogFactory.getLog(REPLY.class);

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	REPLY(ActivityInfo self, ScopeFrame scopeFrame, LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public void run() {
		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373711188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					REPLY.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					REPLY.this.Activity_Completed();
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
				ActivitySkipped evt = new ActivitySkipped(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_skippedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent
						.completed(null, CompensationHandler.emptySet());
			}

		};

		object(false, (termChan).or(receiver));

		// Event Activity_Ready
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(), _self.o.getXpath(),
				_self.aId, sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
				process_name, process_ID, _self.o.getArt(), false, signal,
				_self.self, REPLY.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		final OReply oreply = (OReply) _self.o;

		if (__log.isDebugEnabled()) {
			__log.debug("<reply>  partnerLink=" + oreply.partnerLink
					+ ", operation=" + oreply.operation);
		}
		FaultData fault = null;

		// TODO: Check for fault without message.

		try {
			sendVariableReadEvent(_scopeFrame.resolve(oreply.variable));
			Node msg = fetchVariableData(_scopeFrame.resolve(oreply.variable),
					false);

			assert msg instanceof Element;

			for (Iterator<OScope.CorrelationSet> i = oreply.initCorrelations
					.iterator(); i.hasNext();) {
				OScope.CorrelationSet cset = i.next();
				initializeCorrelation(_scopeFrame.resolve(cset),
						_scopeFrame.resolve(oreply.variable),
						_self.o.getXpath());
			}
			for (OScope.CorrelationSet aJoinCorrelation : oreply.joinCorrelations) {
				// will be ignored if already initialized
				initializeCorrelation(_scopeFrame.resolve(aJoinCorrelation),
						_scopeFrame.resolve(oreply.variable),
						_self.o.getXpath());
			}

			// send reply
			getBpelRuntimeContext()
					.reply(_scopeFrame.resolve(oreply.partnerLink),
							oreply.operation.getName(),
							oreply.messageExchangeId, (Element) msg,
							(oreply.fault != null) ? oreply.fault : null);
		} catch (FaultException e) {
			__log.error(e);
			// @hahnml: Set the message of the exception to the FaultData object
			fault = createFault(e.getQName(), oreply, e.getMessage());
		}

		// State of this Activity ist Faulted or Waiting
		// Faulted
		if (fault != null) {
			final FaultData tmp = fault;
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 10241373771188875L;

				public void linkStatus(boolean value) {
					if (value) // continue received
					{
						_self.parent.completed(tmp,
								CompensationHandler.emptySet());
					} else // suppress_fault received
					{
						_terminatedActivity = true;
						_self.parent.completed(null,
								CompensationHandler.emptySet());
					}
				}

			};
			object(false, receiver);

			// Event Activity_Faulted
			ActivityFaulted evt2 = new ActivityFaulted(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal, fault);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}
		// Waiting
		else {
			Activity_Completed();
		}
	}

	public void Activity_Completed() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373711588855L;

			public void linkStatus(boolean value) {

				// Event Activity_Complete
				ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_self.parent.completed(null, CompensationHandler.emptySet());
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 15465675562L;

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
				ActivitySkipped evt = new ActivitySkipped(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_skippedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent
						.completed(null, CompensationHandler.emptySet());
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
}
