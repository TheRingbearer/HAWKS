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
import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.bpel.common.CorrelationKeySet;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.evt.PartnerLinkModificationEvent;
import org.apache.ode.bpel.evt.VariableModificationEvent;
import org.apache.ode.bpel.extensions.events.ScopeEventHandlingEnded;
import org.apache.ode.bpel.extensions.events.ScopeHandlingEvent;
import org.apache.ode.bpel.o.OEventHandler;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.EventHandlerControlChannel;
import org.apache.ode.bpel.runtime.channels.EventHandlerControlChannelListener;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.PickResponseChannel;
import org.apache.ode.bpel.runtime.channels.PickResponseChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Message event handler.
 */
class EH_EVENT extends BpelJacobRunnable {

	private static final long serialVersionUID = 1L;

	private static final Log __log = LogFactory.getLog(EH_EVENT.class);

	private EventHandlerControlChannel _ehc;
	private TerminationChannel _tc;
	private ParentScopeChannel _psc;
	private ScopeFrame _scopeFrame;
	private OEventHandler.OEvent _oevent;
	
	//krwczk: TODO -check synchActivity and ModificationEvent
	private Boolean _synchronizedActivity = false;

	String xpath_surrounding_scope;
	Long ID_surrounding_scope;
	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	private ScopeFrame sFrame;
	private ScopeFrame pFrame;

	/** Registered compensation handlers. */
	private Set<CompensationHandler> _comps = new HashSet<CompensationHandler>();

	private FaultData _fault;

	/** Active instances (we can have more than one!) */
	private Set<ActivityInfo> _active = new HashSet<ActivityInfo>();

	/** Whether a stop has been requested; if so no more new instances. */
	private boolean _stopped;

	/** Has a termination of this handler been requested. */
	private boolean _terminated;

	private boolean _childrenTerminated;

	EH_EVENT(ParentScopeChannel psc, TerminationChannel tc,
			EventHandlerControlChannel ehc, OEventHandler.OEvent o,
			ScopeFrame scopeFrame) {
		_scopeFrame = scopeFrame;
		_oevent = o;
		_tc = tc;
		_psc = psc;
		_ehc = ehc;
		
		//@hahnml: Set the id to the JacobObject
		this.oId = o.getId();
		
		getFrames();
	}

	public void getFrames() {
		ScopeFrame s = _scopeFrame;
		sFrame = _scopeFrame;
		while (s != null) {
			if (!s.ignore) {
				sFrame = s;
				break;
			}
			s = s.parent;
		}

		pFrame = s.parent;
		ScopeFrame p = s.parent;
		while (p != null) {
			if (!p.ignore) {
				pFrame = p;
				break;
			}
			p = p.parent;
		}

	}

	public void run() {
		if (pFrame == null) {
			xpath_surrounding_scope = null;
			ID_surrounding_scope = null;
		} else {
			xpath_surrounding_scope = pFrame.oscope.getXpath();
			ID_surrounding_scope = pFrame.scopeInstanceId;
		}
		instance(new SELECT());
	}

	/**
	 * Terminate all the active activities.
	 */
	private void terminateActive() {
		if (!_childrenTerminated) {
			for (ActivityInfo tact : _active) {
				replication(tact.self).terminate();
			}
			_childrenTerminated = true;
		}
	}

	/**
	 * Template that does the actual selection interaction with the runtime
	 * system, and then waits on the pick response channel.
	 */
	class SELECT extends BpelJacobRunnable {

		private static final long serialVersionUID = 1L;
		
		SELECT() {
			// @hahnml: Set the OBase id
			oId = EH_EVENT.this.oId;
		}

		/**
		 * @see org.apache.ode.jacob.JacobRunnable#run()
		 */
		public void run() {
			Selector selector;
			try {
				PickResponseChannel pickResponseChannel = newChannel(PickResponseChannel.class);
				CorrelationKeySet keySet = new CorrelationKeySet();
				PartnerLinkInstance pLinkInstance = _scopeFrame
						.resolve(_oevent.partnerLink);
				for (OScope.CorrelationSet cset : _oevent.joinCorrelations) {
					if (getBpelRuntimeContext().isCorrelationInitialized(
							_scopeFrame.resolve(cset))) {
						keySet.add(getBpelRuntimeContext().readCorrelation(
								_scopeFrame.resolve(cset)));
					}
				}
				for (OScope.CorrelationSet cset : _oevent.matchCorrelations) {
					if (!getBpelRuntimeContext().isCorrelationInitialized(
							_scopeFrame.resolve(cset))) {
						throw new FaultException(
								_oevent.getOwner().constants.qnCorrelationViolation,
								"Correlation not initialized.");
					}
					keySet.add(getBpelRuntimeContext().readCorrelation(
							_scopeFrame.resolve(cset)));
				}
				if (keySet.isEmpty()) {
					// Adding a route for opaque correlation. In this case
					// correlation is done on "out-of-band" session id.
					String sessionId = getBpelRuntimeContext()
							.fetchMySessionId(pLinkInstance);
					keySet.add(new CorrelationKey("-1",
							new String[] { sessionId }));
				}

				selector = new Selector(0, pLinkInstance,
						_oevent.operation.getName(),
						_oevent.operation.getOutput() == null,
						_oevent.messageExchangeId, keySet, _oevent.route);
				getBpelRuntimeContext().select(pickResponseChannel, null,
						false, new Selector[] { selector });
				instance(new WAITING(pickResponseChannel));
			} catch (FaultException e) {
				__log.error(e);
				if (_fault == null) {
					// @hahnml: Set the message of the exception to the FaultData object
					_fault = createFault(e.getQName(), _oevent, e.getMessage());
				}
				terminateActive();
				instance(new WAITING(null));
			}
		}
	}

