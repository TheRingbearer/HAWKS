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
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.extensions.events.IterationComplete;
import org.apache.ode.bpel.extensions.events.LoopConditionFalse;
import org.apache.ode.bpel.extensions.events.LoopConditionTrue;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OWhile;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.w3c.dom.Element;

/**
 * BPEL &lt;while&gt; activity
 */
class WHILE extends ACTIVITY {
	private static final long serialVersionUID = 1L;

	private static final Log __log = LogFactory.getLog(WHILE.class);

	private Set<CompensationHandler> _compHandlers = new HashSet<CompensationHandler>();

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	Boolean first_time = true;

	public WHILE(ActivityInfo self, ScopeFrame scopeFrame, LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public void run() {
		if (first_time) {
			// State of Activity is Ready
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 4573333875L;

				public void linkStatus(boolean value) {
					if (value) // Incoming Event Start_Activity received
					{
						WHILE.this.execute();
					} else // Incoming Event Complete_Activity received
					{
						WHILE.this.Activity_Completed(
								CompensationHandler.emptySet(), false);
					}
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 35736346L;

				public void terminate() {
					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
				}

			};

			object(false, (termChan).or(receiver));

			// Event Activity_Ready
			ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal, _self.self, WHILE.this);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		} else {
			WHILE.this.execute();
		}
	}

	public void execute() {
		if (first_time) {
			// Event Activity_Executing
			ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			first_time = false;
		}

		Check_Condition();
	}

	public void Check_Condition() {
		boolean condResult = false;

		try {
			condResult = checkCondition();
		} catch (FaultException fe) {
			__log.error(fe);
			// @hahnml: Set the message of the exception to the FaultData object
			FaultData tmp_fault = createFault(fe.getQName(), _self.o, fe.getMessage());
			Activity_Faulted(tmp_fault, _compHandlers, false);
			return;
		}

		if (condResult) // LoopConditionTrue
		{
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 686594947675L;

				public void linkStatus(boolean value) {
					if (value) {
						ActivityInfo child = new ActivityInfo(genMonotonic(),
								getOWhile().activity,
								newChannel(TerminationChannel.class),
								newChannel(ParentScopeChannel.class));
						instance(createChild(child, _scopeFrame, _linkFrame));
						instance(new WAITER(child));
					} else {
						WHILE.this.Activity_Completed(_compHandlers, false);
					}
				}

			};
			object(false, receiver);

			// Event Loop_Condition_True
			LoopConditionTrue loop_evt = new LoopConditionTrue(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(loop_evt);
		} else /* stop. */// LoopConditionFalse
		{
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 686699547675L;

				public void linkStatus(boolean value) {
					if (value) {
						WHILE.this.Activity_Completed(_compHandlers, false);
					} else {
						ActivityInfo child = new ActivityInfo(genMonotonic(),
								getOWhile().activity,
								newChannel(TerminationChannel.class),
								newChannel(ParentScopeChannel.class));
						instance(createChild(child, _scopeFrame, _linkFrame));
						instance(new WAITER(child));
					}
				}

			};
			object(false, receiver);

			// Event Loop_Condition_False
			LoopConditionFalse loop_evt = new LoopConditionFalse(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(loop_evt);
		}
	}

	public void Activity_Faulted(FaultData fault,
			Set<CompensationHandler> compens, Boolean term) {
		final FaultData tmp = fault;
		final Set<CompensationHandler> compen_tmp = compens;

		if (term) {
			// Event Activity_Terminated
			ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			_self.parent.completed(tmp, compen_tmp);
		} else {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 7684455157675L;

				public void linkStatus(boolean value) {
					if (value) // continue received
					{
						_self.parent.completed(tmp, compen_tmp);
					} else // suppress_fault received
					{
						_terminatedActivity = true;
						_self.parent.completed(null, compen_tmp);
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

	public void Activity_Completed(Set<CompensationHandler> compens,
			Boolean term) {
		final Set<CompensationHandler> compen_tmp = compens;

		if (term) {
			// Event Activity_Terminated
			ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			_self.parent.completed(null, compen_tmp);
		} else {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 7896978848855L;

				public void linkStatus(boolean value) {

					// Event Activity_Complete
					ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
							_self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_self.parent.completed(null, compen_tmp);
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 868346457L;

				public void terminate() {

					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent.completed(null, compen_tmp);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "<T:Act:While:" + _self.o + ">";
	}

	protected Log log() {
		return __log;
	}

	private OWhile getOWhile() {
		return (OWhile) _self.o;
	}

	/**
	 * Evaluates the while condition.
	 * 
	 * @return <code>true</code> if the while condition is satisfied,
	 *         <code>false</code> otherwise.
	 * @throws FaultException
	 *             in case of standard expression fault (e.g. selection failure)
	 */
	private boolean checkCondition() throws FaultException {
		try {
			return getBpelRuntimeContext().getExpLangRuntime()
					.evaluateAsBoolean(getOWhile().whileCondition,
							getEvaluationContext());
		} catch (EvaluationException e) {
			String msg = "Unexpected expression evaluation error checking while condition.";
			__log.error(msg, e);
			throw new InvalidProcessException(msg, e);
		}
	}

	private class WAITER extends BpelJacobRunnable {
		private static final long serialVersionUID = -7645042174027252066L;
		private ActivityInfo _child;
		private boolean _terminated;

		WAITER(ActivityInfo child) {
			_child = child;
			
			// @hahnml: Set the OBase id
			oId = WHILE.this.oId;
		}

		public void run() {
			object(false, new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = -5471984635653784051L;

				public void terminate() {
					if (!_terminated) {
						_terminated = true;
						replication(_child.self).terminate();
					}
					instance(WAITER.this);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
				}
			}.or(new ParentScopeChannelListener(_child.parent) {
				private static final long serialVersionUID = 3907167240907524405L;

				public void compensate(OScope scope, SynchChannel ret) {
					_self.parent.compensate(scope, ret);
					instance(WAITER.this);
				}

				public void completed(FaultData faultData,
						Set<CompensationHandler> compensations) {
					_compHandlers.addAll(compensations);
					if (_terminated || faultData != null) {
						if (faultData != null) {
							Activity_Faulted(faultData, _compHandlers,
									_terminated);
						} else {
							Activity_Completed(_compHandlers, _terminated);
						}
					} else {
						Iteration_Complete();
					}
				}

				public void cancelled() {
					completed(null, CompensationHandler.emptySet());
				}

				public void failure(String reason, Element data) {
					completed(null, CompensationHandler.emptySet());
				}
			}));
		}

		public void Iteration_Complete() {

			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 9345463433575L;

				public void linkStatus(boolean value) {
					instance(WHILE.this);
				}

			};
			object(false, receiver);

			// Event Iteration_Complete
			IterationComplete it_evt = new IterationComplete(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(it_evt);
		}
	}
}
