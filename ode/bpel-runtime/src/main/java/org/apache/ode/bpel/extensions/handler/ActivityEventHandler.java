package org.apache.ode.bpel.extensions.handler;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.evt.BpelEvent;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.explang.EvaluationException;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.manager.BlockingEvent;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.BlockingEventsInstance;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Dead_Path;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Executed;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Executing;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Faulted;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Terminated;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Activity_Skipped;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.CorrelationSet_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Evaluating_TransitionCondition_Faulted;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Evaluated;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Ready;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Set_False;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Set_True;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Loop_Condition_False;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Loop_Condition_True;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Loop_Iteration_Complete;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.PartnerLink_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Compensated;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Compensating;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Complete_With_Fault;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Event_Handling_Ended;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Handling_Event;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Handling_Fault;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Scope_Handling_Termination;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Modification_At_Assign;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityJoinFailure;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.EvaluatingTransitionConditionFaulted;
import org.apache.ode.bpel.extensions.events.IterationComplete;
import org.apache.ode.bpel.extensions.events.LinkEvaluated;
import org.apache.ode.bpel.extensions.events.LoopConditionFalse;
import org.apache.ode.bpel.extensions.events.LoopConditionTrue;
import org.apache.ode.bpel.extensions.events.ScopeActivityExecuted;
import org.apache.ode.bpel.extensions.events.ScopeActivityFaulted;
import org.apache.ode.bpel.extensions.events.ScopeActivityReady;
import org.apache.ode.bpel.extensions.events.ScopeCompensating;
import org.apache.ode.bpel.extensions.events.ScopeCompleteWithFault;
import org.apache.ode.bpel.extensions.events.ScopeHandlingFault;
import org.apache.ode.bpel.extensions.events.ScopeHandlingTermination;
import org.apache.ode.bpel.extensions.processes.Activity_Status;
import org.apache.ode.bpel.extensions.processes.Compensation_Handler;
import org.apache.ode.bpel.extensions.processes.Running_Activity;
import org.apache.ode.bpel.extensions.processes.Running_Scope;
import org.apache.ode.bpel.extensions.sync.Constants;
import org.apache.ode.bpel.o.OElementVarType;
import org.apache.ode.bpel.o.OMessageVarType;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OVarType;
import org.apache.ode.bpel.runtime.BpelRuntimeContext;
import org.apache.ode.bpel.runtime.COMPENSATIONHANDLER_;
import org.apache.ode.bpel.runtime.ExprEvaluationContextImpl;
import org.apache.ode.bpel.runtime.FLOW;
import org.apache.ode.bpel.runtime.SCOPE;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

//@stmz: handles all the events that occur on the level of activities
//the name of a method says it all
public class ActivityEventHandler {

	private static ActivityEventHandler instance;
	public static Logger logger = Logger.getLogger("Log-XML");
	private static Communication comm;
	private static GenericController genCon;
	private static BlockingManager block;
	private static InstanceEventHandler ieh;

	// @stmz: list of running scopes
	private List<Running_Scope> RunningScopes = Collections
			.synchronizedList(new ArrayList<Running_Scope>());

	// @stmz: list of running activities
	private List<Running_Activity> RunningActivities = Collections
			.synchronizedList(new ArrayList<Running_Activity>());

	// @stmz: list of compensation Handlers
	private List<Compensation_Handler> CompensationHandlers = Collections
			.synchronizedList(new ArrayList<Compensation_Handler>());

	// @hahnml: map of activity-XPath-status mappings. Contains the status of
	// activities of flows to evaluate
	// the status of added links during migration.
	private List<Activity_Status> ActivityStatus = Collections
			.synchronizedList(new ArrayList<Activity_Status>());

	// @hahnml: List of FLOW runtime objects
	private List<FLOW> FlowRuntimeObjects = Collections
			.synchronizedList(new ArrayList<FLOW>());

	// @hahnml: List of process SCOPE runtime objects
	private List<SCOPE> ProcessScopeRuntimeObjects = Collections
			.synchronizedList(new ArrayList<SCOPE>());

