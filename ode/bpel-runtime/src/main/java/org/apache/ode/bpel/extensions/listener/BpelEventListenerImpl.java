package org.apache.ode.bpel.extensions.listener;

import java.util.Properties;

import org.apache.ode.bpel.evt.BpelEvent;
import org.apache.ode.bpel.evt.CorrelationSetWriteEvent;
import org.apache.ode.bpel.evt.NewProcessInstanceEvent;
import org.apache.ode.bpel.evt.PartnerLinkModificationEvent;
import org.apache.ode.bpel.evt.ProcessCompletionEvent;
import org.apache.ode.bpel.evt.ProcessInstanceStartedEvent;
import org.apache.ode.bpel.evt.ProcessInstanceStateChangeEvent;
import org.apache.ode.bpel.evt.ProcessTerminationEvent;
import org.apache.ode.bpel.evt.VariableModificationAtAssignEvent;
import org.apache.ode.bpel.evt.VariableModificationEvent;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivitySkipped;
import org.apache.ode.bpel.extensions.events.ActivityDeadPath;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityJoinFailure;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.extensions.events.EvaluatingTransitionConditionFaulted;
import org.apache.ode.bpel.extensions.events.FaultHandlingNoHandler;
import org.apache.ode.bpel.extensions.events.IterationComplete;
import org.apache.ode.bpel.extensions.events.LinkEvaluated;
import org.apache.ode.bpel.extensions.events.LinkReady;
import org.apache.ode.bpel.extensions.events.LoopConditionFalse;
import org.apache.ode.bpel.extensions.events.LoopConditionTrue;
import org.apache.ode.bpel.extensions.events.ScopeActivityComplete;
import org.apache.ode.bpel.extensions.events.ScopeActivityExecuted;
import org.apache.ode.bpel.extensions.events.ScopeActivityExecuting;
import org.apache.ode.bpel.extensions.events.ScopeActivityFaulted;
import org.apache.ode.bpel.extensions.events.ScopeActivityReady;
import org.apache.ode.bpel.extensions.events.ScopeActivityTerminated;
import org.apache.ode.bpel.extensions.events.ScopeCompensated;
import org.apache.ode.bpel.extensions.events.ScopeCompensating;
import org.apache.ode.bpel.extensions.events.ScopeCompleteWithFault;
import org.apache.ode.bpel.extensions.events.ScopeEventHandlingEnded;
import org.apache.ode.bpel.extensions.events.ScopeHandlingEvent;
import org.apache.ode.bpel.extensions.events.ScopeHandlingFault;
import org.apache.ode.bpel.extensions.events.ScopeHandlingTermination;
import org.apache.ode.bpel.extensions.handler.ActivityEventHandler;
import org.apache.ode.bpel.extensions.handler.InstanceEventHandler;
import org.apache.ode.bpel.iapi.BpelEventListener;

//@stmz: listens to BpelEvents that occur during execution of process instances
//the name of a method says it all
//we just call methods in InstanceEventHandler or ActivityEventHandler to further handle these events
public class BpelEventListenerImpl implements BpelEventListener {
	InstanceEventHandler ieh = InstanceEventHandler.getInstance();
	ActivityEventHandler aeh = ActivityEventHandler.getInstance();

