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

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.events.ScopeEventHandlingEnded;
import org.apache.ode.bpel.extensions.events.ScopeHandlingEvent;
import org.apache.ode.bpel.o.OEventHandler;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.EventHandlerControlChannel;
import org.apache.ode.bpel.runtime.channels.EventHandlerControlChannelListener;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannel;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannelListener;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.w3c.dom.Element;

/**
 * Alarm event handler. This process template manages a single alarm event
 * handler. It acts like an activity in that it can be terminated, but also adds
 * a channel for "soft" termination (aka stopping) to deal with the case when
 * the scope that owns the event handler completes naturally.
 */
class EH_ALARM extends BpelJacobRunnable {

	private static final Log __log = LogFactory.getLog(EH_ALARM.class);

	private static final long serialVersionUID = 1L;

	private ParentScopeChannel _psc;
	private TerminationChannel _tc;
	private OEventHandler.OAlarm _oalarm;
	private ScopeFrame _scopeFrame;
	private EventHandlerControlChannel _cc;
	private Set<CompensationHandler> _comps = new HashSet<CompensationHandler>();
	String xpath_surrounding_scope;
	Long ID_surrounding_scope;
	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	private ScopeFrame sFrame;
	private ScopeFrame pFrame;

	/**
	 * Concretion constructor.
	 * 
	 * @param psc
	 *            a link to our parent.
	 * @param tc
	 *            channel we listen on for termination requests
	 * @param cc
	 *            channel we listen on for "stop" requests
	 * @param o
	 *            our prototype / compiled representation
	 * @param scopeFrame
	 *            the {@link ScopeFrame} in which we are executing
	 */
	EH_ALARM(ParentScopeChannel psc, TerminationChannel tc,
			EventHandlerControlChannel cc, OEventHandler.OAlarm o,
			ScopeFrame scopeFrame) {
		_psc = psc;
		_tc = tc;
		_cc = cc;
		_scopeFrame = scopeFrame;
		_oalarm = o;
		
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

		Calendar alarm = Calendar.getInstance();

		if (_oalarm.forExpr != null)
			try {
				getBpelRuntimeContext()
						.getExpLangRuntime()
						.evaluateAsDuration(_oalarm.forExpr,
								getEvaluationContext()).addTo(alarm);
			} catch (EvaluationException e) {
				throw new InvalidProcessException(e);
			} catch (FaultException e) {
				__log.error(e);
				// @hahnml: Set the message of the exception to the FaultData object
				_psc.completed(createFault(e.getQName(), _oalarm.forExpr, e.getMessage()),
						_comps);
				return;
			}
		else if (_oalarm.untilExpr != null)
			try {
				alarm.setTime(getBpelRuntimeContext()
						.getExpLangRuntime()
						.evaluateAsDate(_oalarm.untilExpr,
								getEvaluationContext()).getTime());
			} catch (EvaluationException e) {
				throw new InvalidProcessException(e);
			} catch (FaultException e) {
				__log.error(e);
				_psc.completed(createFault(e.getQName(), _oalarm.untilExpr),
						_comps);
				return;
			}

		// We reduce to waiting for the alarm to be triggered.
		instance(new WAIT(alarm));
	}

	protected EvaluationContext getEvaluationContext() {
		return new ExprEvaluationContextImpl(_scopeFrame,
				getBpelRuntimeContext());
	}

	/**
	 * Template used to wait until a given time, reduing to a {@link FIRE} after
	 * the elapsed time. This template also monitors the termination and
	 * event-control channels for requests from parent.
	 */
	private class WAIT extends BpelJacobRunnable {
		private static final long serialVersionUID = -1426724996925898213L;
		Calendar _alarm;

		/**
		 * Concretion constructor.
		 * 
		 * @param alarm
		 *            date at which time to fire. If null, then we wait forever
		 *            (for control channels handling)
		 */
		WAIT(Calendar alarm) {
			_alarm = alarm;
			
			// @hahnml: Set the OBase id
			oId = EH_ALARM.this.oId;
		}

