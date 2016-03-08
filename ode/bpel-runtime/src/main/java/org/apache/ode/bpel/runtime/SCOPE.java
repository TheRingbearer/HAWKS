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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dao.ScopeStateEnum;
import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;
import org.apache.ode.bpel.evt.ScopeCompletionEvent;
import org.apache.ode.bpel.evt.ScopeFaultEvent;
import org.apache.ode.bpel.evt.ScopeStartEvent;
import org.apache.ode.bpel.evt.VariableModificationEvent;
import org.apache.ode.bpel.extensions.events.ScopeActivityComplete;
import org.apache.ode.bpel.extensions.events.ScopeActivityExecuted;
import org.apache.ode.bpel.extensions.events.ScopeActivityExecuting;
import org.apache.ode.bpel.extensions.events.ScopeActivityFaulted;
import org.apache.ode.bpel.extensions.events.ScopeActivityReady;
import org.apache.ode.bpel.extensions.events.ScopeActivitySkipped;
import org.apache.ode.bpel.extensions.events.ScopeActivityTerminated;
import org.apache.ode.bpel.extensions.events.ScopeCompleteWithFault;
import org.apache.ode.bpel.extensions.events.ScopeHandlingFault;
import org.apache.ode.bpel.extensions.events.ScopeHandlingTermination;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OCatch;
import org.apache.ode.bpel.o.OElementVarType;
import org.apache.ode.bpel.o.OEventHandler;
import org.apache.ode.bpel.o.OFailureHandling;
import org.apache.ode.bpel.o.OFaultHandler;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OMessageVarType;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OVarType;
import org.apache.ode.bpel.runtime.channels.CompensationChannel;
import org.apache.ode.bpel.runtime.channels.EventHandlerControlChannel;
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
 * An active scope.
 */
public class SCOPE extends ACTIVITY {
	private static final long serialVersionUID = 6111903798996023525L;

	private static final Log __log = LogFactory.getLog(SCOPE.class);

	private OScope _oscope;
	private ActivityInfo _child;
	private Set<EventHandlerInfo> _eventHandlers = new HashSet<EventHandlerInfo>();

	String xpath_surrounding_scope = null;
	Long ID_surrounding_scope = null;
	ParentScopeChannel faultToScope;
	Set<OLink> LinksToSet = new HashSet<OLink>();
	FaultData _fault;

	// @hahnml: removed final modifier and moved initialization to the
	// constructors
	QName process_name;
	Long process_ID;

	/** Constructor. */
	public SCOPE(ActivityInfo self, ScopeFrame frame, LinkFrame linkFrame) {
		super(self, frame, linkFrame);
		_oscope = (OScope) self.o;
		process_name = getBpelRuntimeContext().getBpelProcess().getPID();
		process_ID = getBpelRuntimeContext().getPid();
		assert _oscope.activity != null;
	}

	// @hahnml: New constructor to create a SCOPE object without an active
	// JacobThread
	public SCOPE(ActivityInfo self, ScopeFrame frame, LinkFrame linkFrame,
			QName processname, Long pid) {
		super(self, frame, linkFrame, processname, pid);
		_oscope = (OScope) self.o;
		process_name = processname;
		process_ID = pid;
		assert _oscope.activity != null;
	}
	
	//@hahnml:
	/**
	 * @return The buffered final process id of this FLOW (getBpelRuntimeContext().getPid() returns the id of the currently running process instance!)
	 */
	public Long getBufferedProcess_ID() {
		return process_ID;
	}
	
	/**
	 * The child of a scope is usually null if fault handling is done for this scope.
	 * 
	 * @author sonntamo
	 */
	public boolean isChildNull() {
		return _child == null;
	}

