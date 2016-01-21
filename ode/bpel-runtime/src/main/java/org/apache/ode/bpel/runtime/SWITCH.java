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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OSwitch;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.w3c.dom.Element;

/**
 * Runtime implementation of the <code>&lt;switch&gt;</code> activity.
 */
class SWITCH extends ACTIVITY {
	private static final long serialVersionUID = 1L;
	private static final Log __log = LogFactory.getLog(SWITCH.class);
	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	public SWITCH(ActivityInfo self, ScopeFrame scopeFrame, LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public final void run() {
		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 65543875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					SWITCH.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					dpe(_self.o.outgoingLinks);
					SWITCH.this.Activity_Completed(
							CompensationHandler.emptySet(), false);
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 786973346L;

			public void terminate() {
				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;
				dpe(_self.o.outgoingLinks);
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
				_self.self, SWITCH.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		OSwitch oswitch = (OSwitch) _self.o;
		OSwitch.OCase matchedOCase = null;
		FaultData faultData = null;

		EvaluationContext evalCtx = getEvaluationContext();
		for (Iterator i = oswitch.getCases().iterator(); i.hasNext();) {
			OSwitch.OCase ocase = (OSwitch.OCase) i.next();
			try {
				try {
					if (getBpelRuntimeContext().getExpLangRuntime()
							.evaluateAsBoolean(ocase.expression, evalCtx)) {
						matchedOCase = ocase;
						break;
					}
				} catch (EvaluationException e) {
					__log.error("Sub-Language execution failure evaluating "
							+ ocase.expression, e);
					throw new FaultException(
							oswitch.getOwner().constants.qnSubLanguageExecutionFault,
							e.getMessage());
				}
			} catch (FaultException e) {
				__log.error(e.getMessage(), e);
				// @hahnml: Set the message of the exception to the FaultData object
				faultData = createFault(e.getQName(), ocase, e.getMessage());
				Activity_Faulted(faultData, CompensationHandler.emptySet(),
						false);

				// Dead path all the child activiites:
				for (Iterator<OSwitch.OCase> j = oswitch.getCases().iterator(); j
						.hasNext();)
					dpe(j.next().activity);
				return;
			}
		}

		// Dead path cases not chosen
		for (Iterator<OSwitch.OCase> i = oswitch.getCases().iterator(); i
				.hasNext();) {
			OSwitch.OCase cs = i.next();
			if (cs != matchedOCase)
				dpe(cs.activity);
		}

		// no conditions satisfied, we're done.
		if (matchedOCase == null) {
			Activity_Completed(CompensationHandler.emptySet(), false);
		} else /* matched case */{
			ParentScopeChannel tmp = newChannel(ParentScopeChannel.class);
			TerminationChannel term = newChannel(TerminationChannel.class);
			ActivityInfo child = new ActivityInfo(genMonotonic(),
					matchedOCase.activity, term, tmp);
			instance(createChild(child, _scopeFrame, _linkFrame));
			instance(new CHILD_WAIT(tmp, child));
		}
	}

	private class CHILD_WAIT extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;
		private ParentScopeChannel _parentScopeChannel;

		ActivityInfo _child;
		Boolean terminated = false;

		private CHILD_WAIT(ParentScopeChannel parentScopeChannel,
				ActivityInfo info) {
			this._parentScopeChannel = parentScopeChannel;
			this._child = info;
			
			// @hahnml: Set the OBase id
			oId = SWITCH.this.oId;
		}

		public void run() {
			HashSet<ChannelListener> mlSet = new HashSet<ChannelListener>();
			mlSet.add(new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = 8658646535L;

				public void terminate() {
					if (!terminated) {
						// Event Activity_Terminated
						ActivityTerminated evt = new ActivityTerminated(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_terminatedActivity = true;
						terminated = true;
						replication(_child.self).terminate();
					}
					instance(CHILD_WAIT.this);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
				}
			});
			mlSet.add(new ParentScopeChannelListener(this._parentScopeChannel) {
				private static final long serialVersionUID = 3523425675674813033L;

				public void compensate(OScope scope, SynchChannel ret) {
					_self.parent.compensate(scope, ret);
					instance(CHILD_WAIT.this);
				}

				public void completed(FaultData flt,
						Set<CompensationHandler> compensations) {
					// Set the fault to the activity's choice, if and only if no
					// previous
					// fault
					// has been detected (first fault wins).
					HashSet<CompensationHandler> comps = new HashSet<CompensationHandler>(
							compensations);
					comps.addAll(compensations);
					if (flt != null) {
						Activity_Faulted(flt, comps, terminated);
					} else {
						Activity_Completed(comps, terminated);
					}

				}

				public void cancelled() {
					completed(null, CompensationHandler.emptySet());
				}

				public void failure(String reason, Element data) {
					completed(null, CompensationHandler.emptySet());
				}

			});

			object(false, mlSet);
		}

	}

	public void Activity_Faulted(FaultData fault,
			Set<CompensationHandler> compensations, Boolean terminated) {
		final FaultData tmp = fault;
		final Set<CompensationHandler> compens = compensations;

		if (terminated) {
			_self.parent.completed(tmp, compens);
		} else {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 568157675L;

				public void linkStatus(boolean value) {
					if (value) // continue received
					{
						_self.parent.completed(tmp, compens);
					} else // suppress_fault received
					{
						_terminatedActivity = true;
						_self.parent.completed(null, compens);
					}
				}

			};
			object(false, receiver);

			// Event Activity_Faulted
			ActivityFaulted evt2 = new ActivityFaulted(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal, tmp);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}
	}

	public void Activity_Completed(Set<CompensationHandler> compensations,
			Boolean terminated) {
		final Set<CompensationHandler> compens = compensations;

		if (terminated) {
			_self.parent.completed(null, compens);
		} else {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 23551588855L;

				public void linkStatus(boolean value) {

					// Event Activity_Complete
					ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
							_self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_self.parent.completed(null, compens);
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 65855675562L;

				public void terminate() {

					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent.completed(null, compens);
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
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}
	}
}