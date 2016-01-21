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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.engine.DebuggerSupport;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.extensions.events.ActivitySkipped;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OCatch;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OSequence;
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
 * Implementation of the BPEL &lt;sequence&gt; activity.
 */
public class SEQUENCE extends ACTIVITY {
	private static final long serialVersionUID = 1L;
	// AH: changed from private
	protected List<OActivity> _remaining;
	// AH: end
	private final Set<CompensationHandler> _compensations;
	// AH: changed from private
	protected Boolean _firstTime;
	// AH: end
	public FaultData _fault;

	// changed by Bo Ning: deleted "final"
	QName process_name;
	// changed by Bo Ning: deleted "final"
	Long process_ID;

	// added by Bo Ning
	public SEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, QName processname, Long pid) {
		this(self, scopeFrame, linkFrame, ((OSequence) self.o).sequence,
				CompensationHandler.emptySet(), true, processname, pid);
	}

	// added by Bo Ning
	public SEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, List<OActivity> remaining,
			Set<CompensationHandler> compensations, Boolean firstT,
			QName processname, Long pid) {
		super(self, scopeFrame, linkFrame, processname, pid);
		_remaining = remaining;
		_compensations = compensations;
		_firstTime = firstT;
		process_name = processname;
		process_ID = pid;

	}

	// changed by Bo Ning(to public)
	public SEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		this(self, scopeFrame, linkFrame, ((OSequence) self.o).sequence,
				CompensationHandler.emptySet(), true);
	}

	// changed by Bo Ning(to public)
	public SEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, List<OActivity> remaining,
			Set<CompensationHandler> compensations, Boolean firstT) {
		super(self, scopeFrame, linkFrame);
		_remaining = remaining;
		_compensations = compensations;
		_firstTime = firstT;
		process_name = getBpelRuntimeContext().getBpelProcess().getPID();
		process_ID = getBpelRuntimeContext().getPid();
	}

	// AH: used to create the right SEQUENCE or FRAGMENTSEQUENCE
	protected void createInstance(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, List<OActivity> remaining,
			Set<CompensationHandler> compensations, Boolean firstT) {
		instance(new SEQUENCE(_self, _scopeFrame, _linkFrame, remaining,
				compensations, false));
	}

	protected void onActivityComplete() {

	}

	// AH: end

	public void run() {
		if (_firstTime) {
			// State of Activity is Ready
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 10241373711188875L;

				public void linkStatus(boolean value) {
					if (value) // Incoming Event Start_Activity received
					{
						SEQUENCE.this.execute();
					} else // Incoming Event Complete_Activity received
					{
						dpe(_self.o.outgoingLinks);
						SEQUENCE.this.Activity_Complete(false,
								CompensationHandler.emptySet());
					}
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 154656756L;

				public void terminate() {
					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					dpe(_self.o.outgoingLinks);
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					ActivitySkipped evt = new ActivitySkipped(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_skippedActivity = true;
					dpe(_self.o.outgoingLinks);
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}

			};

			object(false, (termChan).or(receiver));

			// Event Activity_Ready
			ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false, signal, _self.self, SEQUENCE.this);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		} else {
			// @hahnml: Changed to work with DAO's
			// get data from ode_instance_migration table
			boolean letItFinish = getBpelRuntimeContext().getBpelProcess()
					.getProcessDAO().getInstance(process_ID)
					.getInstanceMigrationDAO().isFinished();
			boolean runOutofWork = getBpelRuntimeContext().getBpelProcess()
					.getProcessDAO().getInstance(process_ID)
					.getInstanceMigrationDAO().isSuspended();
			boolean wasMigrated = getBpelRuntimeContext().getBpelProcess()
					.getProcessDAO().getInstance(process_ID)
					.getInstanceMigrationDAO().isMigrated();

			// remaining nachladen wenn nötig
			// @hahnml: Check if the instance was migrated and the sequence is
			// not handled yet
			if (wasMigrated
					&& !ProcessModelChangeRegistry.getRegistry().isHandled(
							_self.o)) {
				List<OActivity> aSequence = ((OSequence) _self.o).sequence;
				Integer size = aSequence.size();
				OActivity oActivity = _remaining.get(0);
				Integer sIndex = 0;

				if (oActivity == null) {
					// @hahnml: If some activities were deleted during migration
					// the _remaining entry will be null. We have to find the
					// next valid entry and use this
					// to get the index of the new _remaining start index of the
					// sequence.
					int steps = 0;
					while (oActivity == null && steps < _remaining.size()) {
						oActivity = _remaining.get(steps);

						++steps;
					}

					if (oActivity == null) {
						// If still no activity is found we use the second last
						// one to start
						sIndex = aSequence.size() - 2;
					}

					sIndex = aSequence.indexOf(oActivity);

				} else {

					sIndex = aSequence.indexOf(oActivity);

					// @hahnml: If some new elements were added exactly between
					// the currently executed activity and the one stored in
					// _remaining we have to move backwards to get the new
					// elements.
					boolean elementAdded = true;
					--sIndex;
					while (sIndex > 0 && elementAdded) {

						if (ProcessModelChangeRegistry.getRegistry()
								.getAddedElementXPaths()
								.contains(aSequence.get(sIndex).getXpath())) {
							oActivity = aSequence.get(sIndex);
							--sIndex;
						} else {
							elementAdded = false;
						}

					}

					sIndex = aSequence.indexOf(oActivity);
				}

				// @hahnml: If the process instance was migrated and
				// runOutOfWork before, we have to increase the index by 1 to
				// prevent the second execution of the last activity of the
				// sequence.
				if (runOutofWork) {
					sIndex += 1;
				}

				List<OActivity> remaining = aSequence.subList(sIndex, size);
				_remaining = remaining;

				// Fall 3: Instanz war automatisch im Zustand SUSPENDED wurde
				// migriert und resumed
				getBpelRuntimeContext().getBpelProcess().getProcessDAO()
						.getInstance(process_ID).getInstanceMigrationDAO()
						.setSuspended(false);

				/*
				 * @hahnml: The migration flag has to remain unchanged ("true"),
				 * so that other activities (like other sequences or flows) also
				 * execute their migration logic.
				 */
				// getBpelRuntimeContext().getBpelProcess().getProcessDAO()
				// .getInstance(process_ID).getInstanceMigrationDAO()
				// .setMigrated(false);

				// @hahnml: Instead of setting the migration flag to "false" we
				// register this activity as handled.
				ProcessModelChangeRegistry.getRegistry().setHandled(_self.o);

				runOutofWork = false;
				wasMigrated = false;
			}

			// Fall 1: Instanz soll beendet werden ohne dass sie migriert wurde
			// wenn sie davor automatisch suspended wurde
			if (_remaining.size() == 1 && runOutofWork && letItFinish) {
				_remaining.remove(0);
				TreeSet<CompensationHandler> comps = new TreeSet<CompensationHandler>(
						_compensations);
				Activity_Complete(false, comps);

			}
			// Fall 2: Instanz wurde automatisch suspended, wird ohne Migration
			// resumed
			else if (_remaining.size() == 1 && runOutofWork && !letItFinish) {
				// SEQUENCE in Execution Queue schreiben
				TreeSet<CompensationHandler> comps = new TreeSet<CompensationHandler>(
						_compensations);
				ArrayList<OActivity> remaining = new ArrayList<OActivity>(
						_remaining);

				instance(new SEQUENCE(_self, _scopeFrame, _linkFrame,
						remaining, comps, false));

				// SEQUENCE in Zustand SUSPENDED überführen
				DebuggerSupport debugSupport = getBpelRuntimeContext()
						.getBpelProcess().getDebuggerSupport();
				debugSupport.suspend(process_ID);
			} else {
				this.execute();
			}
			// end schlieta
		}
	}

	public void execute() {
		if (_firstTime) {
			// Event Activity_Executing
			ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		}

		final ActivityInfo child = new ActivityInfo(genMonotonic(),
				_remaining.get(0), newChannel(TerminationChannel.class),
				newChannel(ParentScopeChannel.class, "sequence"
						+ SEQUENCE.this._self.toString()));
		instance(createChild(child, _scopeFrame, _linkFrame));
		instance(new ACTIVE(child));
	}

	// AH: changed to protected
	protected class ACTIVE extends BpelJacobRunnable {
		// AH: end
		private static final long serialVersionUID = -2663862698981385732L;
		private ActivityInfo _child;
		private boolean _terminateRequested = false;

		ACTIVE(ActivityInfo child) {
			_child = child;
			
			// @hahnml: Set the OBase id
			oId = SEQUENCE.this.oId;
		}

		public void run() {
			// schlieta: zur Sicherheit neue Kopie von _remaining erstellen und
			// ersetzen
			ArrayList<OActivity> remaining = new ArrayList<OActivity>(
					_remaining);
			_remaining = remaining;

			object(false, new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = -2680515407515637639L;

				public void terminate() {
					if (!_terminateRequested) {
						replication(_child.self).terminate();

						_remaining.remove(0);
						// Don't do any of the remaining activities, DPE
						// instead.
						deadPathRemaining();

						_terminateRequested = true;
					}
					instance(ACTIVE.this);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					ActivitySkipped evt = new ActivitySkipped(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_skippedActivity = true;
					dpe(_self.o.outgoingLinks);
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}
			}.or(new ParentScopeChannelListener(_child.parent) {
				private static final long serialVersionUID = 7195562310281985971L;

				public void compensate(OScope scope, SynchChannel ret) {
					_self.parent.compensate(scope, ret);
					instance(ACTIVE.this);
				}

				public void completed(FaultData faultData,
						Set<CompensationHandler> compensations) {
					TreeSet<CompensationHandler> comps = new TreeSet<CompensationHandler>(
							_compensations);
					_fault = faultData;
					comps.addAll(compensations);
					
					// Update _remaining
					// @hahnml: Check if the instance was migrated and the
					// sequence is not handled yet. Has to be done if 
					// the model was migrated with a breakpoint during the
					// execution of the last activity of the sequence.
					if (getBpelRuntimeContext().getBpelProcess()
							.getProcessDAO().getInstance(process_ID)
							.getInstanceMigrationDAO().isMigrated()
							&& !ProcessModelChangeRegistry.getRegistry()
									.isHandled(_self.o)) {
						List<OActivity> aSequence = ((OSequence) _self.o).sequence;
						Integer size = aSequence.size();
						OActivity oActivity = _remaining.get(0);
						Integer sIndex = 0;

						if (oActivity == null) {
							// @hahnml: If some activities were deleted
							// during migration
							// the _remaining entry will be null. We have to
							// find the
							// next valid entry and use this
							// to get the index of the new _remaining start
							// index of the
							// sequence.
							int steps = 0;
							while (oActivity == null
									&& steps < _remaining.size()) {
								oActivity = _remaining.get(steps);

								++steps;
							}

							if (oActivity == null) {
								// If still no activity is found we use the
								// second last
								// one to start
								sIndex = aSequence.size() - 2;
							}

							sIndex = aSequence.indexOf(oActivity);

						} else {

							sIndex = aSequence.indexOf(oActivity);

							// @hahnml: If some new elements were added
							// exactly between
							// the currently executed activity and the one
							// stored in
							// _remaining we have to move backwards to get
							// the new
							// elements.
							boolean elementAdded = true;
							--sIndex;
							while (sIndex > 0 && elementAdded) {

								if (ProcessModelChangeRegistry
										.getRegistry()
										.getAddedElementXPaths()
										.contains(
												aSequence.get(sIndex)
														.getXpath())) {
									oActivity = aSequence.get(sIndex);
									--sIndex;
								} else {
									elementAdded = false;
								}

							}

							sIndex = aSequence.indexOf(oActivity);
						}

						// @hahnml: If the process instance was migrated and
						// runOutOfWork before, we have to increase the
						// index by 1 to
						// prevent the second execution of the last activity
						// of the
						// sequence.
						if (getBpelRuntimeContext().getBpelProcess()
								.getProcessDAO().getInstance(process_ID)
								.getInstanceMigrationDAO().isSuspended()) {
							sIndex += 1;
						}

						List<OActivity> remaining = aSequence.subList(
								sIndex, size);
						_remaining = remaining;

						// Fall 3: Instanz war automatisch im Zustand
						// SUSPENDED wurde
						// migriert und resumed
						getBpelRuntimeContext().getBpelProcess()
								.getProcessDAO().getInstance(process_ID)
								.getInstanceMigrationDAO()
								.setSuspended(false);

						/*
						 * @hahnml: The migration flag has to remain
						 * unchanged ("true"), so that other activities
						 * (like other sequences or flows) also execute
						 * their migration logic.
						 */
						// getBpelRuntimeContext().getBpelProcess().getProcessDAO()
						// .getInstance(process_ID).getInstanceMigrationDAO()
						// .setMigrated(false);

						// @hahnml: Instead of setting the migration flag to
						// "false" we register this activity as handled.
						ProcessModelChangeRegistry.getRegistry()
								.setHandled(_self.o);
					}
					
					if (_fault != null || _terminateRequested
							|| _remaining.size() <= 1) {
						if (!_terminateRequested && _remaining.size() >= 2
								&& _fault != null) {
							_remaining.remove(0);
							deadPathRemaining();
						}

						// schlieta neu

						// get data from ode_instance_migration table
						// @hahnml: Changed to work with DAO's
						boolean letItFinish = getBpelRuntimeContext()
								.getBpelProcess().getProcessDAO()
								.getInstance(process_ID)
								.getInstanceMigrationDAO().isFinished();

						// @hahnml: Check if this is the process sequence or the
						// sequence of the process fault handler
						if ((_self.o.getXpath().equals("/process/sequence[1]") || ((_self.o
								.getParent() instanceof OCatch)
								&& (_self.o.getParent().getParent().getXpath()
										.equals("/process")) && (_self.o instanceof OSequence)))
								&& !letItFinish && _remaining.size() == 1) {

							// @hahnml: Check if we got a failure during the
							// execution of the sequence
							if (_fault != null) {
								// Complete this sequence and suspend the
								// process fault handler sequence at its end to
								// keep it alive for migration
								Activity_Complete(_terminateRequested, comps);

							} else {

								// SEQUENCE in Execution Queue schreiben
								ArrayList<OActivity> remaining = new ArrayList<OActivity>(
										_remaining);
								instance(new SEQUENCE(_self, _scopeFrame,
										_linkFrame, remaining, comps, false));

								// suspend Instance
								DebuggerSupport debugSupport = getBpelRuntimeContext()
										.getBpelProcess().getDebuggerSupport();
								debugSupport.suspend(process_ID);

								// SUSPEND-Flag setzen
								getBpelRuntimeContext().getBpelProcess()
										.getProcessDAO()
										.getInstance(process_ID)
										.getInstanceMigrationDAO()
										.setSuspended(true);
							}
						}
						// Instanz beenden
						else {
							getBpelRuntimeContext().getBpelProcess()
									.getProcessDAO().getInstance(process_ID)
									.deleteProcessInstanceMigration();

							Activity_Complete(_terminateRequested, comps);

						}
						// end schlieta

					} else /*
							 * !fault && ! terminateRequested &&
							 * !remaining.isEmpty
							 */{
						ArrayList<OActivity> remaining = new ArrayList<OActivity>(
								_remaining);
						remaining.remove(0);
						createInstance(_self, _scopeFrame, _linkFrame,
								remaining, comps, false);
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

		private void deadPathRemaining() {
			for (Iterator<OActivity> i = _remaining.iterator(); i.hasNext();) {
				OActivity tmp = i.next();
				dpe(tmp.sourceLinks);
				dpe(tmp.outgoingLinks);
			}
		}

	}

	public void Activity_Complete(Boolean terminate,
			Set<CompensationHandler> compens) {
		// AH:
		onActivityComplete();
		// AH: end
		final Set<CompensationHandler> compen_tmp = compens;
		if (terminate) {
			// Event Activity_Terminated
			ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			_self.parent.completed(_fault, compens);
		} else {
			if (_fault != null) {
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 1024157457775L;

					public void linkStatus(boolean value) {
						if (value) // continue received
						{
							_self.parent.completed(_fault, compen_tmp);
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
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal, _fault);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt2);
			} else {
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 1022344588855L;

					public void linkStatus(boolean value) {

						// Event Activity_Complete
						ActivityComplete evt = new ActivityComplete(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_self.parent.completed(null, compen_tmp);
					}

				};
				TerminationChannelListener termChan = new TerminationChannelListener(
						_self.self) {
					private static final long serialVersionUID = 15346579005562L;

					public void terminate() {

						// Event Activity_Terminated
						ActivityTerminated evt = new ActivityTerminated(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(),
								sFrame.scopeInstanceId, process_name,
								process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_terminatedActivity = true;
						_self.parent.completed(null, compen_tmp);
					}
					
					//krwczk: TODO -implement skip
					public void skip() {
						ActivitySkipped evt = new ActivitySkipped(
								_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
								sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
								process_name, process_ID, _self.o.getArt(), false);
						getBpelRuntimeContext().getBpelProcess().getEngine()
								.fireEvent(evt);
						_skippedActivity = true;
						dpe(_self.o.outgoingLinks);
						_self.parent
								.completed(null, CompensationHandler.emptySet());
					}

				};

				object(false, (termChan).or(receiver));

				// Event Activity_Executed
				ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt2);
			}
		}

	}

	public String toString() {
		StringBuffer buf = new StringBuffer("SEQUENCE(self=");
		buf.append(_self);
		buf.append(", linkframe=");
		buf.append(_linkFrame);
		buf.append(", remaining=");
		buf.append(_remaining);
		buf.append(')');
		return buf.toString();
	}
}