		public void run() {
			Calendar now = Calendar.getInstance();

			Set<ChannelListener> listeners = new EventHandlerControlChannelListener(
					_cc) {
				private static final long serialVersionUID = -7750428941445331236L;

				public void stop() {
					_psc.completed(null, _comps);
				}

			}.or(new TerminationChannelListener(_tc) {
				private static final long serialVersionUID = 6100105997983514609L;

				public void terminate() {
					_psc.completed(null, _comps);
				}
				//krwczk: TODO -implement skip
				public void skip() {
	
				}
			});

			if (_alarm == null) {
				object(false, listeners);
			} else if (now.before(_alarm)) {
				TimerResponseChannel trc = newChannel(TimerResponseChannel.class);
				getBpelRuntimeContext().registerTimer(trc, _alarm.getTime(),
						null);

				listeners.add(new TimerResponseChannelListener(trc) {
					private static final long serialVersionUID = 1110683632756756017L;

					public void onTimeout() {
						// This is what we are waiting for, fire the activity
						// Event Scope_Handling_Event
						ScopeHandlingEvent evt = new ScopeHandlingEvent(
								_scopeFrame.oscope.name, _scopeFrame.oscope.getId(), _scopeFrame.oscope
										.getXpath(), null,
								xpath_surrounding_scope, ID_surrounding_scope,
								process_name, process_ID, false, true,
								_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						instance(new FIRE());
					}

					public void onCancel() {
						_psc.completed(null, _comps);
					}
				});
				object(false, listeners);
			} else /* now is later then alarm time */{
				// If the alarm has passed we fire the nested activity
				ActivityInfo child = new ActivityInfo(genMonotonic(),
						_oalarm.activity, newChannel(TerminationChannel.class),
						newChannel(ParentScopeChannel.class));
				instance(createChild(child, _scopeFrame, new LinkFrame(null)));
				instance(new ACTIVE(child));
			}

		}
	}

	/**
	 * Snipped that fires the alarm activity.
	 */
	private class FIRE extends BpelJacobRunnable {
		private static final long serialVersionUID = -7261315204412433250L;
		
		FIRE() {
			// @hahnml: Set the OBase id
			oId = EH_ALARM.this.oId;
		}

		public void run() {
			// Start the child activity.
			ActivityInfo child = new ActivityInfo(genMonotonic(),
					_oalarm.activity, newChannel(TerminationChannel.class),
					newChannel(ParentScopeChannel.class));
			instance(createChild(child, _scopeFrame, new LinkFrame(null)));
			instance(new ACTIVE(child));
		}
	}

	/**
	 * Snippet that is used to monitor a running activity.
	 */
	private class ACTIVE extends BpelJacobRunnable {
		private static final long serialVersionUID = -2166253425722769701L;

		private ActivityInfo _activity;

		/** Indicates whether our parent has requested a stop. */
		private boolean _stopped = false;

		ACTIVE(ActivityInfo activity) {
			_activity = activity;
			
			//@hahnml: Set the OBase id
			oId = EH_ALARM.this.oId;
		}

		public void run() {
			object(false, new ParentScopeChannelListener(_activity.parent) {
				private static final long serialVersionUID = -3357030137175178040L;

				public void compensate(OScope scope, SynchChannel ret) {
					_psc.compensate(scope, ret);
					instance(ACTIVE.this);
				}

				public void completed(FaultData faultData,
						Set<CompensationHandler> compensations) {
					// Event Scope_Event_Handling_Ended
					if (!_stopped) {
						ScopeEventHandlingEnded evt = new ScopeEventHandlingEnded(
								_scopeFrame.oscope.name, _scopeFrame.oscope.getId(), _scopeFrame.oscope
										.getXpath(), null,
								xpath_surrounding_scope, ID_surrounding_scope,
								process_name, process_ID, false, true,
								_scopeFrame.scopeInstanceId, _scopeFrame.ignore);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
					}

					_comps.addAll(compensations);
					if (!_stopped && _oalarm.repeatExpr != null) {
						Calendar next = Calendar.getInstance();
						try {
							getBpelRuntimeContext()
									.getExpLangRuntime()
									.evaluateAsDuration(_oalarm.repeatExpr,
											getEvaluationContext()).addTo(next);
						} catch (EvaluationException e) {
							throw new InvalidProcessException(e);
						} catch (FaultException e) {
							__log.error(e);
							_psc.completed(
									createFault(e.getQName(), _oalarm.forExpr),
									_comps);
							return;
						}
						instance(new WAIT(next));
					} else {
						if (faultData != null) {
							// propagate completion into bounding scope only if
							// we got fault during processing onAlarm
							_psc.completed(faultData, _comps);
						} else {
							instance(new WAIT(null));
						}
					}
				}

				public void cancelled() {
					completed(null, CompensationHandler.emptySet());
				}

				public void failure(String reason, Element data) {
					completed(null, CompensationHandler.emptySet());
				}
			}.or(new EventHandlerControlChannelListener(_cc) {
				private static final long serialVersionUID = -3873619538789039424L;

				public void stop() {
					_stopped = true;
					instance(ACTIVE.this);
				}

			}.or(new TerminationChannelListener(_tc) {
				private static final long serialVersionUID = -4566956567870652885L;

				public void terminate() {
					replication(_activity.self).terminate();
					_stopped = true;
					instance(ACTIVE.this);
				}
				//krwczk: TODO -implement skip
				public void skip() {

				}
			})));

		}
	}
}
