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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.engine.DebuggerSupport;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.extensions.events.LinkEvaluated;
import org.apache.ode.bpel.extensions.events.LinkReady;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OCatch;
import org.apache.ode.bpel.o.OExpression;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OLink;
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
import org.apache.ode.utils.stl.FilterIterator;
import org.apache.ode.utils.stl.MemberOfFunction;
import org.w3c.dom.Element;

public class FLOW extends ACTIVITY {
	private static final long serialVersionUID = 1L;

	private static final Log __log = LogFactory.getLog(FLOW.class);

	private OFlow _oflow;

	// @hahnml
	public FLOW.ACTIVE _active;

	// @hahnml: Changed visibility to public
	public Set<ChildInfo> _children = new HashSet<ChildInfo>();

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	public FLOW(ActivityInfo self, ScopeFrame frame, LinkFrame linkFrame) {
		super(self, frame, linkFrame);
		_oflow = (OFlow) self.o;
	}

	// @hahnml:
	/**
	 * @return The buffered final process id of this FLOW
	 *         (getBpelRuntimeContext().getPid() returns the id of the currently
	 *         running process instance!)
	 */
	public Long getBufferedProcess_ID() {
		return process_ID;
	}

	public void run() {
		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 102415681188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					FLOW.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					dpe(_self.o.outgoingLinks);
					FLOW.this.Activity_Completed(null,
							CompensationHandler.emptySet(), false);
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 1980436L;

			public void terminate() {
				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name,
						_self.o.getId(), _self.o.getXpath(), _self.aId,
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
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal, _self.self, FLOW.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name,
				_self.o.getId(), _self.o.getXpath(), _self.aId,
				sFrame.oscope.getXpath(), sFrame.scopeInstanceId, process_name,
				process_ID, _self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		LinkFrame myLinkFrame = new LinkFrame(_linkFrame);
		for (Iterator<OLink> i = _oflow.localLinks.iterator(); i.hasNext();) {
			OLink link = i.next();
			LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
			myLinkFrame.links.put(link, new LinkInfo(link, lsc, lsc));

			// @hahnml: Create a new LinkDAO
			getBpelRuntimeContext().createLinkInstance(link);
		}

		// @hahnml: Update the _linkFrame
		_linkFrame = myLinkFrame;

		for (Iterator<OActivity> i = _oflow.parallelActivities.iterator(); i
				.hasNext();) {
			OActivity ochild = i.next();
			ChildInfo childInfo = new ChildInfo(new ActivityInfo(
					genMonotonic(), ochild,
					newChannel(TerminationChannel.class),
					newChannel(ParentScopeChannel.class)));
			_children.add(childInfo);

			instance(createChild(childInfo.activity, _scopeFrame, myLinkFrame));
		}

		// @hahnml: Reference ACTIVE as local variable
		_active = new ACTIVE();
		instance(_active);

		// @hahnml: Add the FLOW to the ActivityEventHandler
		ActivityEventHandler.getInstance().addRunningFLOW(this);
	}

	// @hahnml: Changed visibility to public
	public class ACTIVE extends BpelJacobRunnable {
		private static final long serialVersionUID = -8494641460279049245L;
		private FaultData _fault;
		private HashSet<CompensationHandler> _compensations = new HashSet<CompensationHandler>();

		private Boolean terminated = false;

		public ACTIVE() {
			// @hahnml: Set the OBase id
			oId = FLOW.this.oId;
		}

		public void run() {
			// @hahnml: Update the buffered running flow at ActivityEventHandler
			// to ensure the correct execution of following iterations
			// ActivityEventHandler.getInstance().updateRunningFLOW(FLOW.this);

			// @hahnml: Update the LinkFrame and the children set if the flow is
			// migrated, handled and not updated yet
			if (getBpelRuntimeContext().getBpelProcess().getProcessDAO()
					.getInstance(process_ID).getInstanceMigrationDAO()
					.isMigrated()
					&& ProcessModelChangeRegistry.getRegistry().isHandled(
							_oflow)
					&& !ProcessModelChangeRegistry.getRegistry().isUpdated(
							_oflow)) {
				FLOW tmp = ActivityEventHandler.getInstance().getRunningFlow(
						process_ID, _oflow.getXpath());

				// Add all new ChildInfo objects. The _children set of the FLOW
				// stored at the ActivityEventHandler only contains the added
				// child activities.
				for (ChildInfo info : tmp._children) {
					FLOW.this._children.add(info);
				}

				// If the LinkFrame of the updated flow contains any new links
				// update the local LinkFrame
				if (!tmp._linkFrame.links.isEmpty()) {
					FLOW.this._linkFrame = tmp._linkFrame;
				}

				ProcessModelChangeRegistry.getRegistry().setUpdated(_oflow,
						true);
			}

			Iterator<ChildInfo> active = active();
			if (active.hasNext()) {
				Set<ChannelListener> mlSet = new HashSet<ChannelListener>();
				mlSet.add(new TerminationChannelListener(_self.self) {
					private static final long serialVersionUID = 2554750258974084466L;

					public void terminate() {
						if (!terminated) {
							for (Iterator<ChildInfo> i = active(); i.hasNext();)
								replication(i.next().activity.self).terminate();

							terminated = true;
						}
						instance(ACTIVE.this);

						// @hahnml: Update the buffered running flow at
						// ActivityEventHandler
						// to ensure the correct execution of following
						// iterations
						// ActivityEventHandler.getInstance().updateRunningFLOW(FLOW.this);
					}
					
					//krwczk: TODO -implement skip
					public void skip() {
						
					}
				});

				for (; active.hasNext();) {
					final ChildInfo child = active.next();
					mlSet.add(new ParentScopeChannelListener(
							child.activity.parent) {
						private static final long serialVersionUID = -8027205709169238172L;

						public void completed(FaultData faultData,
								Set<CompensationHandler> compensations) {
							child.completed = true;
							_compensations.addAll(compensations);

							// If we receive a fault, we request termination of
							// all our activities
							if (faultData != null && _fault == null) {
								for (Iterator<ChildInfo> i = active(); i
										.hasNext();)
									replication(i.next().activity.self)
											.terminate();
								_fault = faultData;
							}
							instance(ACTIVE.this);

							// @hahnml: Update the buffered running flow at
							// ActivityEventHandler
							// to ensure the correct execution of following
							// iterations
							// ActivityEventHandler.getInstance().updateRunningFLOW(FLOW.this);
						}

						public void compensate(OScope scope, SynchChannel ret) {
							// Flow does not do compensations, forward these to
							// parent.
							_self.parent.compensate(scope, ret);
							instance(ACTIVE.this);

							// @hahnml: Update the buffered running flow at
							// ActivityEventHandler
							// to ensure the correct execution of following
							// iterations
							// ActivityEventHandler.getInstance().updateRunningFLOW(FLOW.this);
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
			} else /** No More active children. */
			{
				// @hahnml: Get data from ode_instance_migration table
				boolean runOutofWork = getBpelRuntimeContext().getBpelProcess()
						.getProcessDAO().getInstance(process_ID)
						.getInstanceMigrationDAO().isSuspended();
				boolean wasMigrated = getBpelRuntimeContext().getBpelProcess()
						.getProcessDAO().getInstance(process_ID)
						.getInstanceMigrationDAO().isMigrated();
				boolean letItFinish = getBpelRuntimeContext().getBpelProcess()
						.getProcessDAO().getInstance(process_ID)
						.getInstanceMigrationDAO().isFinished();

				// @hahnml: Check if the instance was migrated and the flow is
				// not handled yet
				if (wasMigrated
						&& !ProcessModelChangeRegistry.getRegistry().isHandled(
								_oflow)) {
					// Collect all added links
					List<OLink> addedLinks = new ArrayList<OLink>();

					// Create a new linkFrame with all added links
					LinkFrame myLinkFrame = new LinkFrame(_linkFrame);
					for (Iterator<OLink> i = _oflow.localLinks.iterator(); i
							.hasNext();) {
						OLink link = i.next();
						// Check if the link is added during migration
						if (ProcessModelChangeRegistry.getRegistry()
								.isAddedAndNotHandled(link)) {
							LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
							myLinkFrame.links.put(link, new LinkInfo(link, lsc,
									lsc));

							addedLinks.add(link);

							// Mark the link as handled
							ProcessModelChangeRegistry.getRegistry()
									.setHandled(link);

							// @hahnml: Create a new LinkDAO for the new link
							getBpelRuntimeContext().createLinkInstance(link);
						}
					}

					// @hahnml: Update the _linkFrame
					_linkFrame = myLinkFrame;

					// Collect all added activities
					List<OActivity> addedActivities = new ArrayList<OActivity>();

					// Schedule all added activities to instantiate them
					for (Iterator<OActivity> i = _oflow.parallelActivities
							.iterator(); i.hasNext();) {
						OActivity ochild = i.next();
						// Check if the activity is added during migration
						if (ProcessModelChangeRegistry.getRegistry()
								.isAddedAndNotHandled(ochild)) {
							ChildInfo childInfo = new ChildInfo(
									new ActivityInfo(
											genMonotonic(),
											ochild,
											newChannel(TerminationChannel.class),
											newChannel(ParentScopeChannel.class)));
							_children.add(childInfo);

							instance(createChild(childInfo.activity,
									_scopeFrame, myLinkFrame));

							// Mark the activity as handled
							ProcessModelChangeRegistry.getRegistry()
									.setHandled(ochild);

							addedActivities.add(ochild);
						}
					}

					FaultData linkFault = null;

					// Calculate and set the initial link status for all links
					// connecting completed and inserted activities.
					for (OLink link : addedLinks) {
						// Check if the source of the link is not an added
						// activity and if the target of the link is one
						if (!addedActivities.contains(link.source)
								&& addedActivities.contains(link.target)) {
							// Get the status of the source activity
							OActivity act = link.source;
							org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus status = ActivityEventHandler
									.getInstance().getActivityStatus(
											act.getXpath(), process_ID);

							if (status == org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.completed) {
								LinkInfo linfo = myLinkFrame.resolve(link);
								// Link_Ready
								LinkReady lnk_event = new LinkReady(
										link.getXpath(), link.getId(),
										link.name, sFrame.oscope.getXpath(),
										sFrame.scopeInstanceId, process_name,
										process_ID, link.source.getXpath(),
										link.target.getXpath());
								getBpelRuntimeContext().getBpelProcess()
										.getEngine().fireEvent(lnk_event);

								try {
									boolean val = evaluateTransitionCondition(link.transitionCondition);
									// Link_Evaluated
									LinkEvaluated link_event = new LinkEvaluated(
											link.getXpath(), link.getId(),
											link.name,
											sFrame.oscope.getXpath(),
											sFrame.scopeInstanceId,
											process_name, process_ID,
											link.source.getXpath(),
											link.target.getXpath(), val,
											linfo.pub);
									getBpelRuntimeContext().getBpelProcess()
											.getEngine().fireEvent(link_event);

									// @hahnml: Update the status in the LinkDAO
									LinkDAO dao = getBpelRuntimeContext()
											.getProcessInstanceDao().getLink(
													link.getId());
									dao.setState(val ? LinkStateEnum.TRUE
											: LinkStateEnum.FALSE);
								} catch (FaultException e) {
									__log.error(e);
									if (linkFault == null)
										// @hahnml: Set the message of the exception to the FaultData object
										linkFault = createFault(e.getQName(),
												link.transitionCondition, e.getMessage());
								}
							}

							// TODO: DPE und Fehlerbehandlung?

						}
					}

					instance(ACTIVE.this);

					getBpelRuntimeContext().getBpelProcess().getProcessDAO()
							.getInstance(process_ID).getInstanceMigrationDAO()
							.setSuspended(false);

					/*
					 * @hahnml: The migration flag has to remain unchanged
					 * ("true"), so that other activities (like other sequences
					 * or flows) also execute their migration logic.
					 */
					// getBpelRuntimeContext().getBpelProcess().getProcessDAO()
					// .getInstance(process_ID).getInstanceMigrationDAO()
					// .setMigrated(false);

					// @hahnml: Instead of setting the migration flag to "false"
					// we register this activity as handled and updated.
					ProcessModelChangeRegistry.getRegistry().setHandled(_oflow);
					ProcessModelChangeRegistry.getRegistry().setUpdated(_oflow,
							true);

				} else {
					// @hahnml: Check if this is the process main flow or the
					// main flow of the process fault handler
					if ((_self.o.getXpath().equals("/process/flow[1]") || ((_self.o
							.getParent() instanceof OCatch)
							&& (_self.o.getParent().getParent().getXpath()
									.equals("/process")) && (_self.o instanceof OFlow)))
							&& !letItFinish) {
						// @hahnml: Check if we got a failure during the
						// execution of the flow
						if (_fault != null) {
							// NOTE: we do not not have to do DPE here because
							// all
							// the
							// children
							// have been started, and are therefore expected to
							// set
							// the
							// value of
							// their outgoing links.
							FLOW.this.Activity_Completed(_fault,
									_compensations, terminated);
						} else {
							// @hahnml: instantiate an ACTIVE object
							instance(ACTIVE.this);

							// @hahnml: suspend the instance
							DebuggerSupport debugSupport = getBpelRuntimeContext()
									.getBpelProcess().getDebuggerSupport();
							debugSupport.suspend(process_ID);

							// @hahnml: set the SUSPEND flag
							getBpelRuntimeContext().getBpelProcess()
									.getProcessDAO().getInstance(process_ID)
									.getInstanceMigrationDAO()
									.setSuspended(true);
						}

					} else {
						// NOTE: we do not not have to do DPE here because all
						// the
						// children
						// have been started, and are therefore expected to set
						// the
						// value of
						// their outgoing links.
						FLOW.this.Activity_Completed(_fault, _compensations,
								terminated);
					}

					if (letItFinish) {
						// @hahnml: Delete the process
						// instance migration table from the database.
						getBpelRuntimeContext().getBpelProcess()
								.getProcessDAO().getInstance(process_ID)
								.deleteProcessInstanceMigration();
					}
				}
			}
		}
	}

	public void Activity_Completed(FaultData fault,
			Set<CompensationHandler> comps, Boolean terminate) {
		final FaultData tmp = fault;
		final Set<CompensationHandler> compen_tmp = comps;

		if (terminate) {
			// Event Activity_Terminated
			ActivityTerminated evt = new ActivityTerminated(_self.o.name,
					_self.o.getId(), _self.o.getXpath(), _self.aId,
					sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
					process_name, process_ID, _self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			_self.parent.completed(tmp, compen_tmp);

			// @hahnml: Remove the FLOW from the ActivityEventHandler
			ActivityEventHandler.getInstance().removeRunningFLOW(this);
		} else {
			if (fault != null) {
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 1132657788L;

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
				ActivityFaulted evt2 = new ActivityFaulted(_self.o.name,
						_self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal, tmp);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt2);

				// @hahnml: Remove the FLOW from the ActivityEventHandler
				ActivityEventHandler.getInstance().removeRunningFLOW(this);
			} else {
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 86865982355L;

					public void linkStatus(boolean value) {

						// Event Activity_Complete
						ActivityComplete evt = new ActivityComplete(
								_self.o.name, _self.o.getId(),
								_self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_self.parent.completed(null, compen_tmp);

						// @hahnml: Remove the FLOW from the
						// ActivityEventHandler
						ActivityEventHandler.getInstance().removeRunningFLOW(
								FLOW.this);
					}

				};
				TerminationChannelListener termChan = new TerminationChannelListener(
						_self.self) {
					private static final long serialVersionUID = 453533435527L;

					public void terminate() {

						// Event Activity_Terminated
						ActivityTerminated evt = new ActivityTerminated(
								_self.o.name, _self.o.getId(),
								_self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_terminatedActivity = true;
						_self.parent.completed(null, compen_tmp);

						// @hahnml: Remove the FLOW from the
						// ActivityEventHandler
						ActivityEventHandler.getInstance().removeRunningFLOW(
								FLOW.this);
					}
					
					//krwczk: TODO -implement skip
					public void skip() {
						
					}

				};

				object(false, (termChan).or(receiver));

				// Event Activity_Executed
				ActivityExecuted evt2 = new ActivityExecuted(_self.o.name,
						_self.o.getId(), _self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt2);
			}
		}

	}

	// @hahnml: Copied from ACTIVITYGUARD
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

	public String toString() {
		return "<T:Act:Flow:" + _oflow.name + ">";
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
