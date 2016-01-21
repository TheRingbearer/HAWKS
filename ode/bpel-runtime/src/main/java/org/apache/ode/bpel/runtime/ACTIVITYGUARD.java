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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.engine.iteration.RepetitionAndJumpHelper;
import org.apache.ode.bpel.evt.ActivityEnabledEvent;
import org.apache.ode.bpel.evt.ActivityExecEndEvent;
import org.apache.ode.bpel.evt.ActivityExecStartEvent;
import org.apache.ode.bpel.evt.ActivityFailureEvent;
import org.apache.ode.bpel.evt.ActivityRecoveryEvent;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.events.ActivityJoinFailure;
import org.apache.ode.bpel.extensions.events.ActivitySkipped;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.extensions.events.EvaluatingTransitionConditionFaulted;
import org.apache.ode.bpel.extensions.events.LinkEvaluated;
import org.apache.ode.bpel.extensions.events.LinkReady;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OExpression;
import org.apache.ode.bpel.o.OFailureHandling;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OInvoke;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.ActivityRecoveryChannel;
import org.apache.ode.bpel.runtime.channels.ActivityRecoveryChannelListener;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannel;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannelListener;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.w3c.dom.Element;

public class ACTIVITYGUARD extends ACTIVITY {
	private static final long serialVersionUID = 1L;

	private static final Log __log = LogFactory.getLog(ACTIVITYGUARD.class);

	private static final ActivityTemplateFactory __activityTemplateFactory = new ActivityTemplateFactory();
	private OActivity _oactivity;

	/** Link values. */
	private Map<OLink, Boolean> _linkVals = new HashMap<OLink, Boolean>();

	/** Flag to prevent duplicate ActivityEnabledEvents */
	private boolean _firstTime = true;

	private ActivityFailure _failure;

	private ACTIVITY _child;

	FaultData _fault;

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	public ACTIVITYGUARD(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
		_oactivity = self.o;
	}