	public void run() {
		// Create channels to get a fault or order from outside
		faultToScope = newChannel(ParentScopeChannel.class, "fault to scope");

		if (pFrame != null) {
			xpath_surrounding_scope = pFrame.oscope.getXpath();
			ID_surrounding_scope = pFrame.scopeInstanceId;
		}

		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 5754711188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					SCOPE.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					SCOPE.this.Complete_Activity();
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 75474656756L;

			public void terminate() {
				// Event Activity_Terminated
				ScopeActivityTerminated evt = new ScopeActivityTerminated(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						xpath_surrounding_scope, ID_surrounding_scope,
						process_name, process_ID, _self.o.getArt(), true,
						_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent.completed(null, CompensationHandler.emptySet());
			}
			
			public void skip() {
				ScopeActivitySkipped evt = new ScopeActivitySkipped(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						xpath_surrounding_scope, ID_surrounding_scope,
						process_name, process_ID, _self.o.getArt(), true,
						_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_skippedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent.completed(null, CompensationHandler.emptySet());
			}

		};

		object(false, (termChan).or(receiver));

		BpelRuntimeContextImpl runtime = (BpelRuntimeContextImpl) getBpelRuntimeContext();

		// Event Activity_Ready
		ScopeActivityReady evt = new ScopeActivityReady(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, xpath_surrounding_scope,
				ID_surrounding_scope, process_name, process_ID,
				_self.o.getArt(), true, _scopeFrame.scopeInstanceId, signal,
				_self.self, faultToScope, (OScope) _self.o, SCOPE.this,
				_scopeFrame.ignore, SCOPE.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void Complete_Activity() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);

		AwaitingMessage(signal);

		ScopeActivityExecuted evt = new ScopeActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, xpath_surrounding_scope,
				ID_surrounding_scope, process_name, process_ID,
				_self.o.getArt(), true, _scopeFrame.scopeInstanceId, signal,
				_scopeFrame.ignore);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void AwaitingMessage(LinkStatusChannel sig) {
		HashSet<ChannelListener> mlSet2 = new HashSet<ChannelListener>();
		final LinkStatusChannel signal = sig;

		mlSet2.add(new LinkStatusChannelListener(signal) {
			private static final long serialVersionUID = 457448855L;

			public void linkStatus(boolean value) {

				if (_oscope.compensationHandler != null) {
					CompensationHandler compensationHandler = new CompensationHandler(
							_scopeFrame, newChannel(CompensationChannel.class),
							System.currentTimeMillis(), System
									.currentTimeMillis());

					COMPENSATIONHANDLER_ compHandler = new COMPENSATIONHANDLER_(
							compensationHandler, CompensationHandler.emptySet());

					// Event Activity_Complete
					ScopeActivityComplete evt_complete = new ScopeActivityComplete(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							xpath_surrounding_scope, ID_surrounding_scope,
							process_name, process_ID, _self.o.getArt(), true,
							_scopeFrame.scopeInstanceId, compHandler,
							_scopeFrame.ignore);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt_complete);

					dpe(_self.o.outgoingLinks);
					_self.parent.completed(null,
							Collections.singleton(compensationHandler));
					instance(compHandler);
				} else /* no compensation handler */{

					// Event Activity_Complete
					ScopeActivityComplete evt_complete = new ScopeActivityComplete(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							xpath_surrounding_scope, ID_surrounding_scope,
							process_name, process_ID, _self.o.getArt(), true,
							_scopeFrame.scopeInstanceId, null,
							_scopeFrame.ignore);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt_complete);
					dpe(_self.o.outgoingLinks);
					_self.parent.completed(null, CompensationHandler.emptySet());
				}

			}

		});
		mlSet2.add(new TerminationChannelListener(_self.self) {
			private static final long serialVersionUID = 547545562L;

			public void terminate() {

				// Event Activity_Terminated
				ScopeActivityTerminated evt = new ScopeActivityTerminated(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						xpath_surrounding_scope, ID_surrounding_scope,
						process_name, process_ID, _self.o.getArt(), true,
						_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent.completed(null, CompensationHandler.emptySet());

			}
			//krawczls:
			public void skip() {
				ScopeActivitySkipped evt = new ScopeActivitySkipped(
						_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
						xpath_surrounding_scope, ID_surrounding_scope,
						process_name, process_ID, _self.o.getArt(), true,
						_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt);
				_terminatedActivity = true;
				dpe(_self.o.outgoingLinks);
				_self.parent.completed(null, CompensationHandler.emptySet());
			}

		});

		mlSet2.add(new ParentScopeChannelListener(faultToScope) {

			/**
			 * 
			 */
			private static final long serialVersionUID = 45747L;

			public void cancelled() {
				// Ignore
				AwaitingMessage(signal);
			}

			public void compensate(OScope scope, SynchChannel ret) {
				// Ignore
				AwaitingMessage(signal);
			}

			public void completed(FaultData faultData,
					Set<CompensationHandler> compensations) {
				_fault = faultData;
				// dpe the links leaving the scopes never executed child
				// construct
				dpe(_oscope.activity.outgoingLinks);

				LinksToSet = _self.o.outgoingLinks;
				LinksToSet.removeAll(_oscope.activity.outgoingLinks);

				ACTIVE tmp = new ACTIVE();
				tmp.Faulted();
			}

			public void failure(String reason, Element data) {
				// Ignore
				AwaitingMessage(signal);
			}

		});

		object(false, mlSet2);
	}

	public void execute() {
		// Event Activity_Executing
		ScopeActivityExecuting evt = new ScopeActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, xpath_surrounding_scope,
				ID_surrounding_scope, process_name, process_ID,
				_self.o.getArt(), true, _scopeFrame.scopeInstanceId,
				_scopeFrame.ignore);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		// Start the child activity.
		_child = new ActivityInfo(genMonotonic(), _oscope.activity, newChannel(
				TerminationChannel.class,
				"scope execute " + _oscope.activity.toString()), newChannel(
				ParentScopeChannel.class,
				"scope execute " + _oscope.activity.toString()));
		instance(createChild(_child, _scopeFrame, _linkFrame));

