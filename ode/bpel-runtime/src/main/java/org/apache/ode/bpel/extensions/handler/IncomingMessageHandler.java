package org.apache.ode.bpel.extensions.handler;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.jms.Destination;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.common.ProcessState;
import org.apache.ode.bpel.compiler.BpelCompiler;
import org.apache.ode.bpel.compiler.BpelCompiler20;
import org.apache.ode.bpel.compiler.CompilerRegistry;
import org.apache.ode.bpel.compiler.bom.BpelObjectFactory;
import org.apache.ode.bpel.compiler.bom.Expression;
import org.apache.ode.bpel.dao.BpelDAOConnection;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.BpelDatabase;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;
import org.apache.ode.bpel.engine.DebuggerSupport;
import org.apache.ode.bpel.evt.BpelEvent;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.extensions.comm.Communication;
import org.apache.ode.bpel.extensions.comm.manager.BlockingEvent;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.BlockingEventsInstance;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.BlockingEventsProcessModel;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.Blocking_Events;
import org.apache.ode.bpel.extensions.comm.manager.BlockingManager.regController;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Compensate_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Complete_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue_Loop;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue_Loop_Execution;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Fault_To_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Finish_Loop_Execution;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Read_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Read_PartnerLink;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Resume_Instance;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Set_Link_State;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Suppress_Fault;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Suspend_Instance;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Terminate_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_CorrelationSet;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_PartnerLink;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.InstanceEventBlocking;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.ModelEventBlocking;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage.Requested_Blocking_Events;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Set_False;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Link_Set_True;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.PartnerLink_Read;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegisterResponseMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.RegistrationInformationMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineOut.Variable_Read;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityJoinFailure;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.EvaluatingTransitionConditionFaulted;
import org.apache.ode.bpel.extensions.events.FaultHandlingNoHandler;
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
import org.apache.ode.bpel.extensions.processes.Active_Process;
import org.apache.ode.bpel.extensions.processes.Compensation_Handler;
import org.apache.ode.bpel.extensions.processes.Deployed_Process;
import org.apache.ode.bpel.extensions.processes.Running_Activity;
import org.apache.ode.bpel.extensions.processes.Running_Scope;
import org.apache.ode.bpel.iapi.ContextException;
import org.apache.ode.bpel.iapi.Scheduler;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.bpel.iapi.Scheduler.JobInfo;
import org.apache.ode.bpel.iapi.Scheduler.JobType;
import org.apache.ode.bpel.o.OExpression;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.pmapi.InstanceNotFoundException;
import org.apache.ode.bpel.pmapi.ProcessingException;
import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.BpelRuntimeContext;
import org.apache.ode.bpel.runtime.CompensationHandler;
import org.apache.ode.bpel.runtime.CorrelationSetInstance;
import org.apache.ode.bpel.runtime.PartnerLinkInstance;
import org.apache.ode.bpel.runtime.ScopeFrame;
import org.apache.ode.bpel.runtime.VariableInstance;
import org.apache.ode.bpel.runtime.channels.CompensationChannel;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.jacob.JacobRunnable;
import org.apache.ode.jacob.SynchChannel;
import org.apache.ode.jacob.vpu.JacobVPU;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.Namespaces;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

//@stmz: handles all incoming events
//contains a list of runnables
public class IncomingMessageHandler {

	private static IncomingMessageHandler instance;

	private static Communication comm;
	private static GenericController genCon;
	private static BlockingManager block;
	private static DeploymentEventHandler deploymentEventHandler;
	private static InstanceEventHandler instanceEventHandler;
	private List runnables;
	private Scheduler scheduler;

	public static Logger logger = Logger.getLogger("Log-XML");

	private IncomingMessageHandler() {
		comm = Communication.getInstance();
		genCon = GenericController.getInstance();
		block = BlockingManager.getInstance();
		deploymentEventHandler = DeploymentEventHandler.getInstance();
		instanceEventHandler = InstanceEventHandler.getInstance();

		// @stmz: list of runnables. every time, we receive an incoming event,
		// we create
		// a runnable. this runnable is fetched in method execute() in
		// BpelRuntimeContextImpl
		runnables = Collections.synchronizedList(new LinkedList<Runnable>());

		System.out.println("IncomingMessageHandler instantiated.");
	}

	public static IncomingMessageHandler getInstance() {
		if (instance == null) {
			instance = new IncomingMessageHandler();
		}
		return instance;
	}

	public void timerEvent(Long ID, QName procName) {
		JobDetails we = new JobDetails();
		we.setInstanceId(ID);
		we.setType(JobType.TIMER2);
		synchronized (block.getProcesses()) {
			we.setInMem(block.getProcesses().get(procName));
		}

		final JobDetails we2 = we;
		try {
			scheduler.execTransaction(new Callable<Void>() {
				public Void call() throws Exception {

					scheduler.schedulePersistedJob(we2, null);
					return null;
				}
			});
		} catch (ContextException e) {
			System.out.println(e);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	// @stmz: add runnable to list
	public void addRunnable(Long id, JacobRunnable jr) {
		Runnable tmp = new Runnable(id, jr);
		synchronized (runnables) {
			runnables.add(tmp);
		}
	}

	// @stmz: get a runnable, that belongs to a specific process id
	// removes this runnable from the list
	public JacobRunnable getRunnable(Long id) {
		JacobRunnable temp = null;
		Runnable tmp2 = null;
		synchronized (runnables) {
			Iterator<Runnable> itr = runnables.iterator();
			while (itr.hasNext()) {
				Runnable rnr = itr.next();
				if (rnr.processID.equals(id)) {
					tmp2 = rnr;
					break;
				}
			}
			if (tmp2 != null) {
				runnables.remove(tmp2);
				temp = tmp2.runnable;
			}
		}
		return temp;
	}

	// @stmz: get a blocking event, find it via its unique message id
	// remove this blocking event from the list
	public BlockingEvent getEvent(Long msgID) {
		BlockingEvent tmp = null;
		synchronized (block.getBlockingEvents()) {
			Iterator<BlockingEvent> itr = block.getBlockingEvents().iterator();
			while (itr.hasNext()) {
				BlockingEvent event = itr.next();
				if (event.getMsgID().equals(msgID)) {
					tmp = event;
					break;
				}
			}
			if (tmp != null) {
				block.getBlockingEvents().remove(tmp);
			}
		}
		return tmp;
	}

	// Incoming Event Complete_Activity
	public void Complete_Activity(Complete_Activity incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof ActivityReady) {
				final ActivityReady event = (ActivityReady) tmp.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 32431036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ActivityExecuted) {
				final ActivityExecuted event = (ActivityExecuted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 7771036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}

			}

			else if (tmp.getBpelEvent() instanceof ScopeActivityExecuted) {
				final ScopeActivityExecuted event = (ScopeActivityExecuted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 5656536745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}

			}

			else if (tmp.getBpelEvent() instanceof ScopeActivityReady) {
				final ScopeActivityReady event = (ScopeActivityReady) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 7676666036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}

	}

	// Incoming Event Complete_NoFaultHandling
	/*
	 * public void Complete_NoFaultHandling(Complete_NoFaultHandling incEvent) {
	 * BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());
	 * 
	 * if (tmp != null) { if (tmp.getBpelEvent() instanceof
	 * FaultHandlingNoHandler) { final FaultHandlingNoHandler event =
	 * (FaultHandlingNoHandler) tmp.getBpelEvent();
	 * 
	 * JacobRunnable runnable = new JacobRunnable() { private static final long
	 * serialVersionUID = 5557771036745L;
	 * 
	 * public void run() { LinkStatusChannel chan =
	 * importChannel(event.getLink_name(), LinkStatusChannel.class);
	 * chan.linkStatus(true); }
	 * 
	 * };
	 * 
	 * QName name = instanceEventHandler.getQName(event.getProcess_ID()); if
	 * (name != null) { addRunnable(event.getProcess_ID(), runnable);
	 * timerEvent(event.getProcess_ID(), name); } }
	 * 
	 * else { System.out.println("");
	 * System.out.println("Incoming Message made no sense. Possibly deadlocked now."
	 * ); System.out.println(""); unblock(tmp.getBpelEvent()); } } else {
	 * System.out.println("");
	 * System.out.println("No blocking Message found. Possibly deadlocked now."
	 * ); System.out.println(""); } }
	 */

	// Incoming Event Complete_WithFaultHandling
	/*
	 * public void Complete_WithFaultHandling(Complete_WithFaultHandling
	 * incEvent) { BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());
	 * 
	 * if (tmp != null) { if (tmp.getBpelEvent() instanceof
	 * FaultHandlingNoHandler) { final FaultHandlingNoHandler event =
	 * (FaultHandlingNoHandler) tmp.getBpelEvent();
	 * 
	 * JacobRunnable runnable = new JacobRunnable() { private static final long
	 * serialVersionUID = 111117771036745L;
	 * 
	 * public void run() { LinkStatusChannel chan =
	 * importChannel(event.getLink_name(), LinkStatusChannel.class);
	 * chan.linkStatus(false); }
	 * 
	 * };
	 * 
	 * QName name = instanceEventHandler.getQName(event.getProcess_ID()); if
	 * (name != null) { addRunnable(event.getProcess_ID(), runnable);
	 * timerEvent(event.getProcess_ID(), name); } }
	 * 
	 * else { System.out.println("");
	 * System.out.println("Incoming Message made no sense. Possibly deadlocked now."
	 * ); System.out.println(""); unblock(tmp.getBpelEvent()); } } else {
	 * System.out.println("");
	 * System.out.println("No blocking Message found. Possibly deadlocked now."
	 * ); System.out.println(""); } }
	 */

	// Incoming event Continue_Loop_Execution
	public void Continue_Loop_Execution(Continue_Loop_Execution incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof LoopConditionTrue) {
				final LoopConditionTrue event = (LoopConditionTrue) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 65657745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof LoopConditionFalse) {
				final LoopConditionFalse event = (LoopConditionFalse) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 56455556745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}

	}

	// Incoming event Continue_Loop
	public void Continue_Loop(Continue_Loop incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof IterationComplete) {
				final IterationComplete event = (IterationComplete) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 244242455L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}

