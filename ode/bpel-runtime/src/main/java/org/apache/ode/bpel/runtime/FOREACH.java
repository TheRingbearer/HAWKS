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
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.evt.VariableModificationEvent;
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
import org.apache.ode.bpel.o.OExpression;
import org.apache.ode.bpel.o.OForEach;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.stl.FilterIterator;
import org.apache.ode.utils.stl.MemberOfFunction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class FOREACH extends ACTIVITY {

	private static final long serialVersionUID = 1L;
	private static final Log __log = LogFactory.getLog(FOREACH.class);

	private OForEach _oforEach;
	private Set<ChildInfo> _children = new HashSet<ChildInfo>();
	private Set<CompensationHandler> _compHandlers = new HashSet<CompensationHandler>();
	private int _startCounter = -1;
	private int _finalCounter = -1;
	private int _currentCounter = -1;
	private int _completedCounter = 0;
	private int _completionCounter = -1;

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	public FOREACH(ActivityInfo self, ScopeFrame frame, LinkFrame linkFrame) {
		super(self, frame, linkFrame);
		_oforEach = (OForEach) self.o;
	}

	public void run() {
		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 14363875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					FOREACH.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					FOREACH.this.Activity_Completed(CompensationHandler
							.emptySet());
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 145357773346L;

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
				_self.self, FOREACH.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		try {
			_startCounter = evaluateCondition(_oforEach.startCounterValue);
			_finalCounter = evaluateCondition(_oforEach.finalCounterValue);
			if (_oforEach.completionCondition != null) {
				_completionCounter = evaluateCondition(_oforEach.completionCondition.branchCount);
			}
			_currentCounter = _startCounter;
		} catch (FaultException fe) {
			__log.error(fe);
			// @hahnml: Set the message of the exception to the FaultData object
			Activity_Faulted(createFault(fe.getQName(), _self.o, fe.getMessage()), _compHandlers);
			return;
		}

		// Checking for bpws:invalidBranchCondition when the counter limit is
		// superior
		// to the maximum number of children
		if (_completionCounter > 0
				&& _completionCounter > _finalCounter - _startCounter) {
			Activity_Faulted(
					createFault(
							_oforEach.getOwner().constants.qnInvalidBranchCondition,
							_self.o), _compHandlers);
			return;
		}

		// There's really nothing to do
		if (_finalCounter < _startCounter || _completionCounter == 0) {
			// LoopConditionFalse
			LoopConditionFalse();
		} else {
			// If we're parrallel, starting all our child copies, otherwise one
			// will
			// suffice.
			if (_oforEach.parallel) {
				for (int m = _startCounter; m <= _finalCounter; m++) {
					newChild();
				}
				instance(new ACTIVE());
			} else {
				// LoopConditionTrue
				LoopConditionTrue();
			}
		}
	}

	public void LoopConditionTrue() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 21478847675L;

			public void linkStatus(boolean value) {
				if (value) {
					newChild();
					instance(new ACTIVE());
				} else {
					FOREACH.this.Activity_Completed(_compHandlers);
				}
			}

		};
		object(false, receiver);

		// Event Loop_Condition_True
		LoopConditionTrue loop_evt2 = new LoopConditionTrue(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine()
				.fireEvent(loop_evt2);
	}

	public void LoopConditionFalse() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 2145547675L;

			public void linkStatus(boolean value) {
				if (value) {
					FOREACH.this.Activity_Completed(_compHandlers);
				} else {
					newChild();
					instance(new ACTIVE());
				}
			}

		};
		object(false, receiver);

		// Event Loop_Condition_True
		LoopConditionFalse loop_evt2 = new LoopConditionFalse(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine()
				.fireEvent(loop_evt2);
	}

	private class ACTIVE extends BpelJacobRunnable {
		private static final long serialVersionUID = -5642862698981385732L;

		private FaultData _fault;
		private boolean _terminateRequested = false;
		
		ACTIVE() {
			// @hahnml: Set the OBase id
			oId = FOREACH.this.oId;
		}

		public void run() {
			Iterator<ChildInfo> active = active();
			// Continuing as long as a child is active
			if (active().hasNext()) {

				Set<ChannelListener> mlSet = new HashSet<ChannelListener>();
				mlSet.add(new TerminationChannelListener(_self.self) {
					private static final long serialVersionUID = 2554750257484084466L;

					public void terminate() {
						if (!_terminateRequested) {
							// Terminating all children before sepuku
							for (Iterator<ChildInfo> i = active(); i.hasNext();)
								replication(i.next().activity.self).terminate();
							_terminateRequested = true;
						}
						instance(ACTIVE.this);
					}
					
					//krwczk: TODO -implement skip
					public void skip() {
						
					}
				});
				for (; active.hasNext();) {
					// Checking out our children
					final ChildInfo child = active.next();
					mlSet.add(new ParentScopeChannelListener(
							child.activity.parent) {
						private static final long serialVersionUID = -8027205709961438172L;

						public void compensate(OScope scope, SynchChannel ret) {
							// Forward compensation to parent
							_self.parent.compensate(scope, ret);
							instance(ACTIVE.this);
						}

						public void completed(FaultData faultData,
								Set<CompensationHandler> compensations) {
							child.completed = true;

							_compHandlers.addAll(compensations);

							if (_completionCounter > 0
									&& _oforEach.completionCondition.successfulBranchesOnly) {
								if (faultData != null)
									_completedCounter++;
							} else
								_completedCounter++;

							// Keeping the fault to let everybody know
							if (faultData != null && _fault == null) {
								_fault = faultData;
							}
							if (!_oforEach.parallel && !_terminateRequested
									&& faultData == null) {
								Iteration_Complete();
							}

							else {
								if (shouldContinue() && _fault == null
										&& !_terminateRequested) {
									// Everything fine. If parrallel, just let
									// our children be,
									// otherwise making a new child

									// never reached
									if (!_oforEach.parallel)
										newChild();

								} else {
									// Work is done or something wrong happened,
									// children
									// shouldn't continue
									// or enough branches completed
									for (Iterator<ChildInfo> i = active(); i
											.hasNext();)
										replication(i.next().activity.self)
												.terminate();
								}
								instance(ACTIVE.this);
							}

						}

						public void cancelled() {
							completed(null, CompensationHandler.emptySet());
						}

						public void failure(String reason, Element data) {
							completed(null, CompensationHandler.emptySet());
						}
					});
				}
				object(false, mlSet);
			} else {
				// No children left, either because they've all been executed or
				// because
				// we
				// had to make them stop.
				if (_terminateRequested) {
					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent.completed(_fault, _compHandlers);
				} else {
					if (_fault != null) {
						Activity_Faulted(_fault, _compHandlers);

					} else {
						Activity_Completed(_compHandlers);
					}
				}
			}
		}

		public void Iteration_Complete() {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 2144565157675L;

				public void linkStatus(boolean value) {
					ACTIVE.this.Check_Condition();
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

		public void Check_Condition() {
			if (shouldContinue())// LoopCondition = true
			{
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 213421347675L;

					public void linkStatus(boolean value) {
						if (value) {
							newChild();
							instance(ACTIVE.this);
						} else {
							FOREACH.this.Activity_Completed(_compHandlers);
						}
					}

				};
				object(false, receiver);

				// Event Loop_Condition_True
				LoopConditionTrue loop_evt = new LoopConditionTrue(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(loop_evt);
			} else // LoopCondition = false
			{
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 12451347675L;

					public void linkStatus(boolean value) {
						if (value) {
							FOREACH.this.Activity_Completed(_compHandlers);
						} else {
							newChild();
							instance(ACTIVE.this);
						}
					}

				};
				object(false, receiver);

				// Event Loop_Condition_False
				LoopConditionFalse loop_evt = new LoopConditionFalse(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(loop_evt);
			}
		}
	}

	private boolean shouldContinue() {
		boolean stop = false;
		if (_completionCounter > 0) {
			stop = (_completedCounter >= _completionCounter) || stop;
		}
		stop = (_startCounter + _completedCounter > _finalCounter) || stop;
		return !stop;
	}

	private int evaluateCondition(OExpression condition) throws FaultException {
		try {
			return getBpelRuntimeContext().getExpLangRuntime()
					.evaluateAsNumber(condition, getEvaluationContext())
					.intValue();
		} catch (EvaluationException e) {
			String msg;
			msg = "ForEach counter value couldn't be evaluated as xs:unsignedInt.";
			__log.error(msg, e);
			throw new FaultException(
					_oforEach.getOwner().constants.qnForEachCounterError, msg);
		}
	}

	private void newChild() {
		ChildInfo child = new ChildInfo(new ActivityInfo(genMonotonic(),
				_oforEach.innerScope, newChannel(TerminationChannel.class),
				newChannel(ParentScopeChannel.class)));
		_children.add(child);

		// Creating the current counter value node
		Document doc = DOMUtils.newDocument();
		Node counterNode = doc.createTextNode("" + _currentCounter++);

		// Instantiating the scope directly to keep control of its scope frame,
		// allows
		// the introduction of the counter variable in there (monkey business
		// that
		// is).
		ScopeFrame newFrame = new ScopeFrame(_oforEach.innerScope,
				getBpelRuntimeContext().createScopeInstance(
						_scopeFrame.scopeInstanceId, _oforEach.innerScope),
				_scopeFrame, null);
		VariableInstance vinst = newFrame.resolve(_oforEach.counterVariable);

		try {
			initializeVariable(vinst, counterNode);
		} catch (ExternalVariableModuleException e) {
			__log.error("Exception while initializing external variable", e);
			_self.parent.failure(e.toString(), null);
			return;
		}

		// Generating event
		VariableModificationEvent se = new VariableModificationEvent(
				vinst.declaration.name, _self.o.getXpath(), _self.aId,
				sFrame.oscope.getXpath(), vinst.declaration.getXpath(),
				sFrame.scopeInstanceId);
		se.setNewValue(counterNode);
		if (_oforEach.debugInfo != null)
			se.setLineNo(_oforEach.debugInfo.startLine);
		sendEvent(se);

		instance(new SCOPE(child.activity, newFrame, _linkFrame));
	}

	public void Activity_Faulted(FaultData fault,
			Set<CompensationHandler> compens) {
		final FaultData tmp = fault;
		final Set<CompensationHandler> compen_tmp = compens;
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 903465157675L;

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
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	public void Activity_Completed(Set<CompensationHandler> compens) {
		final Set<CompensationHandler> compen_tmp = compens;
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 32422448855L;

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
			private static final long serialVersionUID = 45643675562L;

			public void terminate() {

				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
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
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	public String toString() {
		return "<T:Act:Flow:" + _oforEach.name + ">";
	}

	private Iterator<ChildInfo> active() {
		return new FilterIterator<ChildInfo>(_children.iterator(),
				new MemberOfFunction<ChildInfo>() {
					public boolean isMember(ChildInfo childInfo) {
						return !childInfo.completed;
					}
				});
	}

}