	/**
	 * Template that represents the waiting for a pick response.
	 */
	private class WAITING extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;
		private PickResponseChannel _pickResponseChannel;

		private WAITING(PickResponseChannel pickResponseChannel) {
			_pickResponseChannel = pickResponseChannel;
			
			// @hahnml: Set the OBase id
			oId = EH_EVENT.this.oId;
		}

		@SuppressWarnings("unchecked")
		public void run() {

			if (!_active.isEmpty() || _pickResponseChannel != null) {
				HashSet<ChannelListener> mlset = new HashSet<ChannelListener>();

				if (!_terminated) {
					mlset.add(new TerminationChannelListener(_tc) {
						private static final long serialVersionUID = 7666910462948788042L;

						public void terminate() {
							terminateActive();
							_terminated = true;
							if (_pickResponseChannel != null)
								getBpelRuntimeContext().cancel(
										_pickResponseChannel);
							instance(WAITING.this);
						}
						//krwczk: TODO -implement skip
						public void skip() {
							
						}
					});

				}

				if (!_stopped) {
					mlset.add(new EventHandlerControlChannelListener(_ehc) {
						private static final long serialVersionUID = -1050788954724647970L;

						public void stop() {
							_stopped = true;
							if (_pickResponseChannel != null)
								getBpelRuntimeContext().cancel(
										_pickResponseChannel);
							instance(WAITING.this);
						}
					});

				}

				for (final ActivityInfo ai : _active) {
					mlset.add(new ParentScopeChannelListener(ai.parent) {
						private static final long serialVersionUID = 5341207762415360982L;

						public void compensate(OScope scope, SynchChannel ret) {
							_psc.compensate(scope, ret);
							instance(WAITING.this);
						}

						public void completed(FaultData faultData,
								Set<CompensationHandler> compensations) {
							_active.remove(ai);
							_comps.addAll(compensations);
							if (_fault == null && !_stopped && !_terminated) {
								// Event Scope_Event_Handling_Ended
								ScopeEventHandlingEnded evt = new ScopeEventHandlingEnded(
										_scopeFrame.oscope.name, _scopeFrame.oscope.getId(),
										_scopeFrame.oscope.getXpath(), null,
										xpath_surrounding_scope,
										ID_surrounding_scope, process_name,
										process_ID, false, true,
										_scopeFrame.scopeInstanceId,
										_scopeFrame.ignore);
								getBpelRuntimeContext().getBpelProcess()
										.getEngine().fireEvent(evt);
							}
							if (faultData != null && _fault == null) {
								_fault = faultData;
								terminateActive();
								// ODE-511; needs to clean up the route
								if (_pickResponseChannel != null)
									getBpelRuntimeContext().cancel(
											_pickResponseChannel);
								_psc.completed(_fault, _comps);
							} else
								instance(WAITING.this);
						}

						public void cancelled() {
							completed(null, CompensationHandler.emptySet());
						}

						public void failure(String reason, Element data) {
							completed(null, CompensationHandler.emptySet());
						}
					});
				}

				if (_pickResponseChannel != null)
					mlset.add(new PickResponseChannelListener(
							_pickResponseChannel) {
						private static final long serialVersionUID = -4929999153478677288L;

						public void onRequestRcvd(int selectorIdx, String mexId) {
							// The receipt of the message causes a new scope to
							// be created:
							ScopeFrame ehScopeFrame = new ScopeFrame(
									_oevent,
									getBpelRuntimeContext()
											.createScopeInstance(
													_scopeFrame.scopeInstanceId,
													_oevent), _scopeFrame,
									_comps, _fault);

							if (_oevent.variable != null) {
								Element msgEl = getBpelRuntimeContext()
										.getMyRequest(mexId);

								if (msgEl != null) {
									try {
										VariableInstance vinst = ehScopeFrame
												.resolve(_oevent.variable);
										getBpelRuntimeContext().writeVariable(
												vinst, msgEl);

										VariableModificationEvent se = new VariableModificationEvent(
												vinst.declaration.name, _oevent
														.getXpath(),
												_scopeFrame.scopeInstanceId,
												_scopeFrame.oscope.getXpath(),
												vinst.declaration.getXpath(),
												_scopeFrame.scopeInstanceId);
										se.setNewValue(msgEl);
										_scopeFrame.fillEventInfo(se);
										if (_oevent.debugInfo != null)
											se.setLineNo(_oevent.debugInfo.startLine);
										getBpelRuntimeContext().sendEvent(se);
									} catch (Exception ex) {
										__log.fatal(ex);
										throw new InvalidProcessException(ex);
									}
								}
							}

							try {
								for (OScope.CorrelationSet cset : _oevent.initCorrelations) {
									// @hahnml: CHECK if
									// _oevent.activity.getXpath() returns the
									// correct XPATH
									initializeCorrelation(ehScopeFrame
											.resolve(cset), ehScopeFrame
											.resolve(_oevent.variable),
											_oevent.activity.getXpath());
								}
								for (OScope.CorrelationSet cset : _oevent.joinCorrelations) {
									// will be ignored if already initialized
									// @hahnml: CHECK if
									// _oevent.activity.getXpath() returns the
									// correct XPATH
									initializeCorrelation(ehScopeFrame
											.resolve(cset), ehScopeFrame
											.resolve(_oevent.variable),
											_oevent.activity.getXpath());
								}

								if (_oevent.partnerLink.hasPartnerRole()) {
									// Trying to initialize partner epr based on
									// a message-provided epr/session.
									if (!getBpelRuntimeContext()
											.isPartnerRoleEndpointInitialized(
													ehScopeFrame
															.resolve(_oevent.partnerLink))
											|| !_oevent.partnerLink.initializePartnerRole) {
										Node fromEpr = getBpelRuntimeContext()
												.getSourceEPR(mexId);
										if (fromEpr != null) {
											getBpelRuntimeContext()
													.writeEndpointReference(
															ehScopeFrame
																	.resolve(_oevent.partnerLink),
															(Element) fromEpr);
											
											//@author sonntamo
											PartnerLinkModificationEvent plme = new PartnerLinkModificationEvent(
													_oevent.partnerLink.name,
													_oevent.partnerLink.getXpath(),
													fromEpr,
													_oevent.getXpath(),
													_scopeFrame.oscope.getXpath(),
													_scopeFrame.scopeInstanceId);
											_scopeFrame.fillEventInfo(plme);
											getBpelRuntimeContext().sendEvent(plme);
										}
									}

									String partnersSessionId = getBpelRuntimeContext()
											.getSourceSessionId(mexId);
									if (partnersSessionId != null)
										getBpelRuntimeContext()
												.initializePartnersSessionId(
														ehScopeFrame
																.resolve(_oevent.partnerLink),
														partnersSessionId);
								}

								getBpelRuntimeContext()
										.cancelOutstandingRequests(
												_pickResponseChannel.export());
								// this request is now waiting for a reply
								getBpelRuntimeContext()
										.processOutstandingRequest(
												_scopeFrame
														.resolve(_oevent.partnerLink),
												_oevent.operation.getName(),
												_oevent.messageExchangeId,
												mexId);

							} catch (FaultException e) {
								__log.error(e);
								if (_fault == null) {
									// @hahnml: Set the message of the exception to the FaultData object
									_fault = createFault(e.getQName(), _oevent, e.getMessage());
									terminateActive();
								}
								instance(new WAITING(null));
								return;
							}

							// Event Scope_Handling_Event
							if (!_terminated && !_stopped
									&& !_childrenTerminated) {
								ScopeHandlingEvent evt = new ScopeHandlingEvent(
										_scopeFrame.oscope.name, _scopeFrame.oscope.getId(),
										_scopeFrame.oscope.getXpath(), null,
										xpath_surrounding_scope,
										ID_surrounding_scope, process_name,
										process_ID, false, true,
										_scopeFrame.scopeInstanceId,
										_scopeFrame.ignore);
								getBpelRuntimeContext().getBpelProcess()
										.getEngine().fireEvent(evt);
							}

							// load 'onMessage' activity; we'll do this even if
							// a stop/terminate has been
							// requested becasue we cannot undo the receipt of
							// the message at this point.
							ActivityInfo child = new ActivityInfo(
									genMonotonic(), _oevent.activity,
									newChannel(TerminationChannel.class),
									newChannel(ParentScopeChannel.class));

							_active.add(child);

							LinkFrame lf = new LinkFrame(null);

							ScopeFrame innerScopeFrame = new ScopeFrame(
									(OScope) _oevent.activity,
									getBpelRuntimeContext()
											.createScopeInstance(
													_scopeFrame.scopeInstanceId,
													(OScope) _oevent.activity),
									ehScopeFrame, _comps, _fault);
							instance(new SCOPE(child, innerScopeFrame, lf));

							// If we previously terminated the other activiites,
							// then we do the same
							// here; this is easier then undoing the receive.
							if (_childrenTerminated)
								replication(child.self).terminate();

							if (_terminated || _stopped || _fault != null)
								instance(new WAITING(null));
							else
								instance(new SELECT());
						}

						public void onTimeout() {
							instance(new WAITING(null));
						}

						public void onCancel() {
							instance(new WAITING(null));
						}
					});

				object(false, mlset);
			} else /* Nothing more to do. */{
				_psc.completed(_fault, _comps);
			}
		}

	}
}
