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

import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;

/**
 * JacobRunnable that performs the work of the <code>empty</code> activity.
 */
class TERMINATE extends ACTIVITY {

	private static final long serialVersionUID = 1L;

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	private TerminationChannel termChannel = newChannel(TerminationChannel.class);

	public TERMINATE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public final void run() {
		// State of Activity Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 1021525188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					TERMINATE.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					TERMINATE.this.Activity_Completed();
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				termChannel) {
			private static final long serialVersionUID = 66654344L;

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
				termChannel, TERMINATE.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		// @hahnml: Complete the activity before terminating the instance so
		// that the Activity_Completed event will be fired.
		Activity_Completed();

		getBpelRuntimeContext().terminate();
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

		object(false, receiver);

		// Event Activity_Executed
		ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);

		// @hahnml: Fire the ActivityComplete event.
		// Event Activity_Complete
		ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		_self.parent.completed(null, CompensationHandler.emptySet());
	}

}
