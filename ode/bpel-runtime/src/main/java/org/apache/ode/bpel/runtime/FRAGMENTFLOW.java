package org.apache.ode.bpel.runtime;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.FragmentCompositionResponse;
import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OConstantExpression;
import org.apache.ode.bpel.o.OExpressionLanguage;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentFlow;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannel;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannelListener;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionResponseChannel;
import org.apache.ode.bpel.runtime.channels.FragmentEntryMappedChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.jacob.ChannelListener;
import org.apache.ode.jacob.SynchChannel;
import org.apache.ode.utils.GUID;
import org.apache.ode.utils.fc.Mapping;
import org.apache.ode.utils.stl.FilterIterator;
import org.apache.ode.utils.stl.MemberOfFunction;
import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
class FRAGMENTFLOW extends ACTIVITY {
	private static final long serialVersionUID = 1L;
	private static final Log __log = LogFactory.getLog(FRAGMENTFLOW.class);
	private OFragmentFlow _oflow;

	private Set<ChildInfo> _children = new HashSet<ChildInfo>();

	final QName process_name = getBpelRuntimeContext().getBpelProcess()
			.getPID();
	final Long process_ID = getBpelRuntimeContext().getPid();

	private LinkFrame myLinkFrame;

	public FRAGMENTFLOW(ActivityInfo self, ScopeFrame frame, LinkFrame linkFrame) {
		super(self, frame, linkFrame);
		_oflow = (OFragmentFlow) self.o;
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
					FRAGMENTFLOW.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					dpe(_self.o.outgoingLinks);
					FRAGMENTFLOW.this.Activity_Completed(null,
							CompensationHandler.emptySet(), false);
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 1980436L;

			public void terminate() {
				// Event Activity_Terminated
				ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
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
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(), _self.o.getXpath(),
				_self.aId, sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
				process_name, process_ID, _self.o.getArt(), false, signal,
				_self.self, FRAGMENTFLOW.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
	}

	public void execute() {
		Long instanceId = getBpelRuntimeContext().getPid();
		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();

		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		myLinkFrame = new LinkFrame(_linkFrame);
		for (Iterator<OLink> i = _oflow.localLinks.iterator(); i.hasNext();) {
			OLink link = i.next();
			LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
			myLinkFrame.links.put(link, new LinkInfo(link, lsc, lsc));
		}

		FragmentCompositionChannel compChannel = null;

		Long pid = getBpelRuntimeContext().getPid();

		for (Iterator<OActivity> i = _oflow.parallelActivities.iterator(); i
				.hasNext();) {
			OActivity ochild = i.next();

			// AH: fragment exit needs channel to signal FLOW activity to glue
			// fragment etc.
			ActivityInfo info;

			// use the same channel for all instances of Composition
			if (compChannel == null) {
				compChannel = newChannel(FragmentCompositionChannel.class,
						"created in FLOW");
			}

			info = new ActivityInfo(genMonotonic(), ochild, newChannel(
					TerminationChannel.class, ochild.toString()), newChannel(
					ParentScopeChannel.class, ochild.toString()));

			ChildInfo childInfo = new ChildInfo(info);

			// AH: end

			_children.add(childInfo);

			instance(createChild(childInfo.activity, _scopeFrame, myLinkFrame));
		}
		// AH:

		fcManagementDAO.addGlueResponseChannel(pid, _oflow.getId(),
				compChannel.export());
		instance(new COMPOSER(compChannel));

		// AH: end
		instance(new ACTIVE());
	}

	private void glueOTree(final OFragmentScope scope) {

		OProcess oldOwner = scope.getOwner();
		scope.getOwner().setNewRoot(_oflow.getOwner());
		_oflow.getOwner().adoptChildren(oldOwner);
		_oflow.parallelActivities.add(scope);
		_oflow.nested.add(scope);
		scope.setParent(_oflow);
		scope.processScope = false;
		_oflow.getOwner().incrementGluedFragmentsCount();

		/*
		 * List<OBase> gluedChildren = scope.getOldOwner().getChildren(); for
		 * (OBase element: gluedChildren){ if (element instanceof
		 * OFragmentEntry) { OFragmentEntry entry = (OFragmentEntry) element;
		 * 
		 * OLink link = new OLink(_oflow.getOwner()); link.target = entry;
		 * link.source = null; GUID id = new GUID(); link.name =
		 * String.valueOf(id); link.declaringFlow = _oflow;
		 * _oflow.localLinks.add(link); entry.targetLinks.add(link);
		 * LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
		 * myLinkFrame.links.put(link, new LinkInfo(link, lsc, lsc));
		 * 
		 * } }
		 */

		// getBpelRuntimeContext().getBpelProcess().serializeCompiledProcess(_oflow.getOwner());
		// rekursiv bei allen fragment entries die links erstellen und die auch
		// und in die oactivity von den fragment entries diese hinzufügen.
	}

	private class ACTIVE extends BpelJacobRunnable {
		private static final long serialVersionUID = -8494641460279049245L;
		private FaultData _fault;
		private HashSet<CompensationHandler> _compensations = new HashSet<CompensationHandler>();

		private Boolean terminated = false;
		
		ACTIVE() {
			// @hahnml: Set the OBase id
			oId = FRAGMENTFLOW.this.oId;
		}

		public void run() {
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
						}

						public void compensate(OScope scope, SynchChannel ret) {
							// Flow does not do compensations, forward these to
							// parent.
							_self.parent.compensate(scope, ret);
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
			} else /** No More active children. */
			{
				// NOTE: we do not not have to do DPE here because all the
				// children
				// have been started, and are therefore expected to set the
				// value of
				// their outgoing links.
				FRAGMENTFLOW.this.Activity_Completed(_fault, _compensations,
						terminated);
			}
		}
	}

