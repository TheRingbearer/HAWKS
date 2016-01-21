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
import org.apache.ode.bpel.extensions.events.ActivitySkipped;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OWait;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannel;
import org.apache.ode.bpel.runtime.channels.TimerResponseChannelListener;
import org.apache.ode.utils.xsd.Duration;

import java.util.Calendar;
import java.util.Date;

import javax.xml.namespace.QName;

/**
 * JacobRunnable that performs the work of the <code>&lt;wait&gt;</code>
 * activity.
 */
// @hahnml: Changed to public
public class WAIT extends ACTIVITY {
	private static final long serialVersionUID = 1L;
	private static final Log __log = LogFactory.getLog(WAIT.class);
	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	WAIT(ActivityInfo self, ScopeFrame scopeFrame, LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	public final void run() {
		// State of the Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373711188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					WAIT.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					WAIT.this.Activity_Complete();
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 154656756L;

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
				//Event Activity_Skipped
				ActivitySkipped evt = new ActivitySkipped(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false);
				getBpelRuntimeContext().getBpelProcess().getEngine()
				.fireEvent(evt);
				_skippedActivity = true;
				_self.parent.completed(null, CompensationHandler.emptySet());
			}

		};

		object(false, (termChan).or(receiver));

		// Event Activity_Ready
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(), _self.o.getXpath(),
				_self.aId, sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
				process_name, process_ID, _self.o.getArt(), false, signal,
				_self.self, WAIT.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void Activity_Faulted(FaultException e) {
		// @hahnml: Set the message of the exception to the FaultData object
		final FaultData tmp = createFault(e.getQName(), _self.o, e.getMessage());

		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373771353L;

			public void linkStatus(boolean value) {
				if (value) // continue received
				{
					_self.parent.completed(tmp, CompensationHandler.emptySet());
				} else // suppress_fault received
				{
					_terminatedActivity = true;
					_self.parent
							.completed(null, CompensationHandler.emptySet());
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

	// @hahnml: Handles the EvaluationException if evaluation of the wait
	// condition fails.
	public void Activity_Faulted(EvaluationException e) {
		// @hahnml: Set the message of the exception to the FaultData object
		final FaultData tmp = createFault(
				_self.o.getOwner().constants.qnSubLanguageExecutionFault,
				_self.o, e.getMessage());

		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373771353L;

			public void linkStatus(boolean value) {
				if (value) // continue received
				{
					_self.parent.completed(tmp, CompensationHandler.emptySet());
				} else // suppress_fault received
				{
					_terminatedActivity = true;
					_self.parent
							.completed(null, CompensationHandler.emptySet());
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

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		Date dueDate = null;
		try {
			dueDate = getDueDate();
		} catch (FaultException e) {
			__log.error("Fault while calculating due date: " + e.getQName()
					+ "; Reason: " + e.getMessage());
			Activity_Faulted(e);
			return;
		} catch (EvaluationException ee) {
			String msg = "Unexpected error evaluating wait condition.";
			__log.error(msg, ee);
			// @hahnml: EvaluationException will now be processed in
			// ActivityFaulted(EvaluationException e) which acts equal to a
			// ActivityFaulted(FaultException e).
			Activity_Faulted(ee);
			return;
		}

		if (dueDate.getTime() > getBpelRuntimeContext()
				.getCurrentEventDateTime().getTime()) {
			final TimerResponseChannel timerChannel = newChannel(TimerResponseChannel.class);
			getBpelRuntimeContext().registerTimer(timerChannel, dueDate,
					_self.o);

			object(false, new TimerResponseChannelListener(timerChannel) {
				private static final long serialVersionUID = 3120518305645437327L;

				public void onTimeout() {
					Activity_Complete();
				}

				public void onCancel() {
					Activity_Complete();
				}
			}.or(new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = -2791243270691333946L;

				public void terminate() {
					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent.completed(null, CompensationHandler.emptySet());
					object(new TimerResponseChannelListener(timerChannel) {
						private static final long serialVersionUID = 677746737897792929L;

						public void onTimeout() {
							// ignore
						}

						public void onCancel() {
							// ingore
						}
					});
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					//Event Activity_Skipped
					ActivitySkipped evt = new ActivitySkipped(_self.o.name, _self.o.getId(),
							_self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt);
					_skippedActivity = true;
					_self.parent.completed(null, CompensationHandler.emptySet());
				}
			}));
		} else {
			Activity_Complete();
		}
	}

	public void Activity_Complete() {
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 102342132855L;

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
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 1569976844562L;

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

		// Event Activity_Executed
		ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	protected Date getDueDate() throws FaultException, EvaluationException {

		OWait wait = (OWait) _self.o;

		// Assume the data was well formed (we have a deadline or a duration)
		assert wait.hasFor() || wait.hasUntil();

		EvaluationContext evalCtx = getEvaluationContext();

		Date dueDate = null;
		if (wait.hasFor()) {
			Calendar cal = Calendar.getInstance();
			Duration duration = getBpelRuntimeContext().getExpLangRuntime()
					.evaluateAsDuration(wait.forExpression, evalCtx);
			duration.addTo(cal);
			dueDate = cal.getTime();
		} else if (wait.hasUntil()) {
			Calendar cal = getBpelRuntimeContext().getExpLangRuntime()
					.evaluateAsDate(wait.untilExpression, evalCtx);
			dueDate = cal.getTime();
		} else {
			throw new AssertionError(
					"Static checks failed to find bad WaitActivity!");
		}

		// For now if we cannot evaluate a due date, we assume it is due now.
		// TODO: BPEL-ISSUE: verify BPEL spec for proper handling of these
		// errors
		if (dueDate == null)
			dueDate = new Date();

		return dueDate;
	}

}