		if (_oscope.eventHandler != null) {
			for (Iterator<OEventHandler.OAlarm> i = _oscope.eventHandler.onAlarms
					.iterator(); i.hasNext();) {
				OEventHandler.OAlarm alarm = i.next();
				EventHandlerInfo ehi = new EventHandlerInfo(alarm,
						newChannel(EventHandlerControlChannel.class),
						newChannel(ParentScopeChannel.class, "scope execute "
								+ alarm.toString()), newChannel(
								TerminationChannel.class, "scope execute "
										+ alarm.toString()));
				_eventHandlers.add(ehi);
				instance(new EH_ALARM(ehi.psc, ehi.tc, ehi.cc, alarm,
						_scopeFrame));
			}

			for (Iterator<OEventHandler.OEvent> i = _oscope.eventHandler.onMessages
					.iterator(); i.hasNext();) {
				OEventHandler.OEvent event = i.next();
				EventHandlerInfo ehi = new EventHandlerInfo(event, newChannel(
						EventHandlerControlChannel.class, "scope execute "
								+ event.toString()), newChannel(
						ParentScopeChannel.class,
						"scope execute " + event.toString()),
						newChannel(TerminationChannel.class));
				_eventHandlers.add(ehi);
				instance(new EH_EVENT(ehi.psc, ehi.tc, ehi.cc, event,
						_scopeFrame));
			}
		}

		getBpelRuntimeContext().initializePartnerLinks(
				_scopeFrame.scopeInstanceId, _oscope.partnerLinks.values(), _scopeFrame, _self.o.getXpath());