	// Incoming event Continue
	public void Continue(Continue incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof ActivityFaulted) {
				final ActivityFaulted event = (ActivityFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 547545L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ActivityJoinFailure) {
				final ActivityJoinFailure event = (ActivityJoinFailure) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 43646466745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeActivityFaulted) {
				final ScopeActivityFaulted event = (ScopeActivityFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 12213245L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof EvaluatingTransitionConditionFaulted) {
				final EvaluatingTransitionConditionFaulted event = (EvaluatingTransitionConditionFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 6566565L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeCompensating) {
				final ScopeCompensating event = (ScopeCompensating) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 333345L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeCompleteWithFault) {
				final ScopeCompleteWithFault event = (ScopeCompleteWithFault) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 46464564745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeHandlingFault) {
				final ScopeHandlingFault event = (ScopeHandlingFault) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 6444443745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeHandlingTermination) {
				final ScopeHandlingTermination event = (ScopeHandlingTermination) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 4364445036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}

	// Incoming event Finish_Loop_Execution
	public void Finish_Loop_Execution(Finish_Loop_Execution incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof LoopConditionTrue) {
				final LoopConditionTrue event = (LoopConditionTrue) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 122233211745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof LoopConditionFalse) {
				final LoopConditionFalse event = (LoopConditionFalse) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 34564555L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}

	// Incoming event Set_Link_State
	public void Set_Link_State(Set_Link_State incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof LinkEvaluated) {
				final LinkEvaluated event = (LinkEvaluated) tmp.getBpelEvent();
				final boolean bool = (boolean) incEvent.getValue();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 648867868885L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name2(), LinkStatusChannel.class);
						chan.linkStatus(bool);

						// send event Link_Set_True or Link_Set_False
						if (bool) {
							logger.fine("Link_Set_True!?%&$!"
									+ event.getLink_name() + "!?%&$!"
									+ event.getXPath_Link() + "!?%&$!"
									+ event.getProcess_name() + "!?%&$!"
									+ event.getProcess_ID() + "!?%&$!"
									+ event.getID_scope());

							Link_Set_True message2 = new Link_Set_True();
							comm.fillLinkEventMessage(message2,
									genCon.getTimestamp(),
									event.getProcess_name(),
									event.getProcess_ID(), event.getID_scope(),
									event.getXpath_surrounding_scope(),
									event.getLink_name(), event.getXPath_Link());
							comm.sendMessageToTopic(message2);
						} else {
							logger.fine("Link_Set_False!?%&$!"
									+ event.getLink_name() + "!?%&$!"
									+ event.getXPath_Link() + "!?%&$!"
									+ event.getProcess_name() + "!?%&$!"
									+ event.getProcess_ID() + "!?%&$!"
									+ event.getID_scope());

							Link_Set_False message2 = new Link_Set_False();
							comm.fillLinkEventMessage(message2,
									genCon.getTimestamp(),
									event.getProcess_name(),
									event.getProcess_ID(), event.getID_scope(),
									event.getXpath_surrounding_scope(),
									event.getLink_name(), event.getXPath_Link());
							comm.sendMessageToTopic(message2);
						}

					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			} else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}
	
	//@krawczls: Incoming Event Skip_Activity
	public void Skip_Activity(Skip_Activity incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof ActivityReady) {
				final ActivityReady event = (ActivityReady) tmp.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 32431036745L;

					public void run() {						
						TerminationChannel termChan = event.getTermChan();
						termChan.skip();
					}
				};
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeActivityReady) {
				final ScopeActivityReady event = (ScopeActivityReady) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 7676666036745L;

					public void run() {
						TerminationChannel termChan = event.getTermChannel();
						termChan.skip();
					}
				};
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}
			else {
				System.out.println("");
				System.out.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}
	
	// Incoming Event Start_Activity
	public void Start_Activity(Start_Activity incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());
		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof ActivityReady) {

				final ActivityReady event = (ActivityReady) tmp.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 787611036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}

			} else if (tmp.getBpelEvent() instanceof ScopeActivityReady) {
				final ScopeActivityReady event = (ScopeActivityReady) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 76753611036745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(true);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}

			} else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}

	// Incoming Event Suppress_Fault
	public void Suppress_Fault(Suppress_Fault incEvent) {
		BlockingEvent tmp = getEvent(incEvent.getReplyToMsgID());

		if (tmp != null) {
			if (tmp.getBpelEvent() instanceof ActivityFaulted) {
				final ActivityFaulted event = (ActivityFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 343443322L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}

			}

			else if (tmp.getBpelEvent() instanceof ActivityJoinFailure) {
				final ActivityJoinFailure event = (ActivityJoinFailure) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 23232345L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof ScopeActivityFaulted) {
				final ScopeActivityFaulted event = (ScopeActivityFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 35435645745L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else if (tmp.getBpelEvent() instanceof EvaluatingTransitionConditionFaulted) {
				final EvaluatingTransitionConditionFaulted event = (EvaluatingTransitionConditionFaulted) tmp
						.getBpelEvent();

				JacobRunnable runnable = new JacobRunnable() {
					private static final long serialVersionUID = 323111211L;

					public void run() {
						LinkStatusChannel chan = importChannel(
								event.getLink_name(), LinkStatusChannel.class);
						chan.linkStatus(false);
					}

				};

				// @hahnml: Reference the runnable with the activity it belongs
				// to
				runnable.setOId(event.getOBaseId());

				QName name = instanceEventHandler.getQName(event
						.getProcess_ID());
				if (name != null) {
					addRunnable(event.getProcess_ID(), runnable);
					timerEvent(event.getProcess_ID(), name);
				}
			}

			else {
				System.out.println("");
				System.out
						.println("Incoming Message made no sense. Possibly deadlocked now.");
				System.out.println("");
				unblock(tmp.getBpelEvent());
			}
		} else {
			System.out.println("");
			System.out
					.println("No blocking Message found. Possibly deadlocked now.");
			System.out.println("");
		}
	}

	// incoming event Terminate_Activity
	public void Terminate_Activity(Terminate_Activity incEvent) {
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();
		Running_Activity tmp = null;

		synchronized (evtHandler.getRunningActivities()) {
			Iterator<Running_Activity> itr = evtHandler.getRunningActivities()
					.iterator();
			while (itr.hasNext()) {
				Running_Activity tmp_act = itr.next();
				if (tmp_act.getProcessID().equals(incEvent.getProcessID())
						&& tmp_act.getScopeID().equals(incEvent.getScopeID())
						&& tmp_act.getXPath().equals(incEvent.getAct_Xpath())) {
					tmp = tmp_act;
				}
			}
		}
		final Running_Activity tmp2 = tmp;
		if (tmp2 != null) {

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 779845642211L;

				public void run() {
					TerminationChannel termChan = importChannel(
							tmp2.getTermChannelName(), TerminationChannel.class);
					termChan.terminate();
				}

			};

			QName name = instanceEventHandler.getQName(incEvent.getProcessID());
			if (name != null) {
				addRunnable(incEvent.getProcessID(), runnable);
				timerEvent(incEvent.getProcessID(), name);
			}
		}
	}

	// incoming event Fault_To_Scope
	public void Fault_To_Scope(Fault_To_Scope incEvent) {
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;
		if (tmp2 != null) {

			Element fltMessage = null;

			if (incEvent.getFaultMsg() != null) {
				Document XMLDoc;
				try {
					XMLDoc = DocumentBuilderFactory
							.newInstance()
							.newDocumentBuilder()
							.parse(new InputSource(new StringReader(incEvent
									.getFaultMsg())));
				} catch (Exception e) {
					XMLDoc = null;
				}
				if (XMLDoc != null) {
					Node node = XMLDoc.getFirstChild();
					fltMessage = (Element) node;
				}
			}

			final FaultData fault = new FaultData(incEvent.getFaultName(),
					fltMessage, incEvent.getMessageType(),
					incEvent.getElementType(), tmp.getOscope());

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 7557555552211L;

				public void run() {
					ParentScopeChannel faultChannel = importChannel(
							tmp2.getFaultChannelName(),
							ParentScopeChannel.class);
					faultChannel.completed(fault,
							CompensationHandler.emptySet());
				}

			};

			QName name = instanceEventHandler.getQName(incEvent.getProcessID());
			if (name != null) {
				addRunnable(incEvent.getProcessID(), runnable);
				timerEvent(incEvent.getProcessID(), name);
			}

		}
	}

	// Incoming event Compensate_Scope
	public void Compensate_Scope(Compensate_Scope incEvent) {
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();
		Compensation_Handler tmp = null;

		synchronized (evtHandler.getCompensationHandlers()) {
			Iterator<Compensation_Handler> itr = evtHandler
					.getCompensationHandlers().iterator();
			while (itr.hasNext()) {
				Compensation_Handler tmp_comp = itr.next();
				if (tmp_comp.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_comp.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_comp;
				}
			}
		}

		final Compensation_Handler tmp2 = tmp;
		if (tmp2 != null) {

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 987999999911L;

				public void run() {
					CompensationChannel compChan = importChannel(
							tmp2.getCompChannelName(),
							CompensationChannel.class);
					SynchChannel ret = newChannel(SynchChannel.class);
					compChan.compensate(ret);
				}

			};

			QName name = instanceEventHandler.getQName(incEvent.getProcessID());
			if (name != null) {
				addRunnable(incEvent.getProcessID(), runnable);
				timerEvent(incEvent.getProcessID(), name);
			}
		}

	}

	// Incoming message Read_Variable
	public void Read_Variable(final Destination dest,
			final Read_Variable incEvent) {
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;

		BpelJacobRunnable runnable = new BpelJacobRunnable() {
			private static final long serialVersionUID = 7676776119911L;

			public void run() {
				Node value = null;
				Variable_Read message = new Variable_Read();

				if (tmp2 != null) {
					try {
						value = getBpelRuntimeContext().readVariable(
								incEvent.getScopeID(),
								incEvent.getVariableName(), true);
					} catch (FaultException e) {
						e.printStackTrace();
						value = null;
					}
				}

				String val;
				String val2 = null;
				if (value != null) {
					val = convertToString(value);
					if (val != null) {
						int index = val.indexOf(">");
						val2 = val.substring(index + 1);
					} else {
						val2 = value.getTextContent();
					}
				}

				comm.fillMessageBase(message, genCon.getTimestamp());
				message.setReplyToMsgID(incEvent.getMsgID());
				message.setValue(val2);
				comm.sendMessageToDestination(dest, message);

			}

		};

		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			addRunnable(incEvent.getProcessID(), runnable);
			timerEvent(incEvent.getProcessID(), name);
		}
	}

	// @author sonntamo
	// Incoming message Read_PartnerLink
	public void Read_PartnerLink(final Destination dest,
			final Read_PartnerLink incEvent) {
		ActivityEventHandler evtHandler = ActivityEventHandler.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;

		BpelJacobRunnable runnable = new BpelJacobRunnable() {
			private static final long serialVersionUID = 7676776119911L;

			public void run() {
				Node value = null;
				PartnerLink_Read message = new PartnerLink_Read();

				if (tmp2 != null) {
					try {
						OPartnerLink oPL = tmp2.getOscope().partnerLinks
								.get(incEvent.getPartnerLinkName());
						PartnerLinkInstance pli = tmp2.getScope()
								.get_scopeFrame().resolve(oPL);
						if (getBpelRuntimeContext()
								.isPartnerRoleEndpointInitialized(pli)) {
							value = getBpelRuntimeContext()
									.fetchPartnerRoleEndpointReferenceData(pli);
						}
					} catch (FaultException e) {
						e.printStackTrace();
						value = null;
					}
				}

				String val;
				String val2 = null;
				if (value != null) {
					val = convertToString(value);
					if (val != null) {
						int index = val.indexOf(">");
						val2 = val.substring(index + 1);
					} else {
						val2 = value.getTextContent();
					}
				}

				comm.fillMessageBase(message, genCon.getTimestamp());
				message.setReplyToMsgID(incEvent.getMsgID());
				message.setValue(val2);
				comm.sendMessageToDestination(dest, message);

			}

		};

		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			addRunnable(incEvent.getProcessID(), runnable);
			timerEvent(incEvent.getProcessID(), name);
		}
	}

	// Incoming message Write_Variable
	public void Write_Variable(final Write_Variable incEvent) {
		final ActivityEventHandler evtHandler = ActivityEventHandler
				.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;

		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		DebuggerSupport debug = null;

		if (name != null) {
			synchronized (block.getDebuggers()) {
				debug = block.getDebuggers().get(name);
			}
		}

		if (debug != null) {

			final BpelProcess bpelProcess = debug.getProcess();
			ProcessInstanceDAO instanceDAO = debug
					.getProcessInstanceDAO(incEvent.getProcessID());
			BpelDatabase db = debug.getProcessDatabase();

			if (instanceDAO != null
					&& instanceDAO.getState() == ProcessState.STATE_SUSPENDED) {

				// @hahnml: Surround the whole work with a database transaction
				try {
					db.exec(new BpelDatabase.Callable<Object>() {
						public Object run(BpelDAOConnection conn)
								throws Exception {

							ProcessInstanceDAO instance = conn
									.getInstance(incEvent.getProcessID());

							if (instance == null)
								throw new InstanceNotFoundException(""
										+ incEvent.getProcessID());

							// Handle variable changes during the process
							// instance is suspended
							BpelRuntimeContextImpl processInstance = new BpelRuntimeContextImpl(
									bpelProcess, instance, null, null);

							if (tmp2 != null) {
								try {
									Variable var = tmp2.getOscope()
											.getVisibleVariable(
													incEvent.getVariableName());
									ScopeFrame tmp_frame = tmp2.getScope()
											.get_scopeFrame()
											.find(var.declaringScope);
									VariableInstance variable = tmp_frame
											.resolve(var);

									String newVal = incEvent.getChanges();

									if (newVal.charAt(0) == '<') {
										Document XMLDoc = DocumentBuilderFactory
												.newInstance()
												.newDocumentBuilder()
												.parse(new InputSource(
														new StringReader(newVal)));
										Node val = XMLDoc.getFirstChild();
										Node newValue = processInstance
												.writeVariable(variable, val);
										evtHandler.Variable_Modification_Event(
												var.name, var.getXpath(), null,
												null, var.declaringScope
														.getXpath(), tmp_frame
														.getScopeInstanceId(),
												processInstance
														.getBpelProcess()
														.getPID(),
												processInstance.getPid(),
												newValue);

									}
								} catch (Exception e) {
									System.out.println("");
									System.out
											.println("Failed to write Variable.");
									System.out.println("");
								}
							}

							return null;
						}
					});

				} catch (InstanceNotFoundException infe) {
					throw infe;
				} catch (Exception ex) {
					System.out
							.println("Exception during valriable modification from outside: "
									+ ex.getMessage());
				}

			} else {
				// handle variable changes during the execution of the process
				// instance

				BpelJacobRunnable runnable = new BpelJacobRunnable() {
					private static final long serialVersionUID = 6576578658911L;

					public void run() {
						if (tmp2 != null) {
							try {
								Variable var = tmp2.getOscope()
										.getVisibleVariable(
												incEvent.getVariableName());
								ScopeFrame tmp_frame = tmp2.getScope()
										.get_scopeFrame()
										.find(var.declaringScope);
								VariableInstance variable = tmp_frame
										.resolve(var);

								String newVal = incEvent.getChanges();

								if (newVal.charAt(0) == '<') {
									Document XMLDoc = DocumentBuilderFactory
											.newInstance()
											.newDocumentBuilder()
											.parse(new InputSource(
													new StringReader(newVal)));
									Node val = XMLDoc.getFirstChild();
									Node newValue = getBpelRuntimeContext()
											.writeVariable(variable, val);
									evtHandler.Variable_Modification_Event(
											var.name, var.getXpath(), null,
											null,
											var.declaringScope.getXpath(),
											tmp_frame.getScopeInstanceId(),
											getBpelRuntimeContext()
													.getBpelProcess().getPID(),
											getBpelRuntimeContext().getPid(),
											newValue);

								}
							} catch (Exception e) {
								System.out.println("");
								System.out.println("Failed to write Variable.");
								System.out.println("");
							}
						}
					}

				};

				if (name != null) {
					addRunnable(incEvent.getProcessID(), runnable);
					timerEvent(incEvent.getProcessID(), name);
				}
			}
		}
	}

	// set a PartnerLink to a new Value
	public void Write_PartnerLink(final Write_PartnerLink incEvent) {
		final ActivityEventHandler evtHandler = ActivityEventHandler
				.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;

		BpelJacobRunnable runnable = new BpelJacobRunnable() {
			private static final long serialVersionUID = 6576578658911L;

			public void run() {
				if (tmp2 != null) {
					try {
						OPartnerLink plink = tmp2.getOscope()
								.getLocalPartnerLink(incEvent.getPlName());
						ScopeFrame tmp_frame = tmp2.getScope().get_scopeFrame()
								.find(plink.declaringScope);
						PartnerLinkInstance plval = tmp_frame.resolve(plink);

						String newEPR = incEvent.getNewEPR();

						Document XMLDoc = DocumentBuilderFactory
								.newInstance()
								.newDocumentBuilder()
								.parse(new InputSource(new StringReader(newEPR)));
						Node epr = XMLDoc.getFirstChild();

						String name = epr.getLocalName() == null ? epr
								.getNodeName() : epr.getLocalName();

						if (epr.getNodeType() == Node.TEXT_NODE
								|| (epr.getNodeType() == Node.ELEMENT_NODE
										&& name != null && !name
										.equals("service-ref"))) {
							Document doc = DOMUtils.newDocument();
							Element serviceRef = doc.createElementNS(
									Namespaces.WSBPEL2_0_FINAL_SERVREF,
									"service-ref");
							doc.appendChild(serviceRef);
							NodeList children = epr.getChildNodes();
							for (int m = 0; m < children.getLength(); m++) {
								Node child = children.item(m);
								serviceRef.appendChild(doc.importNode(child,
										true));
							}
							epr = serviceRef;
						}

						getBpelRuntimeContext().writeEndpointReference(plval,
								(Element) epr);

						evtHandler
								.PartnerLinkModification_Event(
										plval.partnerLink.getName(),
										plval.partnerLink.getXpath(), null,
										tmp2.getOscope().getXpath(), tmp_frame
												.getScopeInstanceId(),
										getBpelRuntimeContext()
												.getBpelProcess().getPID(),
										incEvent.getProcessID(), epr);

					} catch (Exception e) {
						System.out.println("");
						System.out.println("Failed to write PartnerLink.");
						System.out.println("");
					}
				}
			}

		};

		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			addRunnable(incEvent.getProcessID(), runnable);
			timerEvent(incEvent.getProcessID(), name);
		}

	}

	// set a CorrelationSet to a new value
	public void Write_CorrelationSet(final Write_CorrelationSet incEvent) {
		final ActivityEventHandler evtHandler = ActivityEventHandler
				.getInstance();
		Running_Scope tmp = null;

		synchronized (evtHandler.getRunningScopes()) {
			Iterator<Running_Scope> itr = evtHandler.getRunningScopes()
					.iterator();
			while (itr.hasNext()) {
				Running_Scope tmp_scope = itr.next();
				if (tmp_scope.getProcess_ID().equals(incEvent.getProcessID())
						&& tmp_scope.getScopeID().equals(incEvent.getScopeID())) {
					tmp = tmp_scope;
				}
			}
		}

		final Running_Scope tmp2 = tmp;

		BpelJacobRunnable runnable = new BpelJacobRunnable() {
			private static final long serialVersionUID = 6576578658911L;

			public void run() {
				if (tmp2 != null) {
					try {
						OScope.CorrelationSet cSet = tmp2.getOscope()
								.getCorrelationSet(incEvent.getCorrSetName());
						ScopeFrame tmp_frame = tmp2.getScope().get_scopeFrame()
								.find(cSet.declaringScope);
						CorrelationSetInstance CSET = tmp_frame.resolve(cSet);

						CorrelationKey ckeyVal = new CorrelationKey(
								CSET.declaration.name, incEvent.getNewValues());
						getBpelRuntimeContext().writeCorrelation(CSET, ckeyVal,
								true, null);

					} catch (Exception e) {
						System.out.println("");
						System.out.println("Failed to write CorrelationSet.");
						System.out.println("");
					}
				}
			}

		};

		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			addRunnable(incEvent.getProcessID(), runnable);
			timerEvent(incEvent.getProcessID(), name);
		}
	}

	// Incoming message RequestRegistrationInformation
	// @stmz: so we need to create a RegistrationInformationMessage in return
	public void RequestRegistrationInformation(Destination dest,
			RequestRegistrationInformation incEvent) {
		RegistrationInformationMessage message = new RegistrationInformationMessage();

		synchronized (deploymentEventHandler.getDepProc()) {
			Iterator<Deployed_Process> itr = deploymentEventHandler
					.getDepProc().iterator();
			while (itr.hasNext()) {
				Deployed_Process tmp = itr.next();
				QName qname = comm.cropQName(tmp.getProcessName());
				message.addDeployedProcess(qname, tmp.getBPELfile(),
						tmp.getWsdlFiles(), tmp.getVersion());
			}
		}

		synchronized (instanceEventHandler.getActProc()) {
			Iterator<Active_Process> itr = instanceEventHandler.getActProc()
					.iterator();
			while (itr.hasNext()) {
				Active_Process tmp = itr.next();
				QName qname = comm.cropQName(tmp.getName());
				message.addActiveProcess(qname, tmp.getID(), tmp.getVersion());
			}
		}

		comm.fillMessageBase(message, genCon.getTimestamp());
		message.setReplyToMsgID(incEvent.getMsgID());
		comm.sendMessageToDestination(dest, message);
	}

	// Incoming message RegisterRequestMessage
	// @stmz: we need to check, if some event types are already blocked by
	// another controller
	// and inform the controller, that the registration failed (and why it
	// failed) or succeeded
	public void RegisterRequestMessage(RegisterRequestMessage incEvent,
			Destination dest) {
		Boolean conflict = false;
		BlockingManager block = BlockingManager.getInstance();

		RegisterResponseMessage message = new RegisterResponseMessage();
		message.setReplyToMsgID(incEvent.getMsgID());

		synchronized (block.getProcessModelBlockingEvents()) {
			synchronized (block.getInstanceBlockingEvents()) {
				Requested_Blocking_Events incEventglobalEventBlockings = incEvent
						.getGlobalEventBlockings();
				List messageglobalConflicts = message.getGlobalConflicts();
				Blocking_Events blockglobalBlockingEvents = block
						.getGlobalBlockingEvents();

				// global to global
				if (blockglobalBlockingEvents.Activity_Ready == true
						&& incEventglobalEventBlockings.Activity_Ready == true) {
					conflict = true;
					messageglobalConflicts.add("Activity_Ready");
				}
				if (blockglobalBlockingEvents.Activity_Executed == true
						&& incEventglobalEventBlockings.Activity_Executed == true) {
					conflict = true;
					messageglobalConflicts.add("Activity_Executed");
				}
				if (blockglobalBlockingEvents.Activity_Faulted == true
						&& incEventglobalEventBlockings.Activity_Faulted == true) {
					conflict = true;
					messageglobalConflicts.add("Activity_Faulted");
				}
				if (blockglobalBlockingEvents.Evaluating_TransitionCondition_Faulted == true
						&& incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true) {
					conflict = true;
					messageglobalConflicts
							.add("Evaluating_TransitionCondition_Faulted");
				}
				/*
				 * if (blockglobalBlockingEvents.FaultHandling_NoHandler == true
				 * && incEventglobalEventBlockings.FaultHandling_NoHandler ==
				 * true) { conflict = true;
				 * messageglobalConflicts.add("FaultHandling_NoHandler"); }
				 */
				if (blockglobalBlockingEvents.Scope_Compensating == true
						&& incEventglobalEventBlockings.Scope_Compensating == true) {
					conflict = true;
					messageglobalConflicts.add("Scope_Compensating");
				}
				if (blockglobalBlockingEvents.Scope_Handling_Termination == true
						&& incEventglobalEventBlockings.Scope_Handling_Termination == true) {
					conflict = true;
					messageglobalConflicts.add("Scope_Handling_Termination");
				}
				if (blockglobalBlockingEvents.Scope_Complete_With_Fault == true
						&& incEventglobalEventBlockings.Scope_Complete_With_Fault == true) {
					conflict = true;
					messageglobalConflicts.add("Scope_Complete_With_Fault");
				}
				if (blockglobalBlockingEvents.Scope_Handling_Fault == true
						&& incEventglobalEventBlockings.Scope_Handling_Fault == true) {
					conflict = true;
					messageglobalConflicts.add("Scope_Handling_Fault");
				}
				if (blockglobalBlockingEvents.Loop_Condition_False == true
						&& incEventglobalEventBlockings.Loop_Condition_False == true) {
					conflict = true;
					messageglobalConflicts.add("Loop_Condition_False");
				}
				if (blockglobalBlockingEvents.Loop_Condition_True == true
						&& incEventglobalEventBlockings.Loop_Condition_True == true) {
					conflict = true;
					messageglobalConflicts.add("Loop_Condition_True");
				}
				if (blockglobalBlockingEvents.Loop_Iteration_Complete == true
						&& incEventglobalEventBlockings.Loop_Iteration_Complete == true) {
					conflict = true;
					messageglobalConflicts.add("Loop_Iteration_Complete");
				}
				if (blockglobalBlockingEvents.Link_Evaluated == true
						&& incEventglobalEventBlockings.Link_Evaluated == true) {
					conflict = true;
					messageglobalConflicts.add("Link_Evaluated");
				}

				// global to processModel
				Iterator<BlockingEventsProcessModel> itr = block
						.getProcessModelBlockingEvents().iterator();
				while (itr.hasNext()) {
					BlockingEventsProcessModel tmp = itr.next();

					if (incEventglobalEventBlockings.Activity_Ready == true
							&& tmp.getBlockEvents().Activity_Ready == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Ready");
					}
					if (incEventglobalEventBlockings.Activity_Executed == true
							&& tmp.getBlockEvents().Activity_Executed == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Executed");
					}
					if (incEventglobalEventBlockings.Activity_Faulted == true
							&& tmp.getBlockEvents().Activity_Faulted == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Faulted");
					}
					if (incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true
							&& tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted == true) {
						conflict = true;
						messageglobalConflicts
								.add("Evaluating_TransitionCondition_Faulted");
					}
					/*
					 * if (incEventglobalEventBlockings.FaultHandling_NoHandler
					 * == true && tmp.getBlockEvents().FaultHandling_NoHandler
					 * == true) { conflict = true;
					 * messageglobalConflicts.add("FaultHandling_NoHandler"); }
					 */
					if (incEventglobalEventBlockings.Scope_Compensating == true
							&& tmp.getBlockEvents().Scope_Compensating == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Compensating");
					}
					if (incEventglobalEventBlockings.Scope_Handling_Termination == true
							&& tmp.getBlockEvents().Scope_Handling_Termination == true) {
						conflict = true;
						messageglobalConflicts
								.add("Scope_Handling_Termination");
					}
					if (incEventglobalEventBlockings.Scope_Complete_With_Fault == true
							&& tmp.getBlockEvents().Scope_Complete_With_Fault == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Complete_With_Fault");
					}
					if (incEventglobalEventBlockings.Scope_Handling_Fault == true
							&& tmp.getBlockEvents().Scope_Handling_Fault == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Handling_Fault");
					}
					if (incEventglobalEventBlockings.Loop_Condition_False == true
							&& tmp.getBlockEvents().Loop_Condition_False == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Condition_False");
					}
					if (incEventglobalEventBlockings.Loop_Condition_True == true
							&& tmp.getBlockEvents().Loop_Condition_True == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Condition_True");
					}
					if (incEventglobalEventBlockings.Loop_Iteration_Complete == true
							&& tmp.getBlockEvents().Loop_Iteration_Complete == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Iteration_Complete");
					}
					if (incEventglobalEventBlockings.Link_Evaluated == true
							&& tmp.getBlockEvents().Link_Evaluated == true) {
						conflict = true;
						messageglobalConflicts.add("Link_Evaluated");
					}

				}

				// global to instance
				Iterator<BlockingEventsInstance> itr2 = block
						.getInstanceBlockingEvents().iterator();
				while (itr2.hasNext()) {

					BlockingEventsInstance tmp2 = itr2.next();

					if (incEventglobalEventBlockings.Activity_Ready == true
							&& tmp2.getBlockEvents().Activity_Ready == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Ready");
					}
					if (incEventglobalEventBlockings.Activity_Executed == true
							&& tmp2.getBlockEvents().Activity_Executed == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Executed");
					}
					if (incEventglobalEventBlockings.Activity_Faulted == true
							&& tmp2.getBlockEvents().Activity_Faulted == true) {
						conflict = true;
						messageglobalConflicts.add("Activity_Faulted");
					}
					if (incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true
							&& tmp2.getBlockEvents().Evaluating_TransitionCondition_Faulted == true) {
						conflict = true;
						messageglobalConflicts
								.add("Evaluating_TransitionCondition_Faulted");
					}
					/*
					 * if (incEventglobalEventBlockings.FaultHandling_NoHandler
					 * == true && tmp2.getBlockEvents().FaultHandling_NoHandler
					 * == true) { conflict = true;
					 * messageglobalConflicts.add("FaultHandling_NoHandler"); }
					 */
					if (incEventglobalEventBlockings.Scope_Compensating == true
							&& tmp2.getBlockEvents().Scope_Compensating == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Compensating");
					}
					if (incEventglobalEventBlockings.Scope_Handling_Termination == true
							&& tmp2.getBlockEvents().Scope_Handling_Termination == true) {
						conflict = true;
						messageglobalConflicts
								.add("Scope_Handling_Termination");
					}
					if (incEventglobalEventBlockings.Scope_Complete_With_Fault == true
							&& tmp2.getBlockEvents().Scope_Complete_With_Fault == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Complete_With_Fault");
					}
					if (incEventglobalEventBlockings.Scope_Handling_Fault == true
							&& tmp2.getBlockEvents().Scope_Handling_Fault == true) {
						conflict = true;
						messageglobalConflicts.add("Scope_Handling_Fault");
					}
					if (incEventglobalEventBlockings.Loop_Condition_False == true
							&& tmp2.getBlockEvents().Loop_Condition_False == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Condition_False");
					}
					if (incEventglobalEventBlockings.Loop_Condition_True == true
							&& tmp2.getBlockEvents().Loop_Condition_True == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Condition_True");
					}
					if (incEventglobalEventBlockings.Loop_Iteration_Complete == true
							&& tmp2.getBlockEvents().Loop_Iteration_Complete == true) {
						conflict = true;
						messageglobalConflicts.add("Loop_Iteration_Complete");
					}
					if (incEventglobalEventBlockings.Link_Evaluated == true
							&& tmp2.getBlockEvents().Link_Evaluated == true) {
						conflict = true;
						messageglobalConflicts.add("Link_Evaluated");
					}

				}

				Iterator<ModelEventBlocking> iter = incEvent
						.getProcessModelEventBlockings().iterator();
				while (iter.hasNext()) {
					ModelEventBlocking tmp = iter.next();

					// processModel to procesModel
					Iterator<BlockingEventsProcessModel> iter2 = block
							.getProcessModelBlockingEvents().iterator();
					while (iter2.hasNext()) {
						BlockingEventsProcessModel tmp2 = iter2.next();

						// if they are from the same process
						if (tmp.getProcessName().equals(tmp2.getProcessName())
								&& tmp.getVersion().equals(tmp2.getVersion())) {

							if (tmp.getEvents().Activity_Ready == true
									&& tmp2.getBlockEvents().Activity_Ready == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Ready", tmp.getProcessName(),
										tmp.getVersion());
							}
							if (tmp.getEvents().Activity_Executed == true
									&& tmp2.getBlockEvents().Activity_Executed == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Executed",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Activity_Faulted == true
									&& tmp2.getBlockEvents().Activity_Faulted == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Faulted",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Evaluating_TransitionCondition_Faulted == true
									&& tmp2.getBlockEvents().Evaluating_TransitionCondition_Faulted == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Evaluating_TransitionCondition_Faulted",
										tmp.getProcessName(), tmp.getVersion());
							}
							/*
							 * if (tmp.getEvents().FaultHandling_NoHandler ==
							 * true &&
							 * tmp2.getBlockEvents().FaultHandling_NoHandler ==
							 * true) { conflict = true;
							 * message.addProcessModelConflict
							 * ("FaultHandling_NoHandler", tmp.getProcessName(),
							 * tmp.getVersion()); }
							 */
							if (tmp.getEvents().Scope_Compensating == true
									&& tmp2.getBlockEvents().Scope_Compensating == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Compensating",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Handling_Termination == true
									&& tmp2.getBlockEvents().Scope_Handling_Termination == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Handling_Termination",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Complete_With_Fault == true
									&& tmp2.getBlockEvents().Scope_Complete_With_Fault == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Complete_With_Fault",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Handling_Fault == true
									&& tmp2.getBlockEvents().Scope_Handling_Fault == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Handling_Fault",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Condition_False == true
									&& tmp2.getBlockEvents().Loop_Condition_False == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Condition_False",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Condition_True == true
									&& tmp2.getBlockEvents().Loop_Condition_True == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Condition_True",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Iteration_Complete == true
									&& tmp2.getBlockEvents().Loop_Iteration_Complete == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Iteration_Complete",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Link_Evaluated == true
									&& tmp2.getBlockEvents().Link_Evaluated == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Link_Evaluated", tmp.getProcessName(),
										tmp.getVersion());
							}

						}

					}

					// processModel to instance
					Iterator<BlockingEventsInstance> iter3 = block
							.getInstanceBlockingEvents().iterator();

					while (iter3.hasNext()) {
						BlockingEventsInstance tmp2 = iter3.next();

						if (tmp.getProcessName().equals(tmp2.getProcessName())
								&& tmp.getVersion().equals(tmp2.getVersion())) {

							if (tmp.getEvents().Activity_Ready == true
									&& tmp2.getBlockEvents().Activity_Ready == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Ready", tmp.getProcessName(),
										tmp.getVersion());
							}
							if (tmp.getEvents().Activity_Executed == true
									&& tmp2.getBlockEvents().Activity_Executed == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Executed",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Activity_Faulted == true
									&& tmp2.getBlockEvents().Activity_Faulted == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Activity_Faulted",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Evaluating_TransitionCondition_Faulted == true
									&& tmp2.getBlockEvents().Evaluating_TransitionCondition_Faulted == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Evaluating_TransitionCondition_Faulted",
										tmp.getProcessName(), tmp.getVersion());
							}
							/*
							 * if (tmp.getEvents().FaultHandling_NoHandler ==
							 * true &&
							 * tmp2.getBlockEvents().FaultHandling_NoHandler ==
							 * true) { conflict = true;
							 * message.addProcessModelConflict
							 * ("FaultHandling_NoHandler", tmp.getProcessName(),
							 * tmp.getVersion()); }
							 */
							if (tmp.getEvents().Scope_Compensating == true
									&& tmp2.getBlockEvents().Scope_Compensating == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Compensating",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Handling_Termination == true
									&& tmp2.getBlockEvents().Scope_Handling_Termination == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Handling_Termination",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Complete_With_Fault == true
									&& tmp2.getBlockEvents().Scope_Complete_With_Fault == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Complete_With_Fault",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Scope_Handling_Fault == true
									&& tmp2.getBlockEvents().Scope_Handling_Fault == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Scope_Handling_Fault",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Condition_False == true
									&& tmp2.getBlockEvents().Loop_Condition_False == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Condition_False",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Condition_True == true
									&& tmp2.getBlockEvents().Loop_Condition_True == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Condition_True",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Loop_Iteration_Complete == true
									&& tmp2.getBlockEvents().Loop_Iteration_Complete == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Loop_Iteration_Complete",
										tmp.getProcessName(), tmp.getVersion());
							}
							if (tmp.getEvents().Link_Evaluated == true
									&& tmp2.getBlockEvents().Link_Evaluated == true) {
								conflict = true;
								message.addProcessModelConflict(
										"Link_Evaluated", tmp.getProcessName(),
										tmp.getVersion());
							}

						}

					}
				}

				// instance to instance
				Iterator<InstanceEventBlocking> itera = incEvent
						.getProcessInstanceEventBlockings().iterator();
				while (itera.hasNext()) {
					InstanceEventBlocking tmp = itera.next();

					Iterator<BlockingEventsInstance> iterb = block
							.getInstanceBlockingEvents().iterator();

					while (iterb.hasNext()) {
						BlockingEventsInstance tmp2 = iterb.next();

						if (tmp.getProcessID().equals(tmp2.getID())) {
							if (tmp.getEvents().Activity_Ready == true
									&& tmp2.getBlockEvents().Activity_Ready == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Activity_Ready",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Activity_Executed == true
									&& tmp2.getBlockEvents().Activity_Executed == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Activity_Executed",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Activity_Faulted == true
									&& tmp2.getBlockEvents().Activity_Faulted == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Activity_Faulted",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Evaluating_TransitionCondition_Faulted == true
									&& tmp2.getBlockEvents().Evaluating_TransitionCondition_Faulted == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Evaluating_TransitionCondition_Faulted",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							/*
							 * if (tmp.getEvents().FaultHandling_NoHandler ==
							 * true &&
							 * tmp2.getBlockEvents().FaultHandling_NoHandler ==
							 * true) { conflict = true;
							 * message.addProcessInstanceConflict
							 * ("FaultHandling_NoHandler",
							 * tmp2.getProcessName(), tmp2.getVersion(),
							 * tmp2.getID()); }
							 */
							if (tmp.getEvents().Scope_Compensating == true
									&& tmp2.getBlockEvents().Scope_Compensating == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Scope_Compensating",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Scope_Handling_Termination == true
									&& tmp2.getBlockEvents().Scope_Handling_Termination == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Scope_Handling_Termination",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Scope_Complete_With_Fault == true
									&& tmp2.getBlockEvents().Scope_Complete_With_Fault == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Scope_Complete_With_Fault",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Scope_Handling_Fault == true
									&& tmp2.getBlockEvents().Scope_Handling_Fault == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Scope_Handling_Fault",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Loop_Condition_False == true
									&& tmp2.getBlockEvents().Loop_Condition_False == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Loop_Condition_False",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Loop_Condition_True == true
									&& tmp2.getBlockEvents().Loop_Condition_True == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Loop_Condition_True",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Loop_Iteration_Complete == true
									&& tmp2.getBlockEvents().Loop_Iteration_Complete == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Loop_Iteration_Complete",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
							if (tmp.getEvents().Link_Evaluated == true
									&& tmp2.getBlockEvents().Link_Evaluated == true) {
								conflict = true;
								message.addProcessInstanceConflict(
										"Link_Evaluated",
										tmp2.getProcessName(),
										tmp2.getVersion(), tmp2.getID());
							}
						}

					}

				}

				if (conflict) {
					message.setRegistered(false);
				} else {
					message.setRegistered(true);

					// add Controller to List of registered Controllers
					block.addNewRegController(dest, incEvent);

					// global to global
					if (incEventglobalEventBlockings.Activity_Ready == true) {
						blockglobalBlockingEvents.Activity_Ready = true;
					}
					if (incEventglobalEventBlockings.Activity_Executed == true) {
						blockglobalBlockingEvents.Activity_Executed = true;
					}
					if (incEventglobalEventBlockings.Activity_Faulted == true) {
						blockglobalBlockingEvents.Activity_Faulted = true;
					}
					if (incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true) {
						blockglobalBlockingEvents.Evaluating_TransitionCondition_Faulted = true;
					}
					/*
					 * if (incEventglobalEventBlockings.FaultHandling_NoHandler
					 * == true) {
					 * blockglobalBlockingEvents.FaultHandling_NoHandler = true;
					 * }
					 */
					if (incEventglobalEventBlockings.Scope_Compensating == true) {
						blockglobalBlockingEvents.Scope_Compensating = true;
					}
					if (incEventglobalEventBlockings.Scope_Handling_Termination == true) {
						blockglobalBlockingEvents.Scope_Handling_Termination = true;
					}
					if (incEventglobalEventBlockings.Scope_Complete_With_Fault == true) {
						blockglobalBlockingEvents.Scope_Complete_With_Fault = true;
					}
					if (incEventglobalEventBlockings.Scope_Handling_Fault == true) {
						blockglobalBlockingEvents.Scope_Handling_Fault = true;
					}
					if (incEventglobalEventBlockings.Loop_Condition_False == true) {
						blockglobalBlockingEvents.Loop_Condition_False = true;
					}
					if (incEventglobalEventBlockings.Loop_Condition_True == true) {
						blockglobalBlockingEvents.Loop_Condition_True = true;
					}
					if (incEventglobalEventBlockings.Loop_Iteration_Complete == true) {
						blockglobalBlockingEvents.Loop_Iteration_Complete = true;
					}
					if (incEventglobalEventBlockings.Link_Evaluated == true) {
						blockglobalBlockingEvents.Link_Evaluated = true;
					}

					// global to processModel

					Iterator<BlockingEventsProcessModel> iterat = block
							.getProcessModelBlockingEvents().iterator();
					while (iterat.hasNext()) {
						BlockingEventsProcessModel tmp = iterat.next();

						if (incEventglobalEventBlockings.Activity_Ready == true) {
							tmp.getBlockEvents().Activity_Ready = true;
						}
						if (incEventglobalEventBlockings.Activity_Executed == true) {
							tmp.getBlockEvents().Activity_Executed = true;
						}
						if (incEventglobalEventBlockings.Activity_Faulted == true) {
							tmp.getBlockEvents().Activity_Faulted = true;
						}
						if (incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true) {
							tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted = true;
						}
						/*
						 * if
						 * (incEventglobalEventBlockings.FaultHandling_NoHandler
						 * == true) {
						 * tmp.getBlockEvents().FaultHandling_NoHandler = true;
						 * }
						 */
						if (incEventglobalEventBlockings.Scope_Compensating == true) {
							tmp.getBlockEvents().Scope_Compensating = true;
						}
						if (incEventglobalEventBlockings.Scope_Handling_Termination == true) {
							tmp.getBlockEvents().Scope_Handling_Termination = true;
						}
						if (incEventglobalEventBlockings.Scope_Complete_With_Fault == true) {
							tmp.getBlockEvents().Scope_Complete_With_Fault = true;
						}
						if (incEventglobalEventBlockings.Scope_Handling_Fault == true) {
							tmp.getBlockEvents().Scope_Handling_Fault = true;
						}
						if (incEventglobalEventBlockings.Loop_Condition_False == true) {
							tmp.getBlockEvents().Loop_Condition_False = true;
						}
						if (incEventglobalEventBlockings.Loop_Condition_True == true) {
							tmp.getBlockEvents().Loop_Condition_True = true;
						}
						if (incEventglobalEventBlockings.Loop_Iteration_Complete == true) {
							tmp.getBlockEvents().Loop_Iteration_Complete = true;
						}
						if (incEventglobalEventBlockings.Link_Evaluated == true) {
							tmp.getBlockEvents().Link_Evaluated = true;
						}
					}

					// global to instance
					Iterator<BlockingEventsInstance> iteratb = block
							.getInstanceBlockingEvents().iterator();
					while (iteratb.hasNext()) {
						BlockingEventsInstance tmp = iteratb.next();

						if (incEventglobalEventBlockings.Activity_Ready == true) {
							tmp.getBlockEvents().Activity_Ready = true;
						}
						if (incEventglobalEventBlockings.Activity_Executed == true) {
							tmp.getBlockEvents().Activity_Executed = true;
						}
						if (incEventglobalEventBlockings.Activity_Faulted == true) {
							tmp.getBlockEvents().Activity_Faulted = true;
						}
						if (incEventglobalEventBlockings.Evaluating_TransitionCondition_Faulted == true) {
							tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted = true;
						}
						/*
						 * if
						 * (incEventglobalEventBlockings.FaultHandling_NoHandler
						 * == true) {
						 * tmp.getBlockEvents().FaultHandling_NoHandler = true;
						 * }
						 */
						if (incEventglobalEventBlockings.Scope_Compensating == true) {
							tmp.getBlockEvents().Scope_Compensating = true;
						}
						if (incEventglobalEventBlockings.Scope_Handling_Termination == true) {
							tmp.getBlockEvents().Scope_Handling_Termination = true;
						}
						if (incEventglobalEventBlockings.Scope_Complete_With_Fault == true) {
							tmp.getBlockEvents().Scope_Complete_With_Fault = true;
						}
						if (incEventglobalEventBlockings.Scope_Handling_Fault == true) {
							tmp.getBlockEvents().Scope_Handling_Fault = true;
						}
						if (incEventglobalEventBlockings.Loop_Condition_False == true) {
							tmp.getBlockEvents().Loop_Condition_False = true;
						}
						if (incEventglobalEventBlockings.Loop_Condition_True == true) {
							tmp.getBlockEvents().Loop_Condition_True = true;
						}
						if (incEventglobalEventBlockings.Loop_Iteration_Complete == true) {
							tmp.getBlockEvents().Loop_Iteration_Complete = true;
						}
						if (incEventglobalEventBlockings.Link_Evaluated == true) {
							tmp.getBlockEvents().Link_Evaluated = true;
						}
					}

					// processModel to processModel
					Iterator<ModelEventBlocking> iterato1 = incEvent
							.getProcessModelEventBlockings().iterator();

					while (iterato1.hasNext()) {
						ModelEventBlocking temp = iterato1.next();

						Iterator<BlockingEventsProcessModel> iterato2 = block
								.getProcessModelBlockingEvents().iterator();
						while (iterato2.hasNext()) {
							BlockingEventsProcessModel tmp = iterato2.next();

							if (temp.getProcessName().equals(
									tmp.getProcessName())
									&& tmp.getVersion().equals(
											temp.getVersion())) {
								if (temp.getEvents().Activity_Ready == true) {
									tmp.getBlockEvents().Activity_Ready = true;
								}
								if (temp.getEvents().Activity_Executed == true) {
									tmp.getBlockEvents().Activity_Executed = true;
								}
								if (temp.getEvents().Activity_Faulted == true) {
									tmp.getBlockEvents().Activity_Faulted = true;
								}
								if (temp.getEvents().Evaluating_TransitionCondition_Faulted == true) {
									tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted = true;
								}
								/*
								 * if (temp.getEvents().FaultHandling_NoHandler
								 * == true) {
								 * tmp.getBlockEvents().FaultHandling_NoHandler
								 * = true; }
								 */
								if (temp.getEvents().Scope_Compensating == true) {
									tmp.getBlockEvents().Scope_Compensating = true;
								}
								if (temp.getEvents().Scope_Handling_Termination == true) {
									tmp.getBlockEvents().Scope_Handling_Termination = true;
								}
								if (temp.getEvents().Scope_Complete_With_Fault == true) {
									tmp.getBlockEvents().Scope_Complete_With_Fault = true;
								}
								if (temp.getEvents().Scope_Handling_Fault == true) {
									tmp.getBlockEvents().Scope_Handling_Fault = true;
								}
								if (temp.getEvents().Loop_Condition_False == true) {
									tmp.getBlockEvents().Loop_Condition_False = true;
								}
								if (temp.getEvents().Loop_Condition_True == true) {
									tmp.getBlockEvents().Loop_Condition_True = true;
								}
								if (temp.getEvents().Loop_Iteration_Complete == true) {
									tmp.getBlockEvents().Loop_Iteration_Complete = true;
								}
								if (temp.getEvents().Link_Evaluated == true) {
									tmp.getBlockEvents().Link_Evaluated = true;
								}
							}

						}

						// processModel to instance
						Iterator<BlockingEventsInstance> iterato3 = block
								.getInstanceBlockingEvents().iterator();

						while (iterato3.hasNext()) {
							BlockingEventsInstance tmp = iterato3.next();

							if (temp.getProcessName().equals(
									tmp.getProcessName())
									&& temp.getVersion().equals(
											tmp.getVersion())) {
								if (temp.getEvents().Activity_Ready == true) {
									tmp.getBlockEvents().Activity_Ready = true;
								}
								if (temp.getEvents().Activity_Executed == true) {
									tmp.getBlockEvents().Activity_Executed = true;
								}
								if (temp.getEvents().Activity_Faulted == true) {
									tmp.getBlockEvents().Activity_Faulted = true;
								}
								if (temp.getEvents().Evaluating_TransitionCondition_Faulted == true) {
									tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted = true;
								}
								/*
								 * if (temp.getEvents().FaultHandling_NoHandler
								 * == true) {
								 * tmp.getBlockEvents().FaultHandling_NoHandler
								 * = true; }
								 */
								if (temp.getEvents().Scope_Compensating == true) {
									tmp.getBlockEvents().Scope_Compensating = true;
								}
								if (temp.getEvents().Scope_Handling_Termination == true) {
									tmp.getBlockEvents().Scope_Handling_Termination = true;
								}
								if (temp.getEvents().Scope_Complete_With_Fault == true) {
									tmp.getBlockEvents().Scope_Complete_With_Fault = true;
								}
								if (temp.getEvents().Scope_Handling_Fault == true) {
									tmp.getBlockEvents().Scope_Handling_Fault = true;
								}
								if (temp.getEvents().Loop_Condition_False == true) {
									tmp.getBlockEvents().Loop_Condition_False = true;
								}
								if (temp.getEvents().Loop_Condition_True == true) {
									tmp.getBlockEvents().Loop_Condition_True = true;
								}
								if (temp.getEvents().Loop_Iteration_Complete == true) {
									tmp.getBlockEvents().Loop_Iteration_Complete = true;
								}
								if (temp.getEvents().Link_Evaluated == true) {
									tmp.getBlockEvents().Link_Evaluated = true;
								}
							}

						}

					}

					// instance to instance

					Iterator<InstanceEventBlocking> iterator1 = incEvent
							.getProcessInstanceEventBlockings().iterator();

					while (iterator1.hasNext()) {
						InstanceEventBlocking temp = iterator1.next();

						Iterator<BlockingEventsInstance> iterator2 = block
								.getInstanceBlockingEvents().iterator();

						while (iterator2.hasNext()) {
							BlockingEventsInstance tmp = iterator2.next();

							if (temp.getProcessID().equals(tmp.getID())) {
								if (temp.getEvents().Activity_Ready == true) {
									tmp.getBlockEvents().Activity_Ready = true;
								}
								if (temp.getEvents().Activity_Executed == true) {
									tmp.getBlockEvents().Activity_Executed = true;
								}
								if (temp.getEvents().Activity_Faulted == true) {
									tmp.getBlockEvents().Activity_Faulted = true;
								}
								if (temp.getEvents().Evaluating_TransitionCondition_Faulted == true) {
									tmp.getBlockEvents().Evaluating_TransitionCondition_Faulted = true;
								}
								/*
								 * if (temp.getEvents().FaultHandling_NoHandler
								 * == true) {
								 * tmp.getBlockEvents().FaultHandling_NoHandler
								 * = true; }
								 */
								if (temp.getEvents().Scope_Compensating == true) {
									tmp.getBlockEvents().Scope_Compensating = true;
								}
								if (temp.getEvents().Scope_Handling_Termination == true) {
									tmp.getBlockEvents().Scope_Handling_Termination = true;
								}
								if (temp.getEvents().Scope_Complete_With_Fault == true) {
									tmp.getBlockEvents().Scope_Complete_With_Fault = true;
								}
								if (temp.getEvents().Scope_Handling_Fault == true) {
									tmp.getBlockEvents().Scope_Handling_Fault = true;
								}
								if (temp.getEvents().Loop_Condition_False == true) {
									tmp.getBlockEvents().Loop_Condition_False = true;
								}
								if (temp.getEvents().Loop_Condition_True == true) {
									tmp.getBlockEvents().Loop_Condition_True = true;
								}
								if (temp.getEvents().Loop_Iteration_Complete == true) {
									tmp.getBlockEvents().Loop_Iteration_Complete = true;
								}
								if (temp.getEvents().Link_Evaluated == true) {
									tmp.getBlockEvents().Link_Evaluated = true;
								}
								// @hahnml: Copy the activity blocking events
								// and conditions
								// from the message to the blocking manager
								if (!temp.getActivityEvents().isEmpty()) {
									for (String act_name : temp
											.getActivityEvents().keySet()) {
										Blocking_Events blockEvts = convertBlockingEvents(temp
												.getActivityEvents().get(
														act_name));
										tmp.getActivityBlockEvents().put(
												act_name, blockEvts);

										// Compile the expression element
										if (temp.getActivityEventConditions() != null && !temp.getActivityEventConditions().isEmpty() && temp.getActivityEventConditions()
												.containsKey(act_name)) {
										OExpression expr = compileExpr(temp.getProcessName(),
												act_name,
												temp.getActivityEventConditions()
														.get(act_name));
										if (expr != null) {
											tmp.getActivityEventConditions()
													.put(act_name, expr);
										}
										}
									}
								}
							}

						}

					}

				}

			}
		}

		comm.fillMessageBase(message, genCon.getTimestamp());
		comm.sendMessageToDestination(dest, message);
	}

	// @hahnml: Compile the activity event conditions to OExpression objects
	private OExpression compileExpr(QName processName, String xPath, Element element) {
		try {
			String uriString = xPath.replaceFirst("/", "");
			uriString = uriString.replaceAll("\\[", "");
			uriString = uriString.replaceAll("\\]", "");
			URI uri = new URI(uriString);
			Expression expression = (Expression) BpelObjectFactory
					.getInstance().createBpelObject(element, uri);

			BpelCompiler compiler = CompilerRegistry.getInstance().getCompiler(processName);

			OExpression expr = null;
			if (compiler != null) {
				expr = compiler.compileExpr(expression);
			} else {
				compiler = new BpelCompiler20();
				expr = compiler.compileExpr(expression);
			}
			return expr;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	// @hahnml: Converts the incoming Requested_Blocking_Events into a
	// Blocking_Events object.
	private Blocking_Events convertBlockingEvents(
			Requested_Blocking_Events requestedBlockingEvents) {
		Blocking_Events result = BlockingManager.getInstance()
				.createBlockingEvent();
		if (requestedBlockingEvents.Activity_Ready == true) {
			result.Activity_Ready = true;
		}
		if (requestedBlockingEvents.Activity_Executed == true) {
			result.Activity_Executed = true;
		}
		if (requestedBlockingEvents.Activity_Faulted == true) {
			result.Activity_Faulted = true;
		}
		if (requestedBlockingEvents.Evaluating_TransitionCondition_Faulted == true) {
			result.Evaluating_TransitionCondition_Faulted = true;
		}
		if (requestedBlockingEvents.Scope_Compensating == true) {
			result.Scope_Compensating = true;
		}
		if (requestedBlockingEvents.Scope_Handling_Termination == true) {
			result.Scope_Handling_Termination = true;
		}
		if (requestedBlockingEvents.Scope_Complete_With_Fault == true) {
			result.Scope_Complete_With_Fault = true;
		}
		if (requestedBlockingEvents.Scope_Handling_Fault == true) {
			result.Scope_Handling_Fault = true;
		}
		if (requestedBlockingEvents.Loop_Condition_False == true) {
			result.Loop_Condition_False = true;
		}
		if (requestedBlockingEvents.Loop_Condition_True == true) {
			result.Loop_Condition_True = true;
		}
		if (requestedBlockingEvents.Loop_Iteration_Complete == true) {
			result.Loop_Iteration_Complete = true;
		}
		if (requestedBlockingEvents.Link_Evaluated == true) {
			result.Link_Evaluated = true;
		}

		return result;
	}

	// @stmz: a controller wants to unregister
	public void unregister(Destination dest) {
		regController controller = block.getRegController(dest);
		if (controller != null) {
			controller.timer.cancel();
			removeAllBlockings(controller);
			cleanUpBlockingEvents(controller.message);
		}
	}

	// @stmz: the controller getting unregistered doesn't block anything from
	// now on
	public void removeAllBlockings(regController controller) {
		synchronized (block.getProcessModelBlockingEvents()) {
			synchronized (block.getInstanceBlockingEvents()) {
				// global to global
				removeBlockings(controller.message.getGlobalEventBlockings(),
						block.getGlobalBlockingEvents());

				// global to processModel
				Iterator<BlockingEventsProcessModel> itr1 = block
						.getProcessModelBlockingEvents().iterator();
				while (itr1.hasNext()) {
					Blocking_Events tmp = itr1.next().getBlockEvents();
					removeBlockings(
							controller.message.getGlobalEventBlockings(), tmp);
				}

				// global to instance
				Iterator<BlockingEventsInstance> itr2 = block
						.getInstanceBlockingEvents().iterator();
				while (itr2.hasNext()) {
					Blocking_Events tmp = itr2.next().getBlockEvents();
					removeBlockings(
							controller.message.getGlobalEventBlockings(), tmp);
				}

				// processModel to processModel and to instance
				Iterator<ModelEventBlocking> itrX = controller.message
						.getProcessModelEventBlockings().iterator();
				while (itrX.hasNext()) {
					ModelEventBlocking model = itrX.next();

					// to processModel
					Iterator<BlockingEventsProcessModel> itrY = block
							.getProcessModelBlockingEvents().iterator();
					while (itrY.hasNext()) {
						BlockingEventsProcessModel model2 = itrY.next();
						if (model.getProcessName().equals(
								model2.getProcessName())
								&& model.getVersion().equals(
										model2.getVersion())) {
							removeBlockings(model.getEvents(),
									model2.getBlockEvents());
						}
					}

					// to instance
					Iterator<BlockingEventsInstance> itrZ = block
							.getInstanceBlockingEvents().iterator();
					while (itrZ.hasNext()) {
						BlockingEventsInstance instance = itrZ.next();
						if (model.getProcessName().equals(
								instance.getProcessName())
								&& model.getVersion().equals(
										instance.getVersion())) {
							removeBlockings(model.getEvents(),
									instance.getBlockEvents());
						}
					}
				}

				// instance to instance
				Iterator<InstanceEventBlocking> itrA = controller.message
						.getProcessInstanceEventBlockings().iterator();
				while (itrA.hasNext()) {
					InstanceEventBlocking instance1 = itrA.next();

					Iterator<BlockingEventsInstance> itrB = block
							.getInstanceBlockingEvents().iterator();
					while (itrB.hasNext()) {
						BlockingEventsInstance instance2 = itrB.next();
						if (instance1.getProcessID().equals(instance2.getID())) {
							removeBlockings(instance1.getEvents(),
									instance2.getBlockEvents());

							// @hahnml: Remove the activity blockings
							instance2.getActivityBlockEvents().clear();
						}
					}
				}

			}
		}
	}

	public void removeBlockings(Requested_Blocking_Events toRemove,
			Blocking_Events toModify) {
		if (toRemove.Activity_Executed == true) {
			toModify.Activity_Executed = false;
		}
		if (toRemove.Activity_Faulted == true) {
			toModify.Activity_Faulted = false;
		}
		if (toRemove.Activity_Ready == true) {
			toModify.Activity_Ready = false;
		}
		if (toRemove.Evaluating_TransitionCondition_Faulted == true) {
			toModify.Evaluating_TransitionCondition_Faulted = false;
		}
		/*
		 * if (toRemove.FaultHandling_NoHandler == true) {
		 * toModify.FaultHandling_NoHandler = false; }
		 */
		if (toRemove.Link_Evaluated == true) {
			toModify.Link_Evaluated = false;
		}
		if (toRemove.Loop_Condition_False == true) {
			toModify.Loop_Condition_False = false;
		}
		if (toRemove.Loop_Condition_True == true) {
			toModify.Loop_Condition_True = false;
		}
		if (toRemove.Loop_Iteration_Complete == true) {
			toModify.Loop_Iteration_Complete = false;
		}
		if (toRemove.Scope_Compensating == true) {
			toModify.Scope_Compensating = false;
		}
		if (toRemove.Scope_Complete_With_Fault == true) {
			toModify.Scope_Complete_With_Fault = false;
		}
		if (toRemove.Scope_Handling_Fault == true) {
			toModify.Scope_Handling_Fault = false;
		}
		if (toRemove.Scope_Handling_Termination == true) {
			toModify.Scope_Handling_Termination = false;
		}
	}

	// @stmz: we need to unblock the blocking events a unregistered controller
	// previously blocked
	// but never unblocked (and thus, will never unblock)
	public void cleanUpBlockingEvents(RegisterRequestMessage message) {
		synchronized (block.getBlockingEvents()) {
			LinkedList cleanUpList = new LinkedList<BlockingEvent>();

			Iterator<BlockingEvent> iter = block.getBlockingEvents().iterator();
			while (iter.hasNext()) {
				BlockingEvent tmp_event = iter.next();

				if (tmp_event.getBpelEvent() instanceof ActivityExecuted) {
					ActivityExecuted tmp = (ActivityExecuted) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Executed == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Executed) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Executed) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ActivityFaulted) {
					ActivityFaulted tmp = (ActivityFaulted) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Faulted == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ActivityJoinFailure) {
					ActivityJoinFailure tmp = (ActivityJoinFailure) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Faulted == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ActivityReady) {
					ActivityReady tmp = (ActivityReady) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Ready == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Ready) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Ready) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof EvaluatingTransitionConditionFaulted) {
					EvaluatingTransitionConditionFaulted tmp = (EvaluatingTransitionConditionFaulted) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Evaluating_TransitionCondition_Faulted == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Evaluating_TransitionCondition_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Evaluating_TransitionCondition_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				}
				/*
				 * else if (tmp_event.getBpelEvent() instanceof
				 * FaultHandlingNoHandler) { FaultHandlingNoHandler tmp =
				 * (FaultHandlingNoHandler) tmp_event.getBpelEvent(); QName
				 * procName = comm.cropQName(tmp.getProcess_name());
				 * 
				 * //global if
				 * (message.getGlobalEventBlockings().FaultHandling_NoHandler ==
				 * true) { cleanUpList.add(tmp_event); }
				 * 
				 * //processModel Iterator<ModelEventBlocking> itr1 =
				 * message.getProcessModelEventBlockings().iterator();
				 * while(itr1.hasNext()) { ModelEventBlocking mod = itr1.next();
				 * if (mod.getProcessName().equals(procName) &&
				 * mod.getVersion().equals(tmp_event.getProcessVersion())) { if
				 * (mod.getEvents().FaultHandling_NoHandler) { if
				 * (!cleanUpList.contains(tmp_event)) {
				 * cleanUpList.add(tmp_event); } } } }
				 * 
				 * //instance Iterator<InstanceEventBlocking> itr2 =
				 * message.getProcessInstanceEventBlockings().iterator();
				 * while(itr2.hasNext()) { InstanceEventBlocking inst =
				 * itr2.next(); if
				 * (inst.getProcessID().equals(tmp_event.getProcessID())) { if
				 * (inst.getEvents().FaultHandling_NoHandler) { if
				 * (!cleanUpList.contains(tmp_event)) {
				 * cleanUpList.add(tmp_event); } } } } }
				 */
				else if (tmp_event.getBpelEvent() instanceof IterationComplete) {
					IterationComplete tmp = (IterationComplete) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Loop_Iteration_Complete == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Loop_Iteration_Complete) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Loop_Iteration_Complete) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof LinkEvaluated) {
					LinkEvaluated tmp = (LinkEvaluated) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Link_Evaluated == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Link_Evaluated) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Link_Evaluated) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof LoopConditionFalse) {
					LoopConditionFalse tmp = (LoopConditionFalse) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Loop_Condition_False == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Loop_Condition_False) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Loop_Condition_False) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof LoopConditionTrue) {
					LoopConditionTrue tmp = (LoopConditionTrue) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Loop_Condition_True == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Loop_Condition_True) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Loop_Condition_True) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeActivityExecuted) {
					ScopeActivityExecuted tmp = (ScopeActivityExecuted) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Executed == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Executed) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Executed) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeActivityFaulted) {
					ScopeActivityFaulted tmp = (ScopeActivityFaulted) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Faulted == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Faulted) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeActivityReady) {
					ScopeActivityReady tmp = (ScopeActivityReady) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Activity_Ready == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Activity_Ready) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Activity_Ready) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeCompensating) {
					ScopeCompensating tmp = (ScopeCompensating) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Scope_Compensating == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Scope_Compensating) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Scope_Compensating) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeCompleteWithFault) {
					ScopeCompleteWithFault tmp = (ScopeCompleteWithFault) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Scope_Complete_With_Fault == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Scope_Complete_With_Fault) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Scope_Complete_With_Fault) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				} else if (tmp_event.getBpelEvent() instanceof ScopeHandlingFault) {
					ScopeHandlingFault tmp = (ScopeHandlingFault) tmp_event
							.getBpelEvent();
					QName procName = comm.cropQName(tmp.getProcess_name());

					// global
					if (message.getGlobalEventBlockings().Scope_Handling_Fault == true) {
						cleanUpList.add(tmp_event);
					}

					// processModel
					Iterator<ModelEventBlocking> itr1 = message
							.getProcessModelEventBlockings().iterator();
					while (itr1.hasNext()) {
						ModelEventBlocking mod = itr1.next();
						if (mod.getProcessName().equals(procName)
								&& mod.getVersion().equals(
										tmp_event.getProcessVersion())) {
							if (mod.getEvents().Scope_Handling_Fault) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}

					// instance
					Iterator<InstanceEventBlocking> itr2 = message
							.getProcessInstanceEventBlockings().iterator();
					while (itr2.hasNext()) {
						InstanceEventBlocking inst = itr2.next();
						if (inst.getProcessID()
								.equals(tmp_event.getProcessID())) {
							if (inst.getEvents().Scope_Handling_Fault) {
								if (!cleanUpList.contains(tmp_event)) {
									cleanUpList.add(tmp_event);
								}
							}
						}
					}
				}
			}

			block.getBlockingEvents().removeAll(cleanUpList);

			Iterator<BlockingEvent> iterat = cleanUpList.iterator();
			while (iterat.hasNext()) {
				BlockingEvent tmp = iterat.next();
				unblock(tmp.getBpelEvent());
			}
		}
	}

	// @stmz: unblock a blocking event to ensure that a process continues and
	// not gets deadlocked
	// in case something went wrong or a process unregisters without unblocking
	// everything that controller
	// blocked
	public void unblock(BpelEvent event) {
		if (event instanceof ActivityExecuted) {
			final ActivityExecuted tmp = (ActivityExecuted) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 5454446745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}

		}

		else if (event instanceof ActivityFaulted) {
			final ActivityFaulted tmp = (ActivityFaulted) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 4343446745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ActivityJoinFailure) {
			final ActivityJoinFailure tmp = (ActivityJoinFailure) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 3243335L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ActivityReady) {
			final ActivityReady tmp = (ActivityReady) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 2323332225L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof EvaluatingTransitionConditionFaulted) {
			final EvaluatingTransitionConditionFaulted tmp = (EvaluatingTransitionConditionFaulted) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 1211333146745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof FaultHandlingNoHandler) {
			final FaultHandlingNoHandler tmp = (FaultHandlingNoHandler) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 535555444333311145L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof IterationComplete) {
			final IterationComplete tmp = (IterationComplete) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 23222111133222L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof LinkEvaluated) {
			final LinkEvaluated evt = (LinkEvaluated) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 575757577522L;

				public void run() {
					LinkStatusChannel chan = importChannel(evt.getLink_name2(),
							LinkStatusChannel.class);
					chan.linkStatus(evt.getValue());

					// send event Link_Set_True or Link_Set_False
					if (evt.getValue()) {
						logger.fine("Link_Set_True!?%&$!" + evt.getLink_name()
								+ "!?%&$!" + evt.getXPath_Link() + "!?%&$!"
								+ evt.getProcess_name() + "!?%&$!"
								+ evt.getProcess_ID() + "!?%&$!"
								+ evt.getID_scope());

						Link_Set_True message2 = new Link_Set_True();
						comm.fillLinkEventMessage(message2,
								genCon.getTimestamp(), evt.getProcess_name(),
								evt.getProcess_ID(), evt.getID_scope(),
								evt.getXpath_surrounding_scope(),
								evt.getLink_name(), evt.getXPath_Link());
						comm.sendMessageToTopic(message2);
					} else {
						logger.fine("Link_Set_False!?%&$!" + evt.getLink_name()
								+ "!?%&$!" + evt.getXPath_Link() + "!?%&$!"
								+ evt.getProcess_name() + "!?%&$!"
								+ evt.getProcess_ID() + "!?%&$!"
								+ evt.getID_scope());

						Link_Set_False message2 = new Link_Set_False();
						comm.fillLinkEventMessage(message2,
								genCon.getTimestamp(), evt.getProcess_name(),
								evt.getProcess_ID(), evt.getID_scope(),
								evt.getXpath_surrounding_scope(),
								evt.getLink_name(), evt.getXPath_Link());
						comm.sendMessageToTopic(message2);
					}
				}

			};

			QName name = instanceEventHandler.getQName(evt.getProcess_ID());
			if (name != null) {
				addRunnable(evt.getProcess_ID(), runnable);
				timerEvent(evt.getProcess_ID(), name);
			}

		}

		else if (event instanceof LoopConditionFalse) {
			final LoopConditionFalse tmp = (LoopConditionFalse) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 23211226745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof LoopConditionTrue) {
			final LoopConditionTrue tmp = (LoopConditionTrue) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 435454761111745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeActivityExecuted) {
			final ScopeActivityExecuted tmp = (ScopeActivityExecuted) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 11122322111L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeActivityFaulted) {
			final ScopeActivityFaulted tmp = (ScopeActivityFaulted) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 5089998446745L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeActivityReady) {
			final ScopeActivityReady tmp = (ScopeActivityReady) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 67763221111L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeCompensating) {
			final ScopeCompensating tmp = (ScopeCompensating) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 92111122L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeCompleteWithFault) {
			final ScopeCompleteWithFault tmp = (ScopeCompleteWithFault) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 6545656667899095L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}

		else if (event instanceof ScopeHandlingFault) {
			final ScopeHandlingFault tmp = (ScopeHandlingFault) event;

			JacobRunnable runnable = new JacobRunnable() {
				private static final long serialVersionUID = 3235221115L;

				public void run() {
					LinkStatusChannel chan = importChannel(tmp.getLink_name(),
							LinkStatusChannel.class);
					chan.linkStatus(true);
				}

			};

			QName name = instanceEventHandler.getQName(tmp.getProcess_ID());
			if (name != null) {
				addRunnable(tmp.getProcess_ID(), runnable);
				timerEvent(tmp.getProcess_ID(), name);
			}
		}
	}

	public void Suspend_Instance(Suspend_Instance incEvent) {
		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			DebuggerSupport debug;
			synchronized (block.getDebuggers()) {
				debug = block.getDebuggers().get(name);
			}
			if (debug != null) {
				debug.suspend(incEvent.getProcessID());
			}
		}
	}

	public void Resume_Instance(Resume_Instance incEvent) {
		QName name = instanceEventHandler.getQName(incEvent.getProcessID());
		if (name != null) {
			DebuggerSupport debug;
			synchronized (block.getDebuggers()) {
				debug = block.getDebuggers().get(name);
			}
			if (debug != null) {
				debug.resume(incEvent.getProcessID());
			}
		}
	}

	public String convertToString(Node d) {
		try {
			Source source = new DOMSource(d);
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			Result result = new StreamResult(outStream);
			Transformer xformer;
			String tmp = "";
			xformer = TransformerFactory.newInstance().newTransformer();
			xformer.transform(source, result);
			tmp = outStream.toString();
			return tmp;
		} catch (Exception e) {
			return null;
		}

	}

	public List getRunnables() {
		return runnables;
	}

	public Scheduler getScheduler() {
		return scheduler;
	}

	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	// @stmz: one object of this class is created, whenever we receive an
	// incoming event
	// contains the JacobRunnable and the processID, so that we know, to which
	// process instance
	// the JacobRunnable belongs
	public class Runnable {
		private Long processID;
		private JacobRunnable runnable;

		public Runnable(Long proID, JacobRunnable run) {
			processID = proID;
			runnable = run;
		}

		public Long getProcessID() {
			return processID;
		}

		public JacobRunnable getRunnable() {
			return runnable;
		}

	}

}