	private ActivityEventHandler() {
		comm = Communication.getInstance();
		genCon = GenericController.getInstance();
		block = BlockingManager.getInstance();
		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("ActivityEventHandler instantiated.");
		}
	}

	public static ActivityEventHandler getInstance() {
		if (instance == null) {
			instance = new ActivityEventHandler();
		}
		return instance;
	}

	public void addRunningActivity(Running_Activity tmp) {
		if (tmp.getXPath() != null) {
			synchronized (RunningActivities) {
				RunningActivities.add(tmp);
			}
		}
	}

	public void addRunningScope(Running_Scope tmp) {
		synchronized (RunningScopes) {
			RunningScopes.add(tmp);
		}
	}

	public void removeRunningActivity(Long processID, Long scopeID, String Xpath) {
		if (Xpath != null) {
			synchronized (RunningActivities) {
				Running_Activity remove = null;
				Iterator<Running_Activity> itr = RunningActivities.iterator();
				while (itr.hasNext()) {
					Running_Activity tmp_act = itr.next();
					if (tmp_act.getProcessID().equals(processID)
							&& tmp_act.getScopeID().equals(scopeID)
							&& tmp_act.getXPath().equals(Xpath)) {
						remove = tmp_act;
					}
				}
				if (remove != null) {
					RunningActivities.remove(remove);
				}
			}

			block.removeBlockingEvent(Xpath, scopeID, processID);
		}
	}

	public void removeRunningScope(QName processName, Long processID,
			Long selfScopeID) {
		synchronized (RunningScopes) {
			Running_Scope remove = null;
			Iterator<Running_Scope> itr = RunningScopes.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp = itr.next();
				if (tmp.getProcess_name().equals(processName)
						&& tmp.getProcess_ID().equals(processID)
						&& tmp.getScopeID().equals(selfScopeID)) {
					remove = tmp;
				}
			}
			if (remove != null) {
				RunningScopes.remove(remove);
			}
		}
	}

	public void addCompensationHandler(Compensation_Handler tmp_handler) {
		synchronized (CompensationHandlers) {
			CompensationHandlers.add(tmp_handler);
		}
	}

	public void removeCompensationHandlers(Long processID) {
		synchronized (CompensationHandlers) {
			ArrayList<Compensation_Handler> removeList = new ArrayList<Compensation_Handler>();
			Iterator<Compensation_Handler> itr = CompensationHandlers
					.iterator();
			while (itr.hasNext()) {
				Compensation_Handler tmp_comp = itr.next();
				if (tmp_comp.getProcess_ID().equals(processID)) {
					removeList.add(tmp_comp);
				}
			}
			CompensationHandlers.removeAll(removeList);
		}
	}

	public void removeRunningActivities(Long processID) {
		synchronized (RunningActivities) {
			ArrayList<Running_Activity> removeList3 = new ArrayList<Running_Activity>();

			Iterator<Running_Activity> itr = RunningActivities.iterator();
			while (itr.hasNext()) {
				Running_Activity tmp = itr.next();
				if (tmp.getProcessID().equals(processID)) {
					removeList3.add(tmp);
				}
			}

			RunningActivities.removeAll(removeList3);
		}
	}

	public void removeRunningScopes(Long processID) {
		synchronized (RunningScopes) {
			ArrayList<Running_Scope> removeList2 = new ArrayList<Running_Scope>();

			Iterator<Running_Scope> itr = RunningScopes.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp = itr.next();
				if (tmp.getProcess_ID().equals(processID)) {
					removeList2.add(tmp);
				}
			}

			RunningScopes.removeAll(removeList2);
		}
	}

	// @hahnml: To realize the blocking of a user defined set of activities by
	// their XPath values, this method was extended.
	public Boolean isBlocking(String event_type, Long processID,
			String act_xpath) {
		Boolean blocking = false;
		synchronized (block.getInstanceBlockingEvents()) {
			BlockingEventsInstance tmpX = null;

			Iterator<BlockingEventsInstance> itr = block
					.getInstanceBlockingEvents().iterator();
			while (itr.hasNext()) {
				BlockingEventsInstance temp = itr.next();
				if (processID.equals(temp.getID())) {
					tmpX = temp;
					break;
				}
			}
			if (tmpX != null) {
				// @hahnml: Get the BpelRuntimeContext from the process SCOPE
				// and create a new EvaluationContext to evaluate
				// breakpoint conditions
				SCOPE processScope = getRunningProcessSCOPE(processID);
				BpelRuntimeContext runtime = processScope
						.getBpelRuntimeContext();
				EvaluationContext evalContext = new ExprEvaluationContextImpl(
						processScope._scopeFrame, runtime);

				if (event_type.equals("Activity_Ready")) {
					blocking = tmpX.getBlockEvents().Activity_Ready;
					// @hahnml: If a blocking event is set to the instance, we
					// don't check the activity blocking events, because they
					// have a lower priority
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Activity_Ready;

						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Activity_Executed")) {
					blocking = tmpX.getBlockEvents().Activity_Executed;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Activity_Executed;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Activity_Faulted")) {
					blocking = tmpX.getBlockEvents().Activity_Faulted;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Activity_Faulted;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type
						.equals("Evaluating_TransitionCondition_Faulted")) {
					blocking = tmpX.getBlockEvents().Evaluating_TransitionCondition_Faulted;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Evaluating_TransitionCondition_Faulted;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				}
				/*
				 * else if (event_type.equals("FaultHandling_NoHandler")) {
				 * blocking = tmpX.getBlockEvents().FaultHandling_NoHandler; }
				 */
				else if (event_type.equals("Link_Evaluated")) {
					blocking = tmpX.getBlockEvents().Link_Evaluated;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Link_Evaluated;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Loop_Condition_False")) {
					blocking = tmpX.getBlockEvents().Loop_Condition_False;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Loop_Condition_False;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Loop_Condition_True")) {
					blocking = tmpX.getBlockEvents().Loop_Condition_True;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Loop_Condition_True;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Loop_Iteration_Complete")) {
					blocking = tmpX.getBlockEvents().Loop_Iteration_Complete;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Loop_Iteration_Complete;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Scope_Compensating")) {
					blocking = tmpX.getBlockEvents().Scope_Compensating;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Scope_Compensating;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Scope_Complete_With_Fault")) {
					blocking = tmpX.getBlockEvents().Scope_Complete_With_Fault;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Scope_Complete_With_Fault;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Scope_Handling_Fault")) {
					blocking = tmpX.getBlockEvents().Scope_Handling_Fault;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Scope_Handling_Fault;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (event_type.equals("Scope_Handling_Termination")) {
					blocking = tmpX.getBlockEvents().Scope_Handling_Termination;
					// @hahnml
					if (!blocking
							&& tmpX.getActivityBlockEvents().containsKey(
									act_xpath)) {
						blocking = tmpX.getActivityBlockEvents().get(act_xpath).Scope_Handling_Termination;
						
						// @hahnml: Check if the blocking event has a condition
						// and evaluate it
						if (blocking && tmpX.getActivityEventConditions().containsKey(
								act_xpath)) {
							try {
								blocking = runtime
										.getExpLangRuntime()
										.evaluateAsBoolean(
												tmpX.getActivityEventConditions()
														.get(act_xpath),
												evalContext);
							} catch (FaultException e) {
								e.printStackTrace();
							} catch (EvaluationException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
		return blocking;
	}

	// possibly blocking
	public void Activity_Ready(String act_name, String act_xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, TerminationChannel termChan,
			ActivityReady bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Activity_Ready!?%&$!" + ID + "!?%&$!" + act_xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);

		QName procName = comm.cropQName(processName);

		String termName = termChan.export();

		// activity is now running
		Running_Activity tmp = new Running_Activity(processID, scope_ID,
				act_xpath, termChan, termName);
		addRunningActivity(tmp);

		// @hahnml
		updateActivityStatus(
				processID,
				act_xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.running);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Ready", processID, act_xpath);
		
		if (!blocking || artificial) // nobody is blocking this event
		{

			if (!artificial) {
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("ODE - not blocking activity " + act_xpath + " in process " + processID);
				}
				Activity_Ready message = new Activity_Ready();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						act_xpath, act_name);
				comm.sendMessageToTopic(message);
			}

			signal.linkStatus(true);

		} else // someone is blocking this event
		{
			Activity_Ready message = new Activity_Ready();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null,
					act_xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), act_xpath, scope_ID,
					processID, true, bpelEvent, version);
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("ODE - blocking activity " + act_xpath + " in process " + processID);
				System.out.println("Send message with id " + message.getMessageID());
			}
			comm.sendMessageToTopic(message);
		}

	}

	// not blocking
	public void Activity_Executing(String act_name, String act_xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope) {
		logger.fine("Activity_Executing!?%&$!" + ID + "!?%&$!" + act_xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);

		QName procName = comm.cropQName(processName);
		if (!artificial) {
			Activity_Executing message = new Activity_Executing();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null,
					act_xpath, act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Activity_Faulted(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, FaultData fault_data,
			Boolean artificial, Boolean isScope, ActivityFaulted bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Activity_Faulted!?%&$!" + ID + "!?%&$!" + xpath + "!?%&$!"
				+ processName + "!?%&$!" + processID + "!?%&$!" + scope_ID);
		QName procName = comm.cropQName(processName);

		QName faultName = fault_data.getFaultName();
		Element faultMessage = fault_data.getFaultMessage();
		QName messageType = null;
		QName elementType = null;
		OVarType tmp = fault_data.getFaultType();
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

		if (fault_data.getElemType() != null) {
			elementType = fault_data.getElemType();
		}
		if (fault_data.getMessType() != null) {
			messageType = fault_data.getMessType();
		}

		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.faulted);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Faulted", processID, xpath);

		String fltMessage = convertElementToString(faultMessage);
		String fltMessage2 = fltMessage;
		if (fltMessage != null) {
			int index = fltMessage.indexOf(">");
			fltMessage2 = fltMessage.substring(index + 1);
		}

		if (!blocking || artificial) // nobody is blocking this event
		{
			if (!artificial) {
				Activity_Faulted message = new Activity_Faulted();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						xpath, act_name);
				message.setFaultName(faultName);
				message.setFaultMsg(fltMessage2);
				message.setExplanation(fault_data.getExplanation());
				message.setMessageType(messageType);
				message.setElementType(elementType);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Activity_Faulted message = new Activity_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Evaluating_TransitionCondition_Faulted(String act_name,
			String xpath, Long ID, String scope_xpath, Long scope_ID,
			QName processName, Long processID, LinkStatusChannel signal,
			FaultData fault_data, Boolean artificial, Boolean isScope,
			EvaluatingTransitionConditionFaulted bpelEvent, String linkXPath) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Evaluating_TransitionCondition_Faulted!?%&$!" + ID
				+ "!?%&$!" + xpath + "!?%&$!" + processName + "!?%&$!"
				+ processID + "!?%&$!" + scope_ID + "!?%&$!" + linkXPath);
		QName procName = comm.cropQName(processName);

		QName faultName = fault_data.getFaultName();
		Element faultMessage = fault_data.getFaultMessage();
		QName messageType = null;
		QName elementType = null;
		OVarType tmp = fault_data.getFaultType();
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

		if (fault_data.getElemType() != null) {
			elementType = fault_data.getElemType();
		}
		if (fault_data.getMessType() != null) {
			messageType = fault_data.getMessType();
		}

		// is this event blocking?
		Boolean blocking = isBlocking("Evaluating_TransitionCondition_Faulted",
				processID, xpath);

		String fltMessage = convertElementToString(faultMessage);
		String fltMessage2 = fltMessage;
		if (fltMessage != null) {
			int index = fltMessage.indexOf(">");
			fltMessage2 = fltMessage.substring(index + 1);
		}

		if (!blocking) // nobody is blocking this event
		{
			Evaluating_TransitionCondition_Faulted message = new Evaluating_TransitionCondition_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			message.setLinkXPath(linkXPath);
			comm.sendMessageToTopic(message);

			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Evaluating_TransitionCondition_Faulted message = new Evaluating_TransitionCondition_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			message.setLinkXPath(linkXPath);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Activity_Executed(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, ActivityExecuted bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Activity_Executed!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);
		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Executed", processID, xpath);

		if (!blocking || artificial) // nobody is blocking this event
		{
			if (!artificial) {
				Activity_Executed message = new Activity_Executed();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Activity_Executed message = new Activity_Executed();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Activity_Complete(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope) {
		if (Constants.DEBUG_LEVEL > 0) {
			System.out.println("ActivityEventHandler - Starting Activity_Complete Event for activity " + xpath + " and process " + processID);
		}
		
		logger.fine("Activity_Complete!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);
		QName procName = comm.cropQName(processName);

		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.completed);

		if (!artificial) {
			Activity_Complete message = new Activity_Complete();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			comm.sendMessageToTopic(message);
			if (Constants.DEBUG_LEVEL > 0) {
				System.out.println("ActivityEventHandler - Sending Complete Message for activity " + xpath + " and process " + processID + " to topic");
			}
		}
	}
	
	//krawczls: A method for skipping an activity.
	//          Sets the Activity_Status of that activity to uncommittedskipped.
	public void Activity_Skipped(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope) {
		
		logger.fine("Activity_Skipped!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);
		
		QName procName = comm.cropQName(processName);

		removeRunningActivity(processID, scope_ID, xpath);
		
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.skipped);
		
		if (!artificial) {
			Activity_Skipped message = new Activity_Skipped();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			comm.sendMessageToTopic(message);
		}
		
	}
	
	// not blocking
	public void Activity_Terminated(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope) {
		logger.fine("Activity_Terminated!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);
		QName procName = comm.cropQName(processName);

		removeRunningActivity(processID, scope_ID, xpath);
		
		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.terminated);

		if (!artificial) {
			Activity_Terminated message = new Activity_Terminated();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Activity_Dead_Path(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope) {
		logger.fine("Activity_Dead_Path!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);
		QName procName = comm.cropQName(processName);

		if (!artificial || isScope) {
			Activity_Dead_Path message = new Activity_Dead_Path();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Activity_Join_Failure(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			FaultData fault, LinkStatusChannel signal,
			ActivityJoinFailure bpelEvent, Boolean suppressJF) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Activity_Faulted!?%&$!" + ID + "!?%&$!" + xpath + "!?%&$!"
				+ processName + "!?%&$!" + processID + "!?%&$!" + scope_ID);
		QName procName = comm.cropQName(processName);

		QName faultName = null;
		Element faultMessage = null;
		QName messageType = null;
		QName elementType = null;

		if (fault != null) {
			faultName = fault.getFaultName();
			faultMessage = fault.getFaultMessage();
			OVarType tmp = fault.getFaultType();
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

			if (fault.getElemType() != null) {
				elementType = fault.getElemType();
			}
			if (fault.getMessType() != null) {
				messageType = fault.getMessType();
			}
		}

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Faulted", processID, xpath);

		String fltMessage = convertElementToString(faultMessage);
		String fltMessage2 = fltMessage;
		if (fltMessage != null) {
			int index = fltMessage.indexOf(">");
			fltMessage2 = fltMessage.substring(index + 1);
		}

		if (!blocking) // nobody is blocking this event
		{
			Activity_Faulted message = new Activity_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			comm.sendMessageToTopic(message);
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Activity_Faulted message = new Activity_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Iteration_Complete(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, IterationComplete bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Iteration_Complete!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Loop_Iteration_Complete", processID,
				xpath);

		if (!blocking || artificial) {

			if (!artificial) {
				Loop_Iteration_Complete message = new Loop_Iteration_Complete();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Loop_Iteration_Complete message = new Loop_Iteration_Complete();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Loop_Condition_True(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, LoopConditionTrue bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Loop_Condition_True!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Loop_Condition_True", processID, xpath);

		if (!blocking || artificial) // wenn niemand das Event blockiert
		{

			if (!artificial) {
				Loop_Condition_True message = new Loop_Condition_True();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Loop_Condition_True message = new Loop_Condition_True();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Loop_Condition_False(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, LoopConditionFalse bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Loop_Condition_False!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Loop_Condition_False", processID, xpath);

		if (!blocking || artificial) // wenn niemand das Event blockiert
		{

			if (!artificial) {
				Loop_Condition_False message = new Loop_Condition_False();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath, null,
						xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Loop_Condition_False message = new Loop_Condition_False();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, null, xpath,
					act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scope_ID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Link_Ready(String xpath, String name, String xpath_scope,
			Long scopeID, QName pr_name, Long pr_id, String src_xpath,
			String tr_xpath) {
		logger.fine("Link_Ready!?%&$!" + name + "!?%&$!" + xpath + "!?%&$!"
				+ pr_name + "!?%&$!" + pr_id + "!?%&$!" + scopeID);

		QName procName = comm.cropQName(pr_name);

		Link_Ready message = new Link_Ready();
		comm.fillLinkEventMessage(message, genCon.getTimestamp(), procName,
				pr_id, scopeID, xpath_scope, name, xpath);
		comm.sendMessageToTopic(message);
	}

	// possibly blocking
	public void Link_Evaluated(String xpath, String name, String xpath_scope,
			Long scopeID, QName pr_name, Long pr_id, String src_xpath,
			String tr_xpath, Boolean value, LinkStatusChannel channel,
			LinkEvaluated bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Link_Evaluated!?%&$!" + name + "!?%&$!" + xpath + "!?%&$!"
				+ pr_name + "!?%&$!" + pr_id + "!?%&$!" + scopeID + "!?%&$!"
				+ value);

		QName procName = comm.cropQName(pr_name);

		// is this event blocking?
		Boolean blocking = isBlocking("Link_Evaluated", pr_id, xpath);

		if (!blocking) // no one is blocking this event
		{

			Link_Evaluated message = new Link_Evaluated();
			comm.fillLinkEventMessage(message, genCon.getTimestamp(), procName,
					pr_id, scopeID, xpath_scope, name, xpath);
			message.setValue(value);
			comm.sendMessageToTopic(message);

			channel.linkStatus(value);

			// fire event Link_Set_True or Link_Set_False
			if (value) {
				logger.fine("Link_Set_True!?%&$!" + name + "!?%&$!" + xpath
						+ "!?%&$!" + pr_name + "!?%&$!" + pr_id + "!?%&$!"
						+ scopeID);

				Link_Set_True message2 = new Link_Set_True();
				comm.fillLinkEventMessage(message2, genCon.getTimestamp(),
						procName, pr_id, scopeID, xpath_scope, name, xpath);
				comm.sendMessageToTopic(message2);
			} else {
				logger.fine("Link_Set_False!?%&$!" + name + "!?%&$!" + xpath
						+ "!?%&$!" + pr_name + "!?%&$!" + pr_id + "!?%&$!"
						+ scopeID);

				Link_Set_False message2 = new Link_Set_False();
				comm.fillLinkEventMessage(message2, genCon.getTimestamp(),
						procName, pr_id, scopeID, xpath_scope, name, xpath);
				comm.sendMessageToTopic(message2);
			}
		} else // someone is blocking this event
		{
			Link_Evaluated message = new Link_Evaluated();
			comm.fillLinkEventMessage(message, genCon.getTimestamp(), procName,
					pr_id, scopeID, xpath_scope, name, xpath);
			message.setValue(value);
			message.setBlocking(true);
			Long version = ieh.getVersion(pr_id);
			String link_name = channel.export();
			bpelEvent.setLink_name2(link_name);

			addBlockingEvent(message.getMessageID(), xpath, scopeID, pr_id,
					false, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// @stmz: for scope events: if ignore = true, then do not send the message
	// over JMS
	// possibly blocking
	public void Scope_Activity_Ready(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, TerminationChannel termChannel,
			ParentScopeChannel faultChannel, OScope oscope, SCOPE scope,
			Boolean ignore, ScopeActivityReady bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Activity_Ready!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		String termName = termChannel.export();
		String faultChanName = faultChannel.export();

		Running_Scope tmp = new Running_Scope(processName, processID,
				selfScopeID, termChannel, faultChannel, oscope, scope,
				faultChanName);
		addRunningScope(tmp);

		Running_Activity tmp2 = new Running_Activity(processID, selfScopeID,
				xpath, termChannel, termName);
		addRunningActivity(tmp2);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.running);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Ready", processID, xpath);

		if (!blocking || ignore) {

			if (!ignore) {
				Activity_Ready message = new Activity_Ready();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Activity_Ready message = new Activity_Ready();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// not blocking
	public void Scope_Activity_Terminated(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Activity_Terminated!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		removeRunningScope(processName, processID, selfScopeID);
		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.terminated);

		if (!ignore) {
			Activity_Terminated message = new Activity_Terminated();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}

	}
	
	//@krawczls:
	public void Scope_Activity_Skipped(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Activity_Skipped!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		removeRunningScope(processName, processID, selfScopeID);
		removeRunningActivity(processID, scope_ID, xpath);

		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.skipped);

		if (!ignore) {
			Activity_Skipped message = new Activity_Skipped();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}

	}
	
	// not blocking
	public void Scope_Activity_Executing(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Activity_Executing!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		if (!ignore) {
			Activity_Executing message = new Activity_Executing();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Scope_Handling_Event(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Handling_Event!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		if (!ignore) {
			Scope_Handling_Event message = new Scope_Handling_Event();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Scope_Handling_Termination(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore, LinkStatusChannel signal,
			ScopeHandlingTermination bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Handling_Termination!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Scope_Handling_Termination", processID,
				xpath);

		if (!blocking || ignore) {
			if (!ignore) {
				Scope_Handling_Termination message = new Scope_Handling_Termination();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Scope_Handling_Termination message = new Scope_Handling_Termination();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Scope_Event_Handling_Ended(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Event_Handling_Ended!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		if (!ignore) {
			Scope_Event_Handling_Ended message = new Scope_Event_Handling_Ended();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Scope_Handling_Fault(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, QName fault, Element msg,
			QName msgType, QName elemType, Boolean ignore,
			ScopeHandlingFault bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Handling_Fault!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID + "!?%&$!" + fault
				+ "!?%&$!" + msg + "!?%&$!" + msgType + "!?%&$!" + elemType);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Scope_Handling_Fault", processID, xpath);

		String fltMessage = convertElementToString(msg);
		String fltMessage2 = fltMessage;
		if (fltMessage != null) {
			int index = fltMessage.indexOf(">");
			fltMessage2 = fltMessage.substring(index + 1);
		}

		if (!blocking || ignore) {

			if (!ignore) {
				Scope_Handling_Fault message = new Scope_Handling_Fault();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				message.setFaultName(fault);
				message.setFaultMsg(fltMessage2);
				message.setMessageType(msgType);
				message.setElementType(elemType);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Scope_Handling_Fault message = new Scope_Handling_Fault();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setFaultName(fault);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(msgType);
			message.setElementType(elemType);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	/*
	 * public void FaultHandling_NoHandler(String xpath, Long ID, String
	 * scope_xpath, Long scope_ID, QName processName, Long processID,
	 * LinkStatusChannel signal, Boolean artificial, Boolean isScope, Long
	 * selfScopeID, Boolean ignore, FaultHandlingNoHandler bpelEvent) { ieh =
	 * InstanceEventHandler.getInstance();
	 * logger.fine("FaultHandling_NoHandler!?%&$!" + ID + "!?%&$!" + xpath +
	 * "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!" + scope_ID +
	 * "!?%&$!" + selfScopeID);
	 * 
	 * QName procName = comm.cropQName(processName);
	 * 
	 * //is this event blocking? Boolean blocking =
	 * isBlocking("FaultHandling_NoHandler", processID);
	 * 
	 * if (!blocking || ignore) {
	 * 
	 * if (!ignore) { FaultHandling_NoHandler message = new
	 * FaultHandling_NoHandler(); comm.fillActivityEventMessage(message,
	 * genCon.getTimestamp(), procName, processID, scope_ID, scope_xpath,
	 * selfScopeID, xpath); comm.sendMessageToTopic(message); }
	 * signal.linkStatus(true); } else //someone is blocking this event {
	 * FaultHandling_NoHandler message = new FaultHandling_NoHandler();
	 * comm.fillActivityEventMessage(message, genCon.getTimestamp(), procName,
	 * processID, scope_ID, scope_xpath, selfScopeID, xpath);
	 * message.setBlocking(true); Long version = ieh.getVersion(processID);
	 * String link_name = signal.export(); bpelEvent.setLink_name(link_name);
	 * 
	 * addBlockingEvent(message.getMessageID(), xpath, selfScopeID, processID,
	 * true, bpelEvent, version); comm.sendMessageToTopic(message); }
	 * 
	 * }
	 */

	// possibly blocking
	public void Scope_Activity_Faulted(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, Boolean ignore,
			FaultData fault_data, ScopeActivityFaulted bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Activity_Faulted!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		QName faultName = fault_data.getFaultName();
		Element faultMessage = fault_data.getFaultMessage();
		QName messageType = null;
		QName elementType = null;
		OVarType tmp = fault_data.getFaultType();
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

		if (fault_data.getElemType() != null) {
			elementType = fault_data.getElemType();
		}
		if (fault_data.getMessType() != null) {
			messageType = fault_data.getMessType();
		}

		removeRunningScope(processName, processID, selfScopeID);
		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.faulted);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Faulted", processID, xpath);

		String fltMessage = convertElementToString(faultMessage);
		String fltMessage2 = fltMessage;
		if (fltMessage != null) {
			int index = fltMessage.indexOf(">");
			fltMessage2 = fltMessage.substring(index + 1);
		}

		if (!blocking || ignore) {

			if (!ignore) {
				Activity_Faulted message = new Activity_Faulted();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				message.setFaultName(faultName);
				message.setFaultMsg(fltMessage2);
				message.setMessageType(messageType);
				message.setElementType(elementType);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);

		} else // someone is blocking this event
		{
			Activity_Faulted message = new Activity_Faulted();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setFaultName(faultName);
			message.setFaultMsg(fltMessage2);
			message.setMessageType(messageType);
			message.setElementType(elementType);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Scope_Complete_With_Fault(String act_name, String xpath,
			Long ID, String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, Boolean ignore,
			ScopeCompleteWithFault bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Complete_With_Fault!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		removeRunningScope(processName, processID, selfScopeID);
		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.faulted);

		// is this event blocking?
		Boolean blocking = isBlocking("Scope_Complete_With_Fault", processID,
				xpath);

		if (!blocking || ignore) {

			if (!ignore) {
				Scope_Complete_With_Fault message = new Scope_Complete_With_Fault();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);

		} else // someone is blocking this event
		{
			Scope_Complete_With_Fault message = new Scope_Complete_With_Fault();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// possibly blocking
	public void Scope_Activity_Executed(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, Boolean ignore,
			ScopeActivityExecuted bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Activity_Executed!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Activity_Executed", processID, xpath);

		if (!blocking || ignore) {
			if (!ignore) {
				Activity_Executed message = new Activity_Executed();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Activity_Executed message = new Activity_Executed();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// not blocking
	public void Scope_Activity_Complete(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, COMPENSATIONHANDLER_ comp, Boolean ignore) {
		logger.fine("Scope_Activity_Complete!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		removeRunningScope(processName, processID, selfScopeID);
		removeRunningActivity(processID, scope_ID, xpath);

		// @hahnml
		updateActivityStatus(
				processID,
				xpath,
				org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus.completed);

		if (comp != null) {
			String compChannelName = comp._self.getCompChannel().export();
			Compensation_Handler tmp_handler = new Compensation_Handler(
					selfScopeID, processName, processID, comp, compChannelName);
			addCompensationHandler(tmp_handler);
		}

		if (!ignore) {
			Activity_Complete message = new Activity_Complete();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// possibly blocking
	public void Scope_Compensating(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, LinkStatusChannel signal, Boolean artificial,
			Boolean isScope, Long selfScopeID, Boolean ignore,
			ScopeCompensating bpelEvent) {
		ieh = InstanceEventHandler.getInstance();
		logger.fine("Scope_Compensating!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		// is this event blocking?
		Boolean blocking = isBlocking("Scope_Compensating", processID, xpath);

		if (!blocking || ignore) {

			if (!ignore) {
				Scope_Compensating message = new Scope_Compensating();
				comm.fillActivityEventMessage(message, genCon.getTimestamp(),
						procName, processID, scope_ID, scope_xpath,
						selfScopeID, xpath, act_name);
				comm.sendMessageToTopic(message);
			}
			signal.linkStatus(true);
		} else // someone is blocking this event
		{
			Scope_Compensating message = new Scope_Compensating();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			message.setBlocking(true);
			Long version = ieh.getVersion(processID);
			String link_name = signal.export();
			bpelEvent.setLink_name(link_name);

			addBlockingEvent(message.getMessageID(), xpath, selfScopeID,
					processID, true, bpelEvent, version);
			comm.sendMessageToTopic(message);
		}

	}

	// not blocking
	public void Scope_Compensated(String act_name, String xpath, Long ID,
			String scope_xpath, Long scope_ID, QName processName,
			Long processID, Boolean artificial, Boolean isScope,
			Long selfScopeID, Boolean ignore) {
		logger.fine("Scope_Compensated!?%&$!" + ID + "!?%&$!" + xpath
				+ "!?%&$!" + processName + "!?%&$!" + processID + "!?%&$!"
				+ scope_ID + "!?%&$!" + selfScopeID);

		QName procName = comm.cropQName(processName);

		if (!ignore) {
			Scope_Compensated message = new Scope_Compensated();
			comm.fillActivityEventMessage(message, genCon.getTimestamp(),
					procName, processID, scope_ID, scope_xpath, selfScopeID,
					xpath, act_name);
			comm.sendMessageToTopic(message);
		}
	}

	// not blocking
	public void Variable_Modification_Event(String varName, String varXpath,
			String act_xpath, Long act_id, String scope_xpath, Long ScopeID,
			QName processName, Long processID, Node newValue) {

		String val;
		String value;
		if (varXpath != null) {
			val = convertToString(newValue);
			int index = val.indexOf(">");
			value = val.substring(index + 1);
		} else {
			value = newValue.getTextContent();
			// value = newValue.toString();
		}

		logger.fine("Variable_Modification!?%&$!" + act_xpath + "!?%&$!"
				+ act_id + "!?%&$!" + ScopeID + "!?%&$!" + processName
				+ "!?%&$!" + processID + "!?%&$!" + value + "!?%&$!" + varName
				+ "!?%&$!" + varXpath);

		QName procName = comm.cropQName(processName);

		Variable_Modification message = new Variable_Modification();
		String var_xpath = varXpath;
		if (var_xpath == null) {
			var_xpath = act_xpath;
		}
		comm.fillVariableModificationMessage(message, genCon.getTimestamp(),
				procName, processID, ScopeID, scope_xpath, act_xpath, varName,
				var_xpath, value);
		if (act_xpath == null && act_id == null) {
			message.setChanged_from_outside(true);
		} else {
			message.setChanged_from_outside(false);
		}
		comm.sendMessageToTopic(message);
	}

	// @hahnml: Will be executed if a VariableModificationAtAssignEvent is
	// fired.
	public void Variable_Modification_At_Assign_Event(String varName,
			String varXpath, String act_xpath, Long act_id, String scope_xpath,
			Long ScopeID, QName processName, Long processID, Node newValue,
			int numberOfCopyElement) {

		String val;
		String value;
		if (varXpath != null) {
			val = convertToString(newValue);
			int index = val.indexOf(">");
			value = val.substring(index + 1);
		} else {
			value = newValue.getTextContent();
			// value = newValue.toString();
		}

		logger.fine("Variable_Modification_At_Assign!?%&$!" + act_xpath
				+ "!?%&$!" + act_id + "!?%&$!" + ScopeID + "!?%&$!"
				+ processName + "!?%&$!" + processID + "!?%&$!" + value
				+ "!?%&$!" + varName + "!?%&$!" + varXpath + "!?%&$!"
				+ numberOfCopyElement);

		QName procName = comm.cropQName(processName);

		Variable_Modification_At_Assign message = new Variable_Modification_At_Assign();
		String var_xpath = varXpath;
		if (var_xpath == null) {
			var_xpath = act_xpath;
		}
		comm.fillVariableModificationAtAssignMessage(message,
				genCon.getTimestamp(), procName, processID, ScopeID,
				scope_xpath, act_xpath, varName, var_xpath, value,
				numberOfCopyElement);
		if (act_xpath == null && act_id == null) {
			message.setChanged_from_outside(true);
		} else {
			message.setChanged_from_outside(false);
		}
		comm.sendMessageToTopic(message);
	}

	// not blocking
	public void CorrelationSet_Write_Event(String[] values, QName processName,
			Long processID, Long scopeID, String cset_xpath, String act_xpath,
			Boolean changedOutside) {
		logger.fine("CorrelationSet_Modification!?%&$!" + processName
				+ "!?%&$!" + processID + "!?%&$!" + scopeID + "!?%&$!"
				+ act_xpath + "!?%&$!" + cset_xpath);
		QName procName = comm.cropQName(processName);

		CorrelationSet_Modification message = new CorrelationSet_Modification();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				processID);
		message.setCSet_xpath(cset_xpath);
		message.setActivityXPath(act_xpath);
		message.setValues(values);
		message.setScopeID(scopeID);
		message.setChanged_from_outside(changedOutside);
		comm.sendMessageToTopic(message);
	}

	// not blocking
	public void PartnerLinkModification_Event(String plName, String plXpath,
			String act_xpath, String scope_xpath, Long scopeID,
			QName processName, Long processID, Node newValue) {
		String val;
		String value;

		if (newValue != null) {
			val = convertToString(newValue);
			int index = val.indexOf(">");
			value = val.substring(index + 1);
		} else {
			value = null;
		}

		logger.fine("PartnerLink_Modification!?%&$!" + act_xpath + "!?%&$!"
				+ null + "!?%&$!" + scopeID + "!?%&$!" + processName + "!?%&$!"
				+ processID + "!?%&$!" + value + "!?%&$!" + plName + "!?%&$!"
				+ plXpath);

		QName procName = comm.cropQName(processName);

		PartnerLink_Modification message = new PartnerLink_Modification();
		comm.fillInstanceEventMessage(message, genCon.getTimestamp(), procName,
				processID);
		message.setActivityXPath(act_xpath);
		message.setPlName(plName);
		message.setPlValue(value);
		message.setPlXPath(plXpath);
		message.setScopeID(scopeID);
		message.setScopeXPath(scope_xpath);
		if (act_xpath == null) {
			message.setChanged_from_outside(true);
		}
		comm.sendMessageToTopic(message);
	}

	public void addBlockingEvent(Long msgID, String path, Long scopeid,
			Long prcID, Boolean isAct, BpelEvent event, Long version) {
		BlockingEvent tmp = new BlockingEvent(msgID, path, scopeid, prcID,
				isAct, event, version);
		block.addBlockingEvent(tmp);
	}

	public String convertToString(Node d) {
		Source source = new DOMSource(d);
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		Result result = new StreamResult(outStream);
		Transformer xformer;
		String tmp = "";
		try {
			xformer = TransformerFactory.newInstance().newTransformer();
			xformer.transform(source, result);
			tmp = outStream.toString();
			return tmp;
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		return tmp;
	}

	public String convertElementToString(Element e) {
		if (e == null) {
			return null;
		}
		try {
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer trans = tf.newTransformer();
			StringWriter sw = new StringWriter();
			trans.transform(new DOMSource(e), new StreamResult(sw));
			String theAnswer = sw.toString();
			return theAnswer;
		} catch (Exception ex) {
			return null;
		}
	}

	public List<Running_Scope> getRunningScopes() {
		return RunningScopes;
	}

	public List<Running_Activity> getRunningActivities() {
		return RunningActivities;
	}

	public List<Compensation_Handler> getCompensationHandlers() {
		return CompensationHandlers;
	}

	// @hahnml: Restricted to activities in flows
	public void updateActivityStatus(
			Long processID,
			String Xpath,
			org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus status) {
		if (Xpath != null && Xpath.contains("flow")) {
			synchronized (ActivityStatus) {
				Iterator<Activity_Status> itr = ActivityStatus.iterator();
				boolean found = false;
				while (itr.hasNext() && !found) {
					Activity_Status tmp_act = itr.next();
					if (tmp_act.getProcessID().equals(processID)
							&& tmp_act.getXPath().equals(Xpath)) {
						found = true;
						tmp_act.setActStatus(status);
					}
				}

				if (!found) {
					Activity_Status tmp = new Activity_Status(processID, Xpath,
							status);
					ActivityStatus.add(tmp);
				}
			}
		}
	}

	// @hahnml
	public void removeActivityStatus(Long processID) {
		synchronized (ActivityStatus) {
			ArrayList<Activity_Status> removeList = new ArrayList<Activity_Status>();

			Iterator<Activity_Status> itr = ActivityStatus.iterator();
			while (itr.hasNext()) {
				Activity_Status tmp = itr.next();
				if (tmp.getProcessID().equals(processID)) {
					removeList.add(tmp);
				}
			}

			ActivityStatus.removeAll(removeList);
		}
	}

	// @hahnml
	public org.apache.ode.bpel.extensions.processes.Activity_Status.ActivityStatus getActivityStatus(
			String xpath, Long processID) {
		if (xpath != null) {
			synchronized (ActivityStatus) {
				Iterator<Activity_Status> itr = ActivityStatus.iterator();
				while (itr.hasNext()) {
					Activity_Status tmp_act = itr.next();
					if (tmp_act.getProcessID().equals(processID)
							&& tmp_act.getXPath().equals(xpath)) {
						return tmp_act.getActStatus();
					}
				}
			}
		}

		return null;
	}

	// @hahnml
	public void addRunningFLOW(FLOW flow) {
		if (flow != null) {
			synchronized (FlowRuntimeObjects) {
				FlowRuntimeObjects.add(flow);
			}
		}
	}

	// @hahnml
//	public void updateRunningFLOW(FLOW newFlow) {
//		// Check if the flow is already buffered
//		if (!FlowRuntimeObjects.contains(newFlow)) {
//			FLOW flow = null;
//			Iterator<FLOW> itr = FlowRuntimeObjects.iterator();
//
//			while (flow == null && itr.hasNext()) {
//				FLOW tmp_flow = itr.next();
//				if (tmp_flow.getBufferedProcess_ID().equals(
//						newFlow.getBufferedProcess_ID())
//						&& tmp_flow._self.o.getXpath().equals(
//								newFlow._self.o.getXpath())) {
//					flow = tmp_flow;
//				}
//			}
//
//			if (flow != null) {
//				this.removeRunningFLOW(flow);
//			}
//
//			this.addRunningFLOW(newFlow);
//		}
//	}

	// @hahnml
	public void removeRunningFLOW(FLOW flow) {
		if (flow != null) {
			synchronized (FlowRuntimeObjects) {
				FlowRuntimeObjects.remove(flow);
			}
		}
	}

	// @hahnml
	public FLOW getRunningFlow(Long processID, String xpath) {
		if (processID != null && xpath != null) {
			synchronized (FlowRuntimeObjects) {
				Iterator<FLOW> itr = FlowRuntimeObjects.iterator();
				while (itr.hasNext()) {
					FLOW tmp_flow = itr.next();
					if (tmp_flow.getBufferedProcess_ID().equals(processID)
							&& tmp_flow._self.o.getXpath().equals(xpath)) {
						return tmp_flow;
					}
				}
			}
		}

		return null;
	}

	// @hahnml
	public void addRunningProcessSCOPE(SCOPE scope) {
		if (scope != null) {
			synchronized (ProcessScopeRuntimeObjects) {
				ProcessScopeRuntimeObjects.add(scope);
			}
		}
	}

	// @hahnml
	public void removeRunningProcessSCOPE(Long instanceID) {
		SCOPE scope = getRunningProcessSCOPE(instanceID);
		if (scope != null) {
			synchronized (ProcessScopeRuntimeObjects) {
				ProcessScopeRuntimeObjects.remove(scope);
			}
		}
	}

	// @hahnml
	private SCOPE getRunningProcessSCOPE(Long instanceID) {
		if (instanceID != null) {
			synchronized (ProcessScopeRuntimeObjects) {
				Iterator<SCOPE> itr = ProcessScopeRuntimeObjects.iterator();
				while (itr.hasNext()) {
					SCOPE tmp_scope = itr.next();
					if (tmp_scope.getBufferedProcess_ID().equals(instanceID)) {
						return tmp_scope;
					}
				}
			}
		}

		return null;
	}
}