	public void run() {
		// Send a notification of the activity being enabled,
		if (_firstTime) {
			sendEvent(new ActivityEnabledEvent());
			_firstTime = false;
		}

		// @hahnml: Restore the link status for all crossing links from the db
		if (RepetitionAndJumpHelper.getInstance().isFlowIteration()) {
			for (OLink link : _oactivity.targetLinks) {
				if (RepetitionAndJumpHelper.getInstance().getCrossingLinks()
						.contains(link)) {

					// Query the link status from the database
					LinkDAO dao = getBpelRuntimeContext()
							.getProcessInstanceDao().getLink(link.getId());

					switch (dao.getState()) {
					case TRUE:
						// Put "true" to the map
						_linkVals.put(link, Boolean.TRUE);
						break;
					case FALSE:
						// Put "false" to the map
						_linkVals.put(link, Boolean.FALSE);
						break;
					case NOT_EVALUATED:
						// Do nothing
						break;
					default:
						// Do nothing
						break;
					}
					
					// Remove the link from the list to avoid duplicate execution
					RepetitionAndJumpHelper.getInstance().getCrossingLinks().remove(link);
				}
			}
		}

		if (_linkVals.keySet().containsAll(_oactivity.targetLinks)) {
			if (evaluateJoinCondition()) {
				ActivityExecStartEvent aese = new ActivityExecStartEvent();
				sendEvent(aese);
				// intercept completion channel in order to execute transition
				// conditions.
				// AH:
				ActivityInfo activity = _self.clone(genMonotonic());
				activity.parent = newChannel(ParentScopeChannel.class,
						"created in ACTIVITYGUARD " + activity.o.toString());
				/*
				 * ActivityInfo activity = new ActivityInfo(genMonotonic(),
				 * _self.o, _self.self, newChannel(ParentScopeChannel.class));
				 */

				// AH: end
				_child = createActivity(activity);
				instance(_child);
				instance(new TCONDINTERCEPT(activity.parent));
			} else {
				ACTIVITYGUARD.this.ActivityJoinFailure();
			}
		} else /* don't know all our links statuses */{
			Set<ChannelListener> mlset = new HashSet<ChannelListener>();
			mlset.add(new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = 5094153128476008961L;

				public void terminate() {
					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(),
							_self.aId, sFrame.oscope.getXpath(),
							sFrame.scopeInstanceId, process_name, process_ID,
							_self.o.getArt(), (_self.o instanceof OScope));
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);

					// Complete immediately, without faulting or registering any
					// comps.
					_self.parent.completed(null, CompensationHandler.emptySet());
					// Dead-path activity
					dpe(_oactivity.sourceLinks);
					dpe(_oactivity.outgoingLinks);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
				}

			});
			for (final OLink link : _oactivity.targetLinks) {
				mlset.add(new LinkStatusChannelListener(_linkFrame
						.resolve(link).sub) {
					private static final long serialVersionUID = 1024137371118887935L;

					public void linkStatus(boolean value) {
						_linkVals.put(link, Boolean.valueOf(value));
						instance(ACTIVITYGUARD.this);
					}
				});
			}

			object(false, mlset);
		}
	}

	public void ActivityJoinFailure() {
		// AH:
		final ActivityInfo activity = _self.clone(genMonotonic());
		activity.parent = newChannel(ParentScopeChannel.class,
				"ActivityJoinFailure");
		/*
		 * final ActivityInfo activity = new ActivityInfo(genMonotonic(),
		 * _self.o, _self.self, newChannel(ParentScopeChannel.class));
		 */
		// AH: end

		if (_oactivity.suppressJoinFailure) {
			_self.parent.completed(null, CompensationHandler.emptySet());

			if (__log.isDebugEnabled())
				__log.debug("Join condition false, suppress join failure on activity "
						+ _self.aId);

			// Dead path activity.
			dpe(_oactivity, activity);
		} else {
			_fault = createFault(_oactivity.getOwner().constants.qnJoinFailure,
					_oactivity);

			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 64242354358875L;

				public void linkStatus(boolean value) {
					if (value) // continue received
					{
						_self.parent.completed(_fault,
								CompensationHandler.emptySet());
					} else // Suppress_Fault received
					{
						_self.parent.completed(null,
								CompensationHandler.emptySet());
					}
					// Dead-path activity
					dpe(_oactivity.sourceLinks);
					dpe(_oactivity.outgoingLinks);
				}

			};
			object(false, receiver);

			// Event Activity_Join_Failure
			ActivityJoinFailure evt = new ActivityJoinFailure(_self.o.name,
					_self.o.getId(), _self.o.getXpath(), activity.aId,
					sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
					process_name, process_ID, _self.o.getArt(),
					(_self.o instanceof OScope), _fault, signal,
					_oactivity.suppressJoinFailure);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		}

	}

	private boolean evaluateTransitionCondition(OExpression transitionCondition)
			throws FaultException {
		if (transitionCondition == null)
			return true;

		try {
			return getBpelRuntimeContext().getExpLangRuntime()
					.evaluateAsBoolean(
							transitionCondition,
							new ExprEvaluationContextImpl(_scopeFrame,
									getBpelRuntimeContext()));
		} catch (EvaluationException e) {
			String msg = "Error in transition condition detected at runtime; condition="
					+ transitionCondition;
			__log.error(msg, e);
			throw new InvalidProcessException(msg, e);
		}
	}

	/**
	 * Evaluate an activity's join condition.
	 * 
	 * @return <code>true</code> if join condition evaluates to true.
	 */
	private boolean evaluateJoinCondition() {
		// For activities with no link targets, the join condition is always
		// satisfied.
		if (_oactivity.targetLinks.size() == 0)
			return true;

		// For activities with no join condition, an OR condition is assumed.
		if (_oactivity.joinCondition == null)
			return _linkVals.values().contains(Boolean.TRUE);

		try {
			return getBpelRuntimeContext()
					.getExpLangRuntime()
					.evaluateAsBoolean(
							_oactivity.joinCondition,
							new ExprEvaluationContextImpl(null, null, _linkVals));
		} catch (Exception e) {
			String msg = "Unexpected error evaluating a join condition: "
					+ _oactivity.joinCondition;
			__log.error(msg, e);
			throw new InvalidProcessException(msg, e);
		}
	}

	private static ACTIVITY createActivity(ActivityInfo activity,
			ScopeFrame scopeFrame, LinkFrame linkFrame) {
		return __activityTemplateFactory.createInstance(activity.o, activity,
				scopeFrame, linkFrame);
	}

	private ACTIVITY createActivity(ActivityInfo activity) {
		return createActivity(activity, _scopeFrame, _linkFrame);
	}

	private void startGuardedActivity() {
		// AH:
		ActivityInfo activity = _self.clone(genMonotonic());
		activity.parent = newChannel(ParentScopeChannel.class,
				"start guarded act " + activity.o.toString());
		/*
		 * ActivityInfo activity = new ActivityInfo(genMonotonic(), _self.o,
		 * _self.self, newChannel(ParentScopeChannel.class));
		 */
		// AH: end
		instance(createActivity(activity));
		instance(new TCONDINTERCEPT(activity.parent));
	}

	/**
	 * Intercepts the {@link
	 * ParentScopeChannel#completed(org.apache.ode.bpel.runtime.channels.
	 * FaultData,
	 * java.util.Set<org.apache.ode.bpel.runtime.CompensationHandler>)} call, to
	 * evaluate transition conditions before returning to the parent.
	 */
	private class TCONDINTERCEPT extends BpelJacobRunnable {
		private static final long serialVersionUID = 4014873396828400441L;
		ParentScopeChannel _in;
		Set<OLink> LinksToDPE = new HashSet<OLink>();
		OLink tmp_link;

		public TCONDINTERCEPT(ParentScopeChannel in) {
			_in = in;

			// @hahnml: Set the OBase id
			oId = ACTIVITYGUARD.this.oId;
		}

		public void evaluatingTransitionConditionFaulted(
				Set<CompensationHandler> compensations) {
			// AH:
			final ActivityInfo activity = _self.clone(genMonotonic());
			activity.parent = newChannel(ParentScopeChannel.class,
					"TCONDINTERCEPT " + activity.o.toString());
			/*
			 * final ActivityInfo activity = new ActivityInfo(genMonotonic(),
			 * _self.o, _self.self, newChannel(ParentScopeChannel.class));
			 */
			// AH: end
			final Set<CompensationHandler> compens = compensations;

			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 64242354358875L;

				public void linkStatus(boolean value) {
					if (value) // Continue received
					{
						_self.parent.completed(_fault, compens);

						LinkInfo linfo = _linkFrame.resolve(tmp_link);
						LinkEvaluated link_event = new LinkEvaluated(
								tmp_link.getXpath(), tmp_link.getId(),
								tmp_link.name, sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, tmp_link.source.getXpath(),
								tmp_link.target.getXpath(), false, linfo.pub);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(link_event);

						// @hahnml: Update the status in the LinkDAO
						LinkDAO dao = getBpelRuntimeContext()
								.getProcessInstanceDao().getLink(
										tmp_link.getId());
						dao.setState(LinkStateEnum.FALSE);

						dpe(LinksToDPE);
					} else // Suppress_Fault received
					{
						_self.parent.completed(null, compens);
						dpe(LinksToDPE);
					}
				}

			};
			object(false, receiver);

			EvaluatingTransitionConditionFaulted evt = new EvaluatingTransitionConditionFaulted(
					_self.o.name, _self.o.getId(), _self.o.getXpath(),
					activity.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), (_self.o instanceof OScope), signal,
					_fault, tmp_link.getXpath());
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		}

		public void run() {
			object(new ParentScopeChannelListener(_in) {
				private static final long serialVersionUID = 2667359535900385952L;

				public void compensate(OScope scope, SynchChannel ret) {
					_self.parent.compensate(scope, ret);
					instance(TCONDINTERCEPT.this);
				}

				public void completed(FaultData faultData,
						Set<CompensationHandler> compensations) {
					sendEvent(new ActivityExecEndEvent());
					// only in case of faultData == null, we need to wait with
					// informing the parent
					// of completion, because evaluation of transitionCondition
					// may fault

					// @hahnml: Check if the parent activity is a OFlow and if
					// the model was migrated
					if (_self.o.getParent() instanceof OFlow
							&& ProcessModelChangeRegistry.getRegistry()
									.isModelChanged()) {
						// Check if the flow is handled already
						if (!ProcessModelChangeRegistry.getRegistry()
								.isHandled(_self.o.getParent())) {
							OFlow oflow = (OFlow) _self.o.getParent();

							// Get the FLOW from the ActivityEventHandler
							FLOW flow = ActivityEventHandler.getInstance()
									.getRunningFlow(
											getBpelRuntimeContext().getPid(),
											oflow.getXpath());

							if (flow != null) {
								// Create a new linkFrame with all added links
								LinkFrame myLinkFrame = new LinkFrame(
										_linkFrame);
								for (Iterator<OLink> i = oflow.localLinks
										.iterator(); i.hasNext();) {
									OLink link = i.next();
									// Check if the link is added during
									// migration and not handled yet
									if (ProcessModelChangeRegistry
											.getRegistry()
											.isAddedAndNotHandled(link)) {
										LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
										myLinkFrame.links.put(link,
												new LinkInfo(link, lsc, lsc));

										// Mark the link as handled
										ProcessModelChangeRegistry
												.getRegistry().setHandled(link);

										// @hahnml: Create a new LinkDAO for the
										// added link
										getBpelRuntimeContext()
												.createLinkInstance(link);
									}
								}

								// Clear the list of original children to store
								// only the new child activities
								flow._children.clear();

								// Schedule all added activities to instantiate
								// them
								for (Iterator<OActivity> i = oflow.parallelActivities
										.iterator(); i.hasNext();) {
									OActivity ochild = i.next();
									// Check if the activity is added during
									// migration and not handled yet
									if (ProcessModelChangeRegistry
											.getRegistry()
											.isAddedAndNotHandled(ochild)) {
										ChildInfo childInfo = new ChildInfo(
												new ActivityInfo(
														genMonotonic(),
														ochild,
														newChannel(TerminationChannel.class),
														newChannel(ParentScopeChannel.class)));

										// Add the new child to the flow
										flow._children.add(childInfo);

										instance(createChild(
												childInfo.activity,
												_scopeFrame, myLinkFrame));

										// Mark the activity as handled
										ProcessModelChangeRegistry
												.getRegistry().setHandled(
														ochild);
									}
								}

								// Update the LinkFrame
								flow._linkFrame = myLinkFrame;

								// Update the InstanceMigrationDAO
								getBpelRuntimeContext().getBpelProcess()
										.getProcessDAO()
										.getInstance(process_ID)
										.getInstanceMigrationDAO()
										.setSuspended(false);

								/*
								 * @hahnml: The migration flag has to remain
								 * unchanged ("true"), so that other activities
								 * (like other sequences or flows) also execute
								 * their migration logic.
								 */
								// getBpelRuntimeContext().getBpelProcess()
								// .getProcessDAO()
								// .getInstance(process_ID)
								// .getInstanceMigrationDAO()
								// .setMigrated(false);

								// @hahnml: Instead of setting the migration
								// flag to "false" we register this activity as
								// handled.
								ProcessModelChangeRegistry.getRegistry()
										.setHandled(oflow);
							}
						}
					}

					_fault = faultData;
					if (_child._terminatedActivity) {
						dpe(_oactivity.sourceLinks);
						_self.parent.completed(_fault, compensations);
					} else {
						if (_fault != null) {
							dpe(_oactivity.sourceLinks);
							_self.parent.completed(_fault, compensations);
						} else {
							for (Iterator<OLink> i = _oactivity.sourceLinks
									.iterator(); i.hasNext();) {
								OLink olink = i.next();
								if (_fault == null) {
									LinkInfo linfo = _linkFrame.resolve(olink);

									// @hahnml: If the link is added during
									// migration and could not resolved the
									// LinkFrame is outdated and has to be
									// updated.
									if (linfo == null
											&& ProcessModelChangeRegistry
													.getRegistry().isHandled(
															olink)) {
										OFlow oflow = olink.declaringFlow;

										// Get the FLOW from the
										// ActivityEventHandler
										FLOW flow = ActivityEventHandler
												.getInstance().getRunningFlow(
														getBpelRuntimeContext()
																.getPid(),
														oflow.getXpath());

										// Get the updated LinkFrame from the
										// FLOW and update the local LinkFrame
										_linkFrame = flow._linkFrame;

										// Resolve the LinkInfo from the updated
										// LinkFrame
										linfo = _linkFrame.resolve(olink);
									}

									// Link_Ready
									LinkReady lnk_event = new LinkReady(
											olink.getXpath(), olink.getId(),
											olink.name,
											sFrame.oscope.getXpath(),
											sFrame.scopeInstanceId,
											process_name, process_ID,
											olink.source.getXpath(),
											olink.target.getXpath());
									getBpelRuntimeContext().getBpelProcess()
											.getEngine().fireEvent(lnk_event);

									try {
										boolean val = evaluateTransitionCondition(olink.transitionCondition);
										// Link_Evaluated
										LinkEvaluated link_event = new LinkEvaluated(
												olink.getXpath(),
												olink.getId(), olink.name,
												sFrame.oscope.getXpath(),
												sFrame.scopeInstanceId,
												process_name, process_ID,
												olink.source.getXpath(),
												olink.target.getXpath(), val,
												linfo.pub);
										getBpelRuntimeContext()
												.getBpelProcess().getEngine()
												.fireEvent(link_event);

										// @hahnml: Update the status in the
										// LinkDAO
										LinkDAO dao = getBpelRuntimeContext()
												.getProcessInstanceDao()
												.getLink(olink.getId());
										dao.setState(val ? LinkStateEnum.TRUE
												: LinkStateEnum.FALSE);
										// linfo.pub.linkStatus(val);
									} catch (FaultException e) {
										// Link_Evaluated
										/*
										 * LinkEvaluated link_event = new
										 * LinkEvaluated(olink.getXpath(),
										 * olink.name, sFrame.oscope.getXpath(),
										 * sFrame.scopeInstanceId, process_name,
										 * process_ID, olink.source.getXpath(),
										 * olink.target.getXpath(), false,
										 * linfo.pub);
										 * getBpelRuntimeContext().getBpelProcess
										 * ().getEngine().fireEvent(link_event);
										 */
										// linfo.pub.linkStatus(false);
										__log.error(e);
										if (_fault == null)
											_fault = createFault(e.getQName(),
													olink.transitionCondition);
										tmp_link = olink;
									}
								}

								else {
									LinksToDPE.add(olink);
								}

							}
							if (_fault == null) {
								_self.parent.completed(_fault, compensations);
							} else {
								TCONDINTERCEPT.this
										.evaluatingTransitionConditionFaulted(compensations);
							}
						}
					}
				}

				public void cancelled() {
					sendEvent(new ActivityExecEndEvent());
					dpe(_oactivity.outgoingLinks);
					dpe(_oactivity.sourceLinks);
					// Implicit scope can tell the difference between cancelled
					// and completed.
					_self.parent.cancelled();
				}

				private OFailureHandling getFailureHandling() {
					if (_oactivity instanceof OInvoke) {
						OInvoke _oinvoke = (OInvoke) _oactivity;
						OFailureHandling f = getBpelRuntimeContext()
								.getConfigForPartnerLink(_oinvoke.partnerLink).failureHandling;
						if (f != null)
							return f;
					}
					return _oactivity.getFailureHandling();
				}

				public void failure(String reason, Element data) {
					if (_failure == null)
						_failure = new ActivityFailure();
					_failure.dateTime = new Date();
					_failure.reason = reason;
					_failure.data = data;

					OFailureHandling failureHandling = getFailureHandling();
					if (failureHandling != null
							&& failureHandling.faultOnFailure
							&& _failure.retryCount >= failureHandling.retryFor) {
						// Fault after retries (may be 0)
						if (__log.isDebugEnabled())
							__log.debug("ActivityRecovery: Activity "
									+ _self.aId + " faulting on failure");
						FaultData faultData = createFault(
								OFailureHandling.FAILURE_FAULT_NAME,
								_oactivity, reason);
						completed(faultData, CompensationHandler.emptySet());
						return;
					}
					if (failureHandling == null
							|| _failure.retryCount >= failureHandling.retryFor) {
						requireRecovery();
						return;
					}

					if (__log.isDebugEnabled())
						__log.debug("ActivityRecovery: Retrying activity "
								+ _self.aId);
					Date future = new Date(new Date().getTime()
							+ (failureHandling == null ? 0L
									: failureHandling.retryDelay * 1000));
					final TimerResponseChannel timerChannel = newChannel(TimerResponseChannel.class);
					getBpelRuntimeContext().registerTimer(timerChannel, future,
							null);
					object(false,
							new TimerResponseChannelListener(timerChannel) {
								private static final long serialVersionUID = -261911108068231376L;

								public void onTimeout() {
									++_failure.retryCount;
									startGuardedActivity();
								}

								public void onCancel() {
									requireRecovery();
								}
							});
				}

				private void requireRecovery() {
					if (__log.isDebugEnabled())
						__log.debug("ActivityRecovery: Activity " + _self.aId
								+ " requires recovery");
					sendEvent(new ActivityFailureEvent(_failure.reason));
					final ActivityRecoveryChannel recoveryChannel = newChannel(ActivityRecoveryChannel.class);
					getBpelRuntimeContext().registerActivityForRecovery(
							recoveryChannel, _self.aId, _failure.reason,
							_failure.dateTime, _failure.data,
							new String[] { "retry", "cancel", "fault" },
							_failure.retryCount);
					object(false, new ActivityRecoveryChannelListener(
							recoveryChannel) {
						private static final long serialVersionUID = 8397883882810521685L;

						public void retry() {
							if (__log.isDebugEnabled())
								__log.debug("ActivityRecovery: Retrying activity "
										+ _self.aId + " (user initiated)");
							sendEvent(new ActivityRecoveryEvent("retry"));
							getBpelRuntimeContext()
									.unregisterActivityForRecovery(
											recoveryChannel);
							++_failure.retryCount;
							startGuardedActivity();
						}

						public void cancel() {
							if (__log.isDebugEnabled())
								__log.debug("ActivityRecovery: Cancelling activity "
										+ _self.aId + " (user initiated)");
							sendEvent(new ActivityRecoveryEvent("cancel"));
							getBpelRuntimeContext()
									.unregisterActivityForRecovery(
											recoveryChannel);
							cancelled();
						}

						public void fault(FaultData faultData) {
							if (__log.isDebugEnabled())
								__log.debug("ActivityRecovery: Faulting activity "
										+ _self.aId + " (user initiated)");
							sendEvent(new ActivityRecoveryEvent("fault"));
							getBpelRuntimeContext()
									.unregisterActivityForRecovery(
											recoveryChannel);
							if (faultData == null)
								faultData = createFault(
										OFailureHandling.FAILURE_FAULT_NAME,
										_self.o, _failure.reason);
							completed(faultData, CompensationHandler.emptySet());
						}
					}.or(new TerminationChannelListener(_self.self) {
						private static final long serialVersionUID = 2148587381204858397L;

						public void terminate() {
							if (__log.isDebugEnabled())
								__log.debug("ActivityRecovery: Cancelling activity "
										+ _self.aId + " (terminated by scope)");
							getBpelRuntimeContext()
									.unregisterActivityForRecovery(
											recoveryChannel);
							cancelled();
						}
						
						//krwczk: TODO -implement skip
						public void skip() {
							
						}
					}));
				}
			});

		}
	}

	static class ActivityFailure implements Serializable {
		private static final long serialVersionUID = 1L;

		Date dateTime;
		String reason;
		Element data;
		int retryCount;
	}

}