		sendEvent(new ScopeStartEvent());
		instance(new ACTIVE());
	}

	private List<CompensationHandler> findCompensationData(OScope scope) {
		List<CompensationHandler> out = new ArrayList<CompensationHandler>();
		for (Iterator<CompensationHandler> i = _scopeFrame.availableCompensations
				.iterator(); i.hasNext();) {
			CompensationHandler ch = i.next();
			if (null == scope || ch.compensated.oscope.equals(scope))
				out.add(ch);
		}
		// sort out in terms of completion order
		Collections.sort(out);
		return out;
	}

	class ACTIVE extends ACTIVITY {
		private static final long serialVersionUID = -5876892592071965346L;
		/** Links collected. */
		private boolean _terminated;
		//krawczls:
		private boolean _skipped;
		private FaultData _fault;
		private long _startTime;
		private HashSet<CompensationHandler> _compensations = new HashSet<CompensationHandler>();
		private boolean _childTermRequested;
		private boolean _childSkipRequested;

		// Maintain a set of links needing dead-path elimination.
		Set<OLink> linksNeedingDPE = new HashSet<OLink>();
		Boolean fromOutsideReceived = false;

		ACTIVE() {
			super(SCOPE.this._self, SCOPE.this._scopeFrame,
					SCOPE.this._linkFrame);
			_startTime = System.currentTimeMillis();
		}

		public void run() {
			if (_child != null || !_eventHandlers.isEmpty()) {
				HashSet<ChannelListener> mlSet = new HashSet<ChannelListener>();

				// Listen to messages from our parent.
				mlSet.add(new TerminationChannelListener(_self.self) {
					private static final long serialVersionUID = 1913414844895865116L;

					public void terminate() {
						if (_terminated == false) {
							_terminated = true;

							// Forward the termination request to the nested
							// activity.
							if (_child != null && !_childTermRequested) {
								replication(_child.self).terminate();
								_childTermRequested = true;
							}

							// Forward the termination request to our event
							// handlers.
							terminateEventHandlers();
						}

						instance(ACTIVE.this);
					}
					
					//krawczls:
					public void skip() {
						if (_skipped == false) {
							_skipped = true;

							// Forward the skip request to the nested
							// activity.
							if (_child != null && !_childSkipRequested) {
								replication(_child.self).skip();
								_childSkipRequested = true;
							}

							// Forward the termination request to our event
							// handlers.
							skipEventHandlers();
						}

						instance(ACTIVE.this);
					}
				});

				// Handle messages from the child if it is still alive
				if (_child != null) {
					mlSet.add(new ParentScopeChannelListener(_child.parent) {
						private static final long serialVersionUID = -6934246487304813033L;

						public void compensate(OScope scope, SynchChannel ret) {
							// If this scope does not have available
							// compensations, defer to
							// parent scope, otherwise do compensation.
							if (_scopeFrame.availableCompensations == null)
								_self.parent.compensate(scope, ret);
							else {
								// TODO: Check if we are doing duplicate
								// compensation
								List<CompensationHandler> compensations = findCompensationData(scope);
								_scopeFrame.availableCompensations
										.removeAll(compensations);
								instance(new ORDEREDCOMPENSATOR(compensations,
										ret));
							}
							instance(ACTIVE.this);
						}

						public void completed(FaultData flt,
								Set<CompensationHandler> compensations) {
							// Set the fault to the activity's choice, if and
							// only if no
							// previous fault
							// has been detected (first fault wins).
							if (flt != null && _fault == null)
								_fault = flt;
							_child = null;
							_compensations.addAll(compensations);

							if (flt == null)
								stopEventHandlers();
							else
								terminateEventHandlers();

							instance(ACTIVE.this);
						}

						public void cancelled() {
							// Implicit scope holds links of the enclosed
							// activity,
							// they only get cancelled when we propagate
							// upwards.
							if (_oscope.implicitScope)
								_self.parent.cancelled();
							else
								completed(null, CompensationHandler.emptySet());
						}

						public void failure(String reason, Element data) {
							completed(
									createFault(
											OFailureHandling.FAILURE_FAULT_NAME,
											_self.o, null), CompensationHandler
											.emptySet());
						}

					});
				}
				if (!fromOutsideReceived) {
					mlSet.add(new ParentScopeChannelListener(faultToScope) {

						/**
					 * 
					 */
						private static final long serialVersionUID = 45747L;

						public void cancelled() {
							// Ignore
							instance(ACTIVE.this);
						}

						public void compensate(OScope scope, SynchChannel ret) {
							// Ignore
							instance(ACTIVE.this);
						}

						public void completed(FaultData faultData,
								Set<CompensationHandler> compensations) {
							fromOutsideReceived = true;
							if (faultData != null && _fault == null) {
								_fault = faultData;
							}
							// Terminate child if we get a fault from outside.
							if (_child != null && !_childTermRequested) {
								replication(_child.self).terminate();
								_childTermRequested = true;
							}
							terminateEventHandlers();

							instance(ACTIVE.this);
						}

						public void failure(String reason, Element data) {
							// Ignore
							instance(ACTIVE.this);
						}

					});
				}

				// Similarly, handle messages from the event handler, if one
				// exists
				// and if it has not completed.
				for (Iterator<EventHandlerInfo> i = _eventHandlers.iterator(); i
						.hasNext();) {
					final EventHandlerInfo ehi = i.next();

					mlSet.add(new ParentScopeChannelListener(ehi.psc) {
						private static final long serialVersionUID = -4694721357537858221L;

						public void compensate(OScope scope, SynchChannel ret) {
							// ACTIVE scopes do not compensate, send request up
							// to parent.
							_self.parent.compensate(scope, ret);
							instance(ACTIVE.this);
						}

						public void completed(FaultData flt,
								Set<CompensationHandler> compenstations) {
							// Set the fault to the activity's choice, if and
							// only if no
							// previous fault
							// has been detected (first fault wins).
							if (flt != null && _fault == null)
								_fault = flt;
							_eventHandlers.remove(ehi);
							_compensations.addAll(compenstations);

							if (flt != null) {
								// Terminate child if we get a fault from the
								// event handler.
								if (_child != null && !_childTermRequested) {
									replication(_child.self).terminate();
									_childTermRequested = true;
								}
								terminateEventHandlers();
							} else
								stopEventHandlers();

							instance(ACTIVE.this);
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
			} else /* nothing to wait for... */{
				// Any compensation handlers that were available but not
				// activated will
				// be forgotten.
				Set<CompensationHandler> unreachableCompensationHandlers = _scopeFrame.availableCompensations;
				if (unreachableCompensationHandlers != null)
					for (Iterator<CompensationHandler> i = unreachableCompensationHandlers
							.iterator(); i.hasNext();) {
						CompensationHandler ch = i.next();
						ch.compChannel.forget();
					}
				_scopeFrame.availableCompensations = null;

				if (_oscope.faultHandler != null)
					for (Iterator<OCatch> i = _oscope.faultHandler.catchBlocks
							.iterator(); i.hasNext();)
						linksNeedingDPE.addAll(i.next().outgoingLinks);

				// We're done with the main work, if we were terminated, we will
				// need to load the termination handler:
				if (_terminated) {
					__log.debug("Scope: " + _oscope + " was terminated.");
					// ??? Should we forward
					Handling_Termination();
				} else if (_fault != null) {
					Faulted();
				} else /* completed ok */
				{
					Activity_Complete();
				}

			}

		}

		public void Handling_Termination() {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 74567312117875L;

				public void linkStatus(boolean value) {
					ACTIVE.this.Terminated();
				}

			};
			object(false, receiver);

			// Event Scope_Handling_Termination
			ScopeHandlingTermination term_event = new ScopeHandlingTermination(
					_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
					xpath_surrounding_scope, ID_surrounding_scope,
					process_name, process_ID, _self.o.getArt(), true,
					_scopeFrame.scopeInstanceId, _scopeFrame.ignore, signal);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(term_event);
		}

		public void Terminated() {
			// Event Activity_Terminated
			ScopeActivityTerminated evt = new ScopeActivityTerminated(
					_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
					xpath_surrounding_scope, ID_surrounding_scope,
					process_name, process_ID, _self.o.getArt(), true,
					_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			dpe(linksNeedingDPE);
			_self.parent.completed(null, _compensations);
		}

		public void Faulted() {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 4777457875L;

				public void linkStatus(boolean value) {
					ACTIVE.this.HandlingFault();
				}

			};
			object(false, receiver);

			QName faultName = _fault.getFaultName();
			Element faultMessage = _fault.getFaultMessage();
			QName messageType = null;
			QName elementType = null;
			OVarType tmp = _fault.getFaultType();
			if (tmp != null) {
				if (tmp instanceof OMessageVarType) {
					OMessageVarType tmp2 = (OMessageVarType) tmp;
					messageType = tmp2.messageType;
					if (tmp2.docLitType != null) {
						elementType = tmp2.docLitType.elementType;
					}
				} else if (tmp instanceof OElementVarType) {
					OElementVarType tmp2 = (OElementVarType) tmp;
					elementType = tmp2.elementType;
				}
			}

			if (_fault.getElemType() != null) {
				elementType = _fault.getElemType();
			}
			if (_fault.getMessType() != null) {
				messageType = _fault.getMessType();
			}

			// Event Scope_Handling_Fault
			ScopeHandlingFault fault_event = new ScopeHandlingFault(
					_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
					xpath_surrounding_scope, ID_surrounding_scope,
					process_name, process_ID, _self.o.getArt(), true,
					_scopeFrame.scopeInstanceId, signal, faultName,
					faultMessage, messageType, elementType, _scopeFrame.ignore);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(fault_event);
		}

		public void Activity_Complete() {
			//@hahnml: This was missing here, to set the scope status to ScopeStateEnum.COMPLETED
			sendEvent(new ScopeCompletionEvent());
			
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);

			WaitForMessage(signal);

			ScopeActivityExecuted evt = new ScopeActivityExecuted(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, xpath_surrounding_scope,
					ID_surrounding_scope, process_name, process_ID,
					_self.o.getArt(), true, _scopeFrame.scopeInstanceId,
					signal, _scopeFrame.ignore);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		}

		public void WaitForMessage(LinkStatusChannel sig) {
			HashSet<ChannelListener> mlSet2 = new HashSet<ChannelListener>();
			final LinkStatusChannel signal = sig;

			mlSet2.add(new LinkStatusChannelListener(signal) {
				private static final long serialVersionUID = 6566448855L;

				public void linkStatus(boolean value) {

					if (_oscope.compensationHandler != null) {
						CompensationHandler compensationHandler = new CompensationHandler(
								_scopeFrame,
								newChannel(CompensationChannel.class),
								_startTime, System.currentTimeMillis());

						COMPENSATIONHANDLER_ compHandler = new COMPENSATIONHANDLER_(
								compensationHandler, _compensations);

						// Event Activity_Complete
						ScopeActivityComplete evt_complete = new ScopeActivityComplete(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								xpath_surrounding_scope, ID_surrounding_scope,
								process_name, process_ID, _self.o.getArt(),
								true, _scopeFrame.scopeInstanceId, compHandler,
								_scopeFrame.ignore);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt_complete);

						_self.parent.completed(null,
								Collections.singleton(compensationHandler));
						instance(compHandler);
					} else /* no compensation handler */{

						// Event Activity_Complete
						ScopeActivityComplete evt_complete = new ScopeActivityComplete(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								xpath_surrounding_scope, ID_surrounding_scope,
								process_name, process_ID, _self.o.getArt(),
								true, _scopeFrame.scopeInstanceId, null,
								_scopeFrame.ignore);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt_complete);

						_self.parent.completed(null, _compensations);
					}

					// DPE links needing DPE (i.e. the unselected catch blocks).
					dpe(linksNeedingDPE);

				}

			});
			mlSet2.add(new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = 8683675562L;

				public void terminate() {

					// Event Activity_Terminated
					ScopeActivityTerminated evt = new ScopeActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							xpath_surrounding_scope, ID_surrounding_scope,
							process_name, process_ID, _self.o.getArt(), true,
							_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					dpe(linksNeedingDPE);
					_self.parent.completed(null, _compensations);

				}
				
				//krawczls:
				public void skip() {
					ScopeActivitySkipped evt = new ScopeActivitySkipped(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							xpath_surrounding_scope, ID_surrounding_scope,
							process_name, process_ID, _self.o.getArt(), true,
							_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_skippedActivity = true;
					dpe(linksNeedingDPE);
					_self.parent.completed(null, _compensations);
				}

			});

			mlSet2.add(new ParentScopeChannelListener(faultToScope) {

				/**
 * 
 */
				private static final long serialVersionUID = 45747L;

				public void cancelled() {
					// Ignore
					WaitForMessage(signal);
				}

				public void compensate(OScope scope, SynchChannel ret) {
					// Ignore
					WaitForMessage(signal);
				}

				public void completed(FaultData faultData,
						Set<CompensationHandler> compensations) {
					_fault = faultData;
					Faulted();
				}

				public void failure(String reason, Element data) {
					// Ignore
					WaitForMessage(signal);
				}
			});

			object(false, mlSet2);
		}

		public void HandlingFault() {
			sendEvent(new ScopeFaultEvent(_fault.getFaultName(),
					_fault.getFaultLineNo(), _fault.getExplanation()));
			OCatch catchBlock;
			// Find a fault handler for our fault.
			if (_fault.getFromOutside()) {
				// differentiate, because if this fault comes from outside, some
				// of the
				// newly added attributes in FaultData are set
				QName faultName = _fault.getFaultName();
				QName messageType = _fault.getMessType();
				QName elementType = _fault.getElemType();

				catchBlock = _oscope.faultHandler == null ? null
						: findCatchNew(_oscope.faultHandler, faultName,
								messageType, elementType);
			} else {
				catchBlock = _oscope.faultHandler == null ? null : findCatch(
						_oscope.faultHandler, _fault.getFaultName(),
						_fault.getFaultType());
			}

			// Collect all the compensation data for completed child scopes.
			assert !!_eventHandlers.isEmpty();
			assert _child == null;
			if (catchBlock == null) {
				// If we cannot find a catch block for this fault, then we
				// simply
				// propagate the fault
				// to the parent. NOTE: the "default" fault handler as described
				// in the
				// BPEL spec
				// must be generated by the compiler.
				if (__log.isDebugEnabled())
					__log.debug(_self + ": has no fault handler for "
							+ _fault.getFaultName()
							+ "; scope will propagate FAULT!");
				if (_oscope.faultHandler == null) {
					FaultHandling_NoHandler();
				} else {
					Activity_Faulted(false);
				}
				// _self.parent.completed(_fault, _compensations);
			} else /* catchBlock != null */{
				if (__log.isDebugEnabled())
					__log.debug(_self + ": has a fault handler for "
							+ _fault.getFaultName() + ": " + catchBlock);

				// default catch block has no links
				linksNeedingDPE.removeAll(catchBlock.outgoingLinks);
				LinksToSet.removeAll(catchBlock.outgoingLinks);

				// We have to create a scope for the catch block.
				BpelRuntimeContext ntive = getBpelRuntimeContext();

				ActivityInfo faultHandlerActivity = new ActivityInfo(
						genMonotonic(), catchBlock, newChannel(
								TerminationChannel.class, "FH"), newChannel(
								ParentScopeChannel.class, "FH"));

				ScopeFrame faultHandlerScopeFrame = new ScopeFrame(catchBlock,
						ntive.createScopeInstance(_scopeFrame.scopeInstanceId,
								catchBlock), _scopeFrame, _compensations,
						_fault);
				faultHandlerScopeFrame.ignore = true;
				if (catchBlock.faultVariable != null) {
					try {
						VariableInstance vinst = faultHandlerScopeFrame
								.resolve(catchBlock.faultVariable);
						initializeVariable(vinst, _fault.getFaultMessage());

						// Generating event
						VariableModificationEvent se = new VariableModificationEvent(
								vinst.declaration.name, catchBlock.getXpath(),
								_self.aId, _scopeFrame.oscope.getXpath(),
								vinst.declaration.getXpath(),
								_scopeFrame.scopeInstanceId);
						se.setNewValue(_fault.getFaultMessage());
						if (_oscope.debugInfo != null)
							se.setLineNo(_oscope.debugInfo.startLine);
						sendEvent(se);
					} catch (Exception ex) {
						__log.fatal(ex);
						throw new InvalidProcessException(ex);
					}
				}

				// Create the fault handler scope.
				instance(new SCOPE(faultHandlerActivity,
						faultHandlerScopeFrame, SCOPE.this._linkFrame));

				object(new ParentScopeChannelListener(
						faultHandlerActivity.parent) {
					private static final long serialVersionUID = -6009078124717125270L;

					public void compensate(OScope scope, SynchChannel ret) {
						// This should never happen.
						throw new AssertionError("received compensate request!");
					}

					public void completed(FaultData fault,
							Set<CompensationHandler> compensations) {
						// The compensations that have been registered here,
						// will never be
						// activated,
						// so we'll forget them as soon as possible.
						for (Iterator<CompensationHandler> i = compensations
								.iterator(); i.hasNext();)
							i.next().compChannel.forget();

						if (fault != null) {
							_fault = fault;
							Activity_Faulted(true);
						} else {
							Scope_Complete_With_Fault();
						}
						// _self.parent.completed(fault,
						// CompensationHandler.emptySet());
					}

					public void cancelled() {
						completed(null, CompensationHandler.emptySet());
					}

					public void failure(String reason, Element data) {
						completed(null, CompensationHandler.emptySet());
					}
				});
			}
		}

		public void FaultHandling_NoHandler() {
			Activity_Faulted(false);
			/*
			 * LinkStatusChannel signalX = newChannel(LinkStatusChannel.class);
			 * LinkStatusChannelListener receiverX = new
			 * LinkStatusChannelListener(signalX) { private static final long
			 * serialVersionUID = 5461112222111875L; public void
			 * linkStatus(boolean value) { if (value) //no Handler available for
			 * Scope, not even in another fragment for example {
			 * ACTIVE.this.Activity_Faulted(false); } else //fault was handled
			 * in another scope fragment, for example {
			 * ACTIVE.this.Scope_Complete_With_Fault(); } }
			 * 
			 * }; object(false, receiverX);
			 * 
			 * FaultHandlingNoHandler evt = new
			 * FaultHandlingNoHandler(_self.o.getXpath(), _self.aId,
			 * xpath_surrounding_scope, ID_surrounding_scope, process_name,
			 * process_ID, _self.o.getArt(), true, _scopeFrame.scopeInstanceId,
			 * signalX, _scopeFrame.ignore);
			 * getBpelRuntimeContext().getBpelProcess
			 * ().getEngine().fireEvent(evt);
			 */
		}

		public void Activity_Faulted(Boolean Handler) {
			final Boolean bool = Handler;
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 5657675L;

				public void linkStatus(boolean value) {

					if (value) // continue received
					{
						if (bool) {
							_self.parent.completed(_fault,
									CompensationHandler.emptySet());
						} else {
							_self.parent.completed(_fault, _compensations);
						}
					} else // suppress_fault received
					{
						if (bool) {
							_terminatedActivity = true;
							_self.parent.completed(null,
									CompensationHandler.emptySet());
						} else {
							_terminatedActivity = true;
							_self.parent.completed(null, _compensations);
						}
					}

					// DPE links needing DPE (i.e. the unselected catch blocks).
					dpe(linksNeedingDPE);
					dpe(LinksToSet);
				}

			};
			object(false, receiver);

			// Event Activity_Faulted
			ScopeActivityFaulted evt2 = new ScopeActivityFaulted(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, xpath_surrounding_scope,
					ID_surrounding_scope, process_name, process_ID,
					_self.o.getArt(), true, _scopeFrame.scopeInstanceId,
					signal, _scopeFrame.ignore, _fault);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}

		public void Scope_Complete_With_Fault() {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 78987675L;

				public void linkStatus(boolean value) {
					_self.parent
							.completed(null, CompensationHandler.emptySet());
					// DPE links needing DPE (i.e. the unselected catch blocks).
					dpe(linksNeedingDPE);
					dpe(LinksToSet);
				}

			};
			object(false, receiver);

			// Event Scope_Complete_With_Fault
			ScopeCompleteWithFault evt2 = new ScopeCompleteWithFault(
					_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
					xpath_surrounding_scope, ID_surrounding_scope,
					process_name, process_ID, _self.o.getArt(), true,
					_scopeFrame.scopeInstanceId, signal, _scopeFrame.ignore);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}
		
		//krawczls: added skipRequested
		private void terminateEventHandlers() {
			for (Iterator<EventHandlerInfo> i = _eventHandlers.iterator(); i
					.hasNext();) {
				EventHandlerInfo ehi = i.next();
				if (!ehi.terminateRequested && !ehi.stopRequested && !ehi.skipRequested) {
					replication(ehi.tc).terminate();
					ehi.terminateRequested = true;
				}
			}
		}
		
		//krawczls:
		private void skipEventHandlers() {
			for (Iterator<EventHandlerInfo> i = _eventHandlers.iterator(); i
				.hasNext();) {
				EventHandlerInfo ehi = i.next();
				if (!ehi.terminateRequested && !ehi.stopRequested && !ehi.skipRequested) {
					replication(ehi.tc).skip();
					ehi.skipRequested = true;
				}
			}
		}
		
		private void stopEventHandlers() {
			for (Iterator<EventHandlerInfo> i = _eventHandlers.iterator(); i
					.hasNext();) {
				EventHandlerInfo ehi = i.next();
				if (!ehi.stopRequested && !ehi.terminateRequested && !ehi.skipRequested) {
					ehi.cc.stop();
					ehi.stopRequested = true;
				}
			}
		}

	}

	private static OCatch findCatch(OFaultHandler fh, QName faultName,
			OVarType faultType) {
		OCatch bestMatch = null;
		for (OCatch c : fh.catchBlocks) {
			// First we try to eliminate this catch block based on fault-name
			// mismatches:
			if (c.faultName != null) {
				if (faultName == null)
					continue;
				if (!faultName.equals(c.faultName))
					continue;
			}

			// Then we try to eliminate this catch based on type
			// incompatibility:
			if (c.faultVariable != null) {
				if (faultType == null)
					continue;
				else if (c.faultVariable.type instanceof OMessageVarType) {
					if (faultType instanceof OMessageVarType
							&& ((OMessageVarType) faultType)
									.equals(c.faultVariable.type)) {
						// Don't eliminate.
					} else if (faultType instanceof OElementVarType
							&& ((OMessageVarType) c.faultVariable.type).docLitType != null
							&& !((OMessageVarType) c.faultVariable.type).docLitType
									.equals(faultType)) {
						// Don't eliminate.
					} else {
						continue; // Eliminate.
					}
				} else if (c.faultVariable.type instanceof OElementVarType) {
					if (faultType instanceof OElementVarType
							&& faultType.equals(c.faultVariable.type)) {
						// Don't eliminate
					} else if (faultType instanceof OMessageVarType
							&& ((OMessageVarType) faultType).docLitType != null
							&& ((OMessageVarType) faultType).docLitType
									.equals(c.faultVariable.type)) {
						// Don't eliminate
					} else {
						continue; // eliminate
					}
				} else {
					continue; // Eliminate
				}
			}

			// If we got to this point we did not eliminate this catch block.
			// However,
			// we don't just
			// use the first non-eliminated catch, we instead try to find the
			// best
			// match.
			if (bestMatch == null) {
				// Obviously something is better then nothing.
				bestMatch = c;
			} else {
				// Otherwise we prefer name and variable matches but prefer
				// name-only
				// matches to
				// variable-only matches.
				int existingScore = (bestMatch.faultName == null ? 0 : 2)
						+ (bestMatch.faultVariable == null ? 0 : 1);
				int currentScore = (c.faultName == null ? 0 : 2)
						+ (c.faultVariable == null ? 0 : 1);
				if (currentScore > existingScore) {
					bestMatch = c;
				}
			}
		}
		return bestMatch;
	}

	private static OCatch findCatchNew(OFaultHandler fh, QName faultName,
			QName messageType, QName elementType) {
		OCatch bestMatch = null;
		for (Iterator<OCatch> i = fh.catchBlocks.iterator(); i.hasNext();) {
			OCatch c = i.next();

			// First we try to eliminate this catch block based on fault-name
			// mismatches:
			if (c.faultName != null) {
				if (faultName == null)
					continue;
				if (!faultName.equals(c.faultName))
					continue;
			}

			// Then we try to eliminate this catch based on type
			// incompatibility:
			if (c.faultVariable != null) {
				if (messageType == null && elementType == null)
					continue;
				else if (c.faultVariable.type instanceof OMessageVarType
						&& messageType != null) {
					OMessageVarType tmp = (OMessageVarType) c.faultVariable.type;
					if (tmp.messageType.equals(messageType)) {
						// Don't eliminate.
					} else if (elementType != null && tmp.docLitType != null) {
						if (tmp.docLitType.elementType.equals(elementType)) {
							// Don't eliminate
						} else {
							continue;
						}
					} else {
						continue; // Eliminate.
					}
				} else if (c.faultVariable.type instanceof OElementVarType
						&& elementType != null) {
					OElementVarType tmp = (OElementVarType) c.faultVariable.type;
					if (tmp.elementType.equals(elementType)) {
						// Don't eliminate
					} else {
						continue; // eliminate
					}
				} else {
					continue; // Eliminate
				}
			}

			// If we got to this point we did not eliminate this catch block.
			// However,
			// we don't just
			// use the first non-eliminated catch, we instead try to find the
			// best
			// match.
			if (bestMatch == null) {
				// Obviously something is better then nothing.
				bestMatch = c;
			} else {
				// Otherwise we prefer name and variable matches but prefer
				// name-only
				// matches to
				// variable-only matches.
				int existingScore = (bestMatch.faultName == null ? 0 : 2)
						+ (bestMatch.faultVariable == null ? 0 : 1);
				int currentScore = (c.faultName == null ? 0 : 2)
						+ (c.faultVariable == null ? 0 : 1);
				if (currentScore > existingScore) {
					bestMatch = c;
				}
			}
		}
		return bestMatch;
	}

	static final class EventHandlerInfo implements Serializable {
		private static final long serialVersionUID = -9046603073542446478L;
		final OBase o;
		final EventHandlerControlChannel cc;
		final ParentScopeChannel psc;
		final TerminationChannel tc;
		boolean terminateRequested;
		boolean skipRequested;
		boolean stopRequested;

		EventHandlerInfo(OBase o, EventHandlerControlChannel cc,
				ParentScopeChannel psc, TerminationChannel tc) {
			this.o = o;
			this.cc = cc;
			this.psc = psc;
			this.tc = tc;
		}
	}

}