	public void Activity_Completed(FaultData fault,
			Set<CompensationHandler> comps, Boolean terminate) {

		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();
		Long instanceId = getBpelRuntimeContext().getPid();
		fcManagementDAO.removeChannel(instanceId, _self.o.getId());

		final FaultData tmp = fault;
		final Set<CompensationHandler> compen_tmp = comps;

		if (terminate) {
			// Event Activity_Terminated
			ActivityTerminated evt = new ActivityTerminated(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
			_terminatedActivity = true;
			_self.parent.completed(tmp, compen_tmp);
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
				ActivityFaulted evt2 = new ActivityFaulted(_self.o.name, _self.o.getId(),
						_self.o.getXpath(), _self.aId,
						sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
						process_name, process_ID, _self.o.getArt(), false,
						signal, tmp);
				getBpelRuntimeContext().getBpelProcess().getEngine()
						.fireEvent(evt2);
			} else {
				LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
				LinkStatusChannelListener receiver = new LinkStatusChannelListener(
						signal) {
					private static final long serialVersionUID = 86865982355L;

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
					private static final long serialVersionUID = 453533435527L;

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
		return "<T:Act:FragmentFlow:" + _oflow.name + ">";
	}

	private Iterator<ChildInfo> active() {
		return new FilterIterator<ChildInfo>(_children.iterator(),
				new MemberOfFunction<ChildInfo>() {
					public boolean isMember(ChildInfo childInfo) {
						return !childInfo.completed;
					}
				});
	}

	// AH:
	private class COMPOSER extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;

		private FragmentCompositionChannel compChannel;

		private COMPOSER(FragmentCompositionChannel compChannel) {
			this.compChannel = compChannel;
			
			// @hahnml: Set the OBase id
			oId = FRAGMENTFLOW.this.oId;
		}

		public void run() {
			object(false, new FragmentCompositionChannelListener(compChannel) {
				private static final long serialVersionUID = -8140057051098543899L;

				public void glue(QName newFragmentName,
						FragmentCompositionResponse response) {

					if (FragmentCompositionUtil.isGlueAllowed(_oflow.getOwner())) {
						BpelEngineImpl engine = FragmentCompositionUtil
								.getEngine(FRAGMENTFLOW.this);
						OProcess toGlue = engine.getProcess(newFragmentName)
								.getOProcessFromCBP();
						OFragmentScope scope = FragmentCompositionUtil
								.getScopeToGlue(_oflow, toGlue);
						if (scope != null) {
							glueOTree(scope);
							FragmentCompositionUtil.merge(engine,
									getBpelRuntimeContext().getBpelProcess(),
									engine.getProcess(newFragmentName), toGlue,
									_oflow);

							__log.info("Fragment "
									+ newFragmentName.getLocalPart()
									+ " is glued into " + _self.o);
							ChildInfo childInfo = new ChildInfo(
									new ActivityInfo(genMonotonic(), scope,
											newChannel(
													TerminationChannel.class,
													"glued " + scope),
											newChannel(
													ParentScopeChannel.class,
													"glued " + scope)));

							// instantiate the glued child
							_children.add(childInfo);

							response.returnValue(true);
							instance(createChild(childInfo.activity,
									_scopeFrame, myLinkFrame));
							__log.info("Glued activity instance created");
							instance(new COMPOSER(compChannel));

						} else {
							instance(new COMPOSER(compChannel));
							response.returnValue(false);
						}
					}
				}

				private void addLinkIfNeeded(int fragmentExitId,
						int fragmentEntryId) {
					OBase exit = _oflow.getOwner().getChild(fragmentExitId);
					OBase entry = _oflow.getOwner().getChild(fragmentEntryId);
					if (exit != null && exit instanceof OFragmentExit
							&& entry != null && entry instanceof OFragmentEntry) {
						OFragmentExit fragmentExit = (OFragmentExit) exit;
						OFragmentEntry fragmentEntry = (OFragmentEntry) entry;
						if (fragmentExit.sourceLinks.size() == 0) {
							OLink link = new OLink(_oflow.getOwner());
							link.target = fragmentEntry;
							link.source = fragmentExit;
							GUID id = new GUID();
							link.name = String.valueOf(id);
							link.declaringFlow = _oflow;
							_oflow.localLinks.add(link);
							fragmentEntry.targetLinks.add(link);
							fragmentExit.sourceLinks.add(link);
							LinkStatusChannel lsc = newChannel(LinkStatusChannel.class);
							myLinkFrame.links.put(link, new LinkInfo(link, lsc,
									lsc));
						}
					}
				}

				public void wireAndMap(int fragmentExitId, int fragmentEntryId,
						Mapping[] mappings, FragmentCompositionResponse response) {
					try {
						OBase exit = _oflow.getOwner().getChild(fragmentExitId);
						ElementMappingUtil.wireAndMap(FRAGMENTFLOW.this,
								fragmentExitId, fragmentEntryId, mappings);
						if (exit != null && exit instanceof OFragmentExit) {
							addLinkIfNeeded(fragmentExitId, fragmentEntryId);
						}
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(_oflow.getOwner());

						if (exit != null && exit instanceof OFragmentExit) {
							addLinkIfNeeded(fragmentExitId, fragmentEntryId);

							Long instanceId = getBpelRuntimeContext().getPid();
							FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
									.getBpelProcess().getEngine()
									.getFCManagementDAO();

							String channel = fcManagementDAO.getChannel(
									instanceId, fragmentExitId);
							if (channel != null) {
								FragmentCompositionResponseChannel responseChannel = importChannel(
										channel,
										FragmentCompositionResponseChannel.class);
								responseChannel.fragmentCompositionCompleted();
								fcManagementDAO.removeChannel(instanceId,
										exit.getId());
							} else {
								throw new FragmentCompositionException(
										"FragmentCompositionResponseChannel could not be found!");
							}
						}

						if (FragmentCompositionUtil.isGlueAllowed(_oflow
								.getOwner())) {
							instance(new COMPOSER(compChannel));
						}

						response.returnValue(true);

					} catch (FragmentCompositionException e) {
						response.throwException(e);
						instance(new COMPOSER(compChannel));
					}
				}

				// AH: TODO do i need the termination listener

				public void ignoreFragmentExit(int fragmentExitId,
						FragmentCompositionResponse response) {

					FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
							.getBpelProcess().getEngine().getFCManagementDAO();
					Long instanceId = getBpelRuntimeContext().getPid();

					OBase exit = _oflow.getOwner().getChild(fragmentExitId);
					if (exit != null && exit instanceof OFragmentExit) {
						OFragmentExit fragmentExit = (OFragmentExit) exit;

						boolean exitWasAlreadyIgnored = fragmentExit.ignoredExit;

						fragmentExit.danglingExit = false;
						fragmentExit.ignoredExit = true;
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(_self.o.getOwner());
						String channel = fcManagementDAO.getChannel(instanceId,
								fragmentExit.getId());
						if (channel != null) {
							FragmentCompositionResponseChannel respChannel = importChannel(
									channel,
									FragmentCompositionResponseChannel.class);
							respChannel.fragmentCompositionCompleted();
							fcManagementDAO.removeChannel(instanceId,
									fragmentExit.getId());
							if (!exitWasAlreadyIgnored) {
								_oflow.getOwner()
										.incrementGluedFragmentsCount();
							}

						} else {
							response.throwException(new FragmentCompositionException(
									"FragmentCompositionResponseChannel could not be found!"));
							if (FragmentCompositionUtil.isGlueAllowed(_oflow
									.getOwner())) {
								instance(new COMPOSER(compChannel));
							}
							return;
						}

						response.returnValue(true);
					} else {
						response.returnValue(false);
					}

					if (FragmentCompositionUtil.isGlueAllowed(_oflow.getOwner())) {
						instance(new COMPOSER(compChannel));
					}

				}

				public void ignoreFragmentEntry(int fragmentEntryId,
						FragmentCompositionResponse response) {
					FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
							.getBpelProcess().getEngine().getFCManagementDAO();
					Long instanceId = getBpelRuntimeContext().getPid();

					OBase entry = _oflow.getOwner().getChild(fragmentEntryId);
					if (entry != null && entry instanceof OFragmentEntry) {
						OFragmentEntry fragmentEntry = (OFragmentEntry) entry;

						fragmentEntry.danglingEntry = false;
						fragmentEntry.ignoredEntry = true;
						String channel = fcManagementDAO.getChannel(instanceId,
								fragmentEntry.getId());
						if (channel != null) {
							FragmentEntryMappedChannel respChannel = importChannel(
									channel, FragmentEntryMappedChannel.class);
							setTransitionConditionToFalse(fragmentEntry);
							respChannel.ignoreEntry();
							fcManagementDAO.removeChannel(instanceId,
									fragmentEntry.getId());

						} else {
							response.throwException(new FragmentCompositionException(
									"FragmentEntryMappedChannel could not be found!"));
							if (FragmentCompositionUtil.isGlueAllowed(_oflow
									.getOwner())) {
								instance(new COMPOSER(compChannel));
							}
							return;
						}

						response.returnValue(true);
					} else {
						response.returnValue(false);
					}

					if (FragmentCompositionUtil.isGlueAllowed(_oflow.getOwner())) {
						instance(new COMPOSER(compChannel));
					}

				}

				private void setTransitionConditionToFalse(OFragmentEntry entry) {
					// set transition condition of outgoing links to false
					Set<OLink> links = entry.sourceLinks;
					OProcess process = _self.o.getOwner();
					OExpressionLanguage lang;
					if (links.size() > 0) {
						lang = new OExpressionLanguage(process, null);
						lang.expressionLanguageUri = "uri:www.fivesight.com/konstExpression";
						lang.properties
								.put("runtime-class",
										"org.apache.ode.bpel.runtime.explang.konst.KonstExpressionLanguageRuntimeImpl");
						process.expressionLanguages.add(lang);
						for (OLink link : links) {
							OConstantExpression exp = new OConstantExpression(
									process, new Boolean(false));
							exp.expressionLanguage = lang;
							link.transitionCondition = exp;
						}
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(_self.o.getOwner());
					}

				}
			});
		}
	}
	// AH: end
}