	public void onEvent(BpelEvent bpelEvent) {

		// InstanceEvents
		if (bpelEvent instanceof NewProcessInstanceEvent) {
			NewProcessInstanceEvent tmp = (NewProcessInstanceEvent) bpelEvent;
			ieh.Process_Instantiated(tmp.getProcessId(),
					tmp.getProcessInstanceId(), tmp.getVersion());
		}

		else if (bpelEvent instanceof ProcessCompletionEvent) {
			ProcessCompletionEvent tmp = (ProcessCompletionEvent) bpelEvent;
			if (tmp.getFault() == null) {
				// Completion ok
				ieh.Instance_Completed(tmp.getProcessId(),
						tmp.getProcessInstanceId());
			} else {
				// Completion faulted
				ieh.Instance_Faulted(tmp.getProcessId(),
						tmp.getProcessInstanceId(), tmp.getFault(),
						tmp.getElementType(), tmp.getMessageType(),
						tmp.getFaultMsg());
			}
		}

		else if (bpelEvent instanceof ProcessTerminationEvent) {
			ProcessTerminationEvent tmp = (ProcessTerminationEvent) bpelEvent;
			ieh.Instance_Terminated(tmp.getProcessId(),
					tmp.getProcessInstanceId());
		}

		else if (bpelEvent instanceof ProcessInstanceStartedEvent) {
			ProcessInstanceStartedEvent tmp = (ProcessInstanceStartedEvent) bpelEvent;
			ieh.Instance_Running(tmp.getProcessId(), tmp.getProcessInstanceId());
		}

		else if (bpelEvent instanceof ProcessInstanceStateChangeEvent) {
			ProcessInstanceStateChangeEvent tmp = (ProcessInstanceStateChangeEvent) bpelEvent;
			if (tmp.getState() != null) {
				if (tmp.getState().equals("Suspend")) {
					ieh.Instance_Suspended(tmp.getProcessId(),
							tmp.getProcessInstanceId());
				} else if (tmp.getState().equals("Resume")) {
					ieh.Instance_Resumed(tmp.getProcessId(),
							tmp.getProcessInstanceId());
				} else if (tmp.getState().equals("IterationPrepared")) {
					// @hahnml: Propagate new state iterate
					ieh.Instance_Iteration_Prepared(tmp.getProcessId(),
							tmp.getProcessInstanceId(), tmp.getDetails());
				} else if (tmp.getState().equals("ReexecutionPrepared")) {
					ieh.Instance_Reexecution_Prepared(tmp.getProcessId(),
							tmp.getProcessInstanceId(), tmp.getDetails());
				} else if (tmp.getState().equals("JumpToPrepared")) {
					ieh.Instance_JumpTo_Prepared(tmp.getProcessId(),
							tmp.getProcessInstanceId(), tmp.getDetails());
				}
			}
		}

		// ActivityEvents
		else if (bpelEvent instanceof ActivityReady) {
			ActivityReady evt = (ActivityReady) bpelEvent;
			aeh.Activity_Ready(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getArtificial(),
					evt.getIsScope(), evt.getTermChan(), evt);
		}

		else if (bpelEvent instanceof ActivityTerminated) {
			ActivityTerminated evt = (ActivityTerminated) bpelEvent;
			aeh.Activity_Terminated(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope());
		}

		else if (bpelEvent instanceof ActivityComplete) {
			ActivityComplete evt = (ActivityComplete) bpelEvent;
			aeh.Activity_Complete(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope());
		}
		//krawczls: TODO -extend by the parameters specified in ActivitySkipped
		else if (bpelEvent instanceof ActivitySkipped) {
			ActivitySkipped evt = (ActivitySkipped) bpelEvent;
			aeh.Activity_Skipped(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope());
		}

		else if (bpelEvent instanceof ActivityExecuted) {
			ActivityExecuted evt = (ActivityExecuted) bpelEvent;
			aeh.Activity_Executed(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getArtificial(),
					evt.getIsScope(), evt);
		}

		else if (bpelEvent instanceof ActivityExecuting) {
			ActivityExecuting evt = (ActivityExecuting) bpelEvent;
			aeh.Activity_Executing(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope());
		}

		else if (bpelEvent instanceof ActivityFaulted) {
			ActivityFaulted evt = (ActivityFaulted) bpelEvent;
			aeh.Activity_Faulted(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getFault_data(),
					evt.getArtificial(), evt.getIsScope(), evt);
		}

		else if (bpelEvent instanceof EvaluatingTransitionConditionFaulted) {
			EvaluatingTransitionConditionFaulted evt = (EvaluatingTransitionConditionFaulted) bpelEvent;
			aeh.Evaluating_TransitionCondition_Faulted(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getFault(), evt.getArtificial(), evt.getIsScope(), evt,
					evt.getLinkXPath());
		}

		else if (bpelEvent instanceof ActivityDeadPath) {
			ActivityDeadPath evt = (ActivityDeadPath) bpelEvent;
			aeh.Activity_Dead_Path(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope());
		}

		else if (bpelEvent instanceof ActivityJoinFailure) {
			ActivityJoinFailure evt = (ActivityJoinFailure) bpelEvent;
			aeh.Activity_Join_Failure(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(), evt.getFault(),
					evt.getChan(), evt, evt.getSuppressJoinFailure());
		}

		else if (bpelEvent instanceof IterationComplete) {
			IterationComplete evt = (IterationComplete) bpelEvent;
			aeh.Iteration_Complete(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getArtificial(),
					evt.getIsScope(), evt);
		}

		else if (bpelEvent instanceof LoopConditionTrue) {
			LoopConditionTrue evt = (LoopConditionTrue) bpelEvent;
			aeh.Loop_Condition_True(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getArtificial(),
					evt.getIsScope(), evt);
		}

		else if (bpelEvent instanceof LoopConditionFalse) {
			LoopConditionFalse evt = (LoopConditionFalse) bpelEvent;
			aeh.Loop_Condition_False(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(), evt);
		}

		else if (bpelEvent instanceof LinkReady) {
			LinkReady evt = (LinkReady) bpelEvent;
			aeh.Link_Ready(evt.getXPath_Link(), evt.getLink_name(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getSource_xpath(), evt.getTarget_xpath());
		}

		else if (bpelEvent instanceof LinkEvaluated) {
			LinkEvaluated evt = (LinkEvaluated) bpelEvent;
			aeh.Link_Evaluated(evt.getXPath_Link(), evt.getLink_name(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getSource_xpath(), evt.getTarget_xpath(),
					evt.getValue(), evt.getChan(), evt);
		}

		else if (bpelEvent instanceof ScopeActivityReady) {
			ScopeActivityReady evt = (ScopeActivityReady) bpelEvent;
			aeh.Scope_Activity_Ready(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getTermChannel(),
					evt.getFaultChannel(), evt.getOscope(), evt.getScope(),
					evt.getIgnore(), evt);
		}

		else if (bpelEvent instanceof ScopeActivityTerminated) {
			ScopeActivityTerminated evt = (ScopeActivityTerminated) bpelEvent;
			aeh.Scope_Activity_Terminated(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore());
		}

		else if (bpelEvent instanceof ScopeActivityExecuting) {
			ScopeActivityExecuting evt = (ScopeActivityExecuting) bpelEvent;
			aeh.Scope_Activity_Executing(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore());
		}

		else if (bpelEvent instanceof ScopeHandlingEvent) {
			ScopeHandlingEvent evt = (ScopeHandlingEvent) bpelEvent;
			aeh.Scope_Handling_Event(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore());
		}

		else if (bpelEvent instanceof ScopeHandlingTermination) {
			ScopeHandlingTermination evt = (ScopeHandlingTermination) bpelEvent;
			aeh.Scope_Handling_Termination(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore(), evt.getChan(), evt);
		}

		else if (bpelEvent instanceof ScopeEventHandlingEnded) {
			ScopeEventHandlingEnded evt = (ScopeEventHandlingEnded) bpelEvent;
			aeh.Scope_Event_Handling_Ended(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore());
		}

		else if (bpelEvent instanceof ScopeHandlingFault) {
			ScopeHandlingFault evt = (ScopeHandlingFault) bpelEvent;
			aeh.Scope_Handling_Fault(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getFault_name(),
					evt.getFaultMSG(), evt.getMessageType(),
					evt.getElementType(), evt.getIgnore(), evt);
		}

		/*
		 * else if (bpelEvent instanceof FaultHandlingNoHandler) {
		 * FaultHandlingNoHandler evt = (FaultHandlingNoHandler) bpelEvent;
		 * aeh.FaultHandling_NoHandler(evt.getXpath_act(), evt.getID_act(),
		 * evt.getXpath_surrounding_scope(), evt.getID_scope(),
		 * evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
		 * evt.getArtificial(), evt.getIsScope(), evt.getSelfScopeID(),
		 * evt.getIgnore(), evt); }
		 */

		else if (bpelEvent instanceof ScopeActivityFaulted) {
			ScopeActivityFaulted evt = (ScopeActivityFaulted) bpelEvent;
			aeh.Scope_Activity_Faulted(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore(), evt.getFault(), evt);
		}

		else if (bpelEvent instanceof ScopeCompleteWithFault) {
			ScopeCompleteWithFault evt = (ScopeCompleteWithFault) bpelEvent;
			aeh.Scope_Complete_With_Fault(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore(), evt);
		}

		else if (bpelEvent instanceof ScopeActivityExecuted) {
			ScopeActivityExecuted evt = (ScopeActivityExecuted) bpelEvent;
			aeh.Scope_Activity_Executed(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(), evt.getChan(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore(), evt);
		}

		else if (bpelEvent instanceof ScopeActivityComplete) {
			ScopeActivityComplete evt = (ScopeActivityComplete) bpelEvent;
			aeh.Scope_Activity_Complete(evt.getActivity_name(),
					evt.getXpath_act(), evt.getID_act(),
					evt.getXpath_surrounding_scope(), evt.getID_scope(),
					evt.getProcess_name(), evt.getProcess_ID(),
					evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getCompHandler(), evt.getIgnore());
		}

		else if (bpelEvent instanceof ScopeCompensating) {
			ScopeCompensating evt = (ScopeCompensating) bpelEvent;
			aeh.Scope_Compensating(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getChan(), evt.getArtificial(),
					evt.getIsScope(), evt.getSelfScopeID(), evt.getIgnore(),
					evt);
		}

		else if (bpelEvent instanceof ScopeCompensated) {
			ScopeCompensated evt = (ScopeCompensated) bpelEvent;
			aeh.Scope_Compensated(evt.getActivity_name(), evt.getXpath_act(),
					evt.getID_act(), evt.getXpath_surrounding_scope(),
					evt.getID_scope(), evt.getProcess_name(),
					evt.getProcess_ID(), evt.getArtificial(), evt.getIsScope(),
					evt.getSelfScopeID(), evt.getIgnore());
		}

		else if (bpelEvent instanceof VariableModificationAtAssignEvent) {
			VariableModificationAtAssignEvent evt = (VariableModificationAtAssignEvent) bpelEvent;
			aeh.Variable_Modification_At_Assign_Event(evt.getVarName(),
					evt.getVar_Xpath(), evt.getAct_xpath(),
					evt.getActivityID(), evt.getScopeXPath(), evt.getScopeID(),
					evt.getProcessId(), evt.getProcessInstanceId(),
					evt.getNewValue(), evt.getNumberOfCopyStatement());
		}

		else if (bpelEvent instanceof VariableModificationEvent) {
			VariableModificationEvent evt = (VariableModificationEvent) bpelEvent;
			aeh.Variable_Modification_Event(evt.getVarName(),
					evt.getVar_Xpath(), evt.getAct_xpath(),
					evt.getActivityID(), evt.getScopeXPath(), evt.getScopeID(),
					evt.getProcessId(), evt.getProcessInstanceId(),
					evt.getNewValue());
		}

		else if (bpelEvent instanceof CorrelationSetWriteEvent) {
			CorrelationSetWriteEvent evt = (CorrelationSetWriteEvent) bpelEvent;
			aeh.CorrelationSet_Write_Event(evt.getKey().getValues(),
					evt.getProcessId(), evt.getProcessInstanceId(),
					evt.getScopeId(), evt.getXPath(), evt.getAct_XPath(),
					evt.getOutside());
		}

		else if (bpelEvent instanceof PartnerLinkModificationEvent) {
			PartnerLinkModificationEvent evt = (PartnerLinkModificationEvent) bpelEvent;
			aeh.PartnerLinkModification_Event(evt.getpLinkName(),
					evt.getXpath(), evt.getAct_xpath(), evt.getScope_xpath(),
					evt.getScopeId(), evt.getProcessId(),
					evt.getProcessInstanceId(), evt.getValue());
		}
	}

	public void shutdown() {
		System.out.println("Listener shutdown.");

	}

	public void startup(Properties configProperties) {
		ieh = InstanceEventHandler.getInstance();
		System.out.println("Listener started.");
	}

}
