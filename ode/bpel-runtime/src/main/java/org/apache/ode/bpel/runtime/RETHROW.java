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
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;

/**
 * FaultActivity
 */
class RETHROW extends ACTIVITY {
	private static final long serialVersionUID = -6433171659586530126L;
	private static final Log __log = LogFactory.getLog(RETHROW.class);

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	RETHROW(ActivityInfo self, ScopeFrame scopeFrame, LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public void run() {
		// State of this Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 102414351188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					RETHROW.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					RETHROW.this.Activity_Completed();
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 1546454556L;

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
				_self.self, RETHROW.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void Activity_Completed() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 54741373711588855L;

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
			private static final long serialVersionUID = 689665675562L;

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

		// Event Activity_Executed
		ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		// find the faultData in the scope stack
		FaultData fault = _scopeFrame.getFault();
		if (fault == null) {
			String msg = "Attempting to execute 'rethrow' activity with no visible fault in scope.";
			__log.error(msg);
			throw new InvalidProcessException(msg);
		}

		// State of this Activity is Faulted
		final FaultData tmp = fault;
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 1024237858188875L;

			public void linkStatus(boolean value) {
				if (value) // continue received
				{
					_self.parent.completed(tmp, CompensationHandler.emptySet());
				} else // suppress_fault received
				{
					_terminatedActivity = true;
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
			}

		};
		object(false, receiver);

		// Event Activity_Faulted
		ActivityFaulted evt2 = new ActivityFaulted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal, fault);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}
}
