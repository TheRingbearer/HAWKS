package org.apache.ode.bpel.runtime;

import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.FragmentCompositionResponse;
import org.apache.ode.bpel.engine.FragmentCompositionResponseDummy;
import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityFaulted;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OFragmentScope;
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
import org.apache.ode.jacob.SynchChannel;
import org.apache.ode.utils.fc.Mapping;
import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FRAGMENTREGION extends ACTIVITY {
	public FaultData _fault;
	private OFragmentRegion _oRegion;
	private static final Log __log = LogFactory.getLog(FRAGMENTREGION.class);

	public FRAGMENTREGION(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
		_oRegion = (OFragmentRegion) _self.o;

	}

	@Override
	public void run() {

		// State of Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 10241373711188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					FRAGMENTREGION.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					dpe(_self.o.outgoingLinks);
					FRAGMENTREGION.this.Activity_Complete(false,
							CompensationHandler.emptySet());
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
				_self.self, FRAGMENTREGION.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

		FragmentCompositionChannel compChannel = newChannel(
				FragmentCompositionChannel.class, "created in FRAGMENTREGION");

		Long pid = getBpelRuntimeContext().getPid();
		instance(new COMPOSER(compChannel));
		if (_oRegion.danglingExit) {
			FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
					.getBpelProcess().getEngine().getFCManagementDAO();
			fcManagementDAO.addGlueResponseChannel(pid, _oRegion.getId(),
					compChannel.export());
		} else {
			FragmentCompositionResponseDummy dummy = new FragmentCompositionResponseDummy();
			compChannel.wireAndMap(_oRegion.getId(), _oRegion.fragmentEntryId,
					_oRegion.mappings, dummy);
		}

	}

	public void Activity_Complete(Boolean terminate,
			Set<CompensationHandler> compens) {

		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();
		Long instanceId = getBpelRuntimeContext().getPid();
		fcManagementDAO.removeChannel(instanceId, _self.o.getId());

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

	private void mapVariablesBack() {
		ElementMappingUtil.executeMapping(this);
	}

	private class COMPOSER extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;

		private FragmentCompositionChannel compChannel;

		private COMPOSER(FragmentCompositionChannel compChannel) {
			this.compChannel = compChannel;
			
			// @hahnml: Set the OBase id
			oId = FRAGMENTREGION.this.oId;
		}

		public void run() {
			object(false, new FragmentCompositionChannelListener(compChannel) {
				private static final long serialVersionUID = -8140057051098543899L;

				public void glue(QName newFragmentName,
						FragmentCompositionResponse response) {
					if (_oRegion.child == null) {
						BpelEngineImpl engine = FragmentCompositionUtil
								.getEngine(FRAGMENTREGION.this);
						OProcess toGlue = engine.getProcess(newFragmentName)
								.getOProcessFromCBP();
						OFragmentScope scope = FragmentCompositionUtil
								.getScopeToGlue(_oRegion, toGlue);
						if (scope != null) {
							glueOTree(scope);
							FragmentCompositionUtil.merge(engine,
									getBpelRuntimeContext().getBpelProcess(),
									engine.getProcess(newFragmentName), toGlue,
									_oRegion);
							__log.info("Fragment "
									+ newFragmentName.getLocalPart()
									+ " is glued into " + _self.o);
							instance(new COMPOSER(compChannel));
							/*
							 * ActivityInfo info = new ActivityInfo(
							 * genMonotonic(), _oRegion.child,
							 * newChannel(TerminationChannel.class, "glued " +
							 * _oRegion.child),
							 * newChannel(ParentScopeChannel.class, "glued " +
							 * _oRegion.child));
							 * 
							 * instance(new ACTIVE(info)); // instantiate the
							 * glued child instance(createChild(info,
							 * _scopeFrame, _linkFrame));
							 */
							response.returnValue(true);
						} else {
							instance(new COMPOSER(compChannel));
							response.returnValue(false);
						}

					} else {
						response.throwException(new FragmentCompositionException(
								"Threre is already one fragment inside this FragmentRegion!"));
					}
				}

				public void wireAndMap(int fragmentExitId, int fragmentEntryId,
						Mapping[] mappings, FragmentCompositionResponse response) {

					try {
						OBase exit = _oRegion.getOwner().getChild(
								fragmentExitId);
						if (_oRegion.danglingExit
								&& exit instanceof OFragmentExit) {
							throw new FragmentCompositionException(
									"Region must be wired with frg:fragmentEntry first!");
						} else {
							ElementMappingUtil.wireAndMap(FRAGMENTREGION.this,
									fragmentExitId, fragmentEntryId, mappings);
							getBpelRuntimeContext().getBpelProcess()
									.serializeCompiledProcess(
											_oRegion.getOwner());

							if (exit != null && exit instanceof OFragmentExit) {
								Long instanceId = getBpelRuntimeContext()
										.getPid();
								FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
										.getBpelProcess().getEngine()
										.getFCManagementDAO();
								String channel = fcManagementDAO.getChannel(
										instanceId, fragmentExitId);
								if (channel != null) {
									FragmentCompositionResponseChannel responseChannel = importChannel(
											channel,
											FragmentCompositionResponseChannel.class);
									responseChannel
											.fragmentCompositionCompleted();
									fcManagementDAO.removeChannel(instanceId,
											exit.getId());
								}
							} else if (exit != null
									&& exit instanceof OFragmentRegion) {
								exitFinished();
							}

							instance(new COMPOSER(compChannel));
							ActivityInfo info = new ActivityInfo(
									genMonotonic(), _oRegion.child, newChannel(
											TerminationChannel.class, "glued "
													+ _oRegion.child),
									newChannel(ParentScopeChannel.class,
											"glued " + _oRegion.child));

							instance(new ACTIVE(info));
							// instantiate the glued child
							instance(createChild(info, _scopeFrame, _linkFrame));
							__log.info("Glued activity instance created");

							response.returnValue(true);
						}
					} catch (FragmentCompositionException e) {
						response.throwException(e);
						instance(new COMPOSER(compChannel));
					}

				}

				private void glueOTree(final OFragmentScope scope) {
					OProcess oldOwner = scope.activity.getOwner();
					scope.activity.getOwner().setNewRoot(_oRegion.getOwner());
					_oRegion.getOwner().adoptChildren(oldOwner);
					_oRegion.child = scope;
					scope.setParent(_oRegion);
					scope.processScope = false;
					_oRegion.getOwner().incrementGluedFragmentsCount();

				}

				// AH: TODO do i need the termination listener

				public void ignoreFragmentExit(int fragmentExitId,
						FragmentCompositionResponse response) {
					instance(new COMPOSER(compChannel));
					response.returnValue(false);
				}

				private void exitFinished() {
					Long instanceId = getBpelRuntimeContext().getPid();
					FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
							.getBpelProcess().getEngine().getFCManagementDAO();
					int fragmentEntryId = _oRegion.fragmentEntryId;
					String channelId = fcManagementDAO.getChannel(instanceId,
							fragmentEntryId);
					if (channelId != null) {
						FragmentEntryMappedChannel channel = importChannel(
								channelId, FragmentEntryMappedChannel.class);
						channel.fragmentEntryMapped();
						fcManagementDAO.removeChannel(instanceId,
								fragmentEntryId);
					}
				}

				public void ignoreFragmentEntry(int fragmentEntryId,
						FragmentCompositionResponse response) {
					instance(new COMPOSER(compChannel));
					response.returnValue(false);
				}
			});
		}
	}

	public String toString() {
		return "<T:Act:FragmentRegion:" + _oRegion.name + ">";
	}

	protected class ACTIVE extends BpelJacobRunnable {

		private static final long serialVersionUID = -7347779094771132494L;
		private ActivityInfo _child;
		private boolean _terminateRequested = false;

		ACTIVE(ActivityInfo child) {
			_child = child;

			// @hahnml: Set the OBase id
			oId = FRAGMENTREGION.this.oId;
		}

		public void run() {
			object(false, new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = -2680515407515637639L;

				public void terminate() {
					if (!_terminateRequested) {
						replication(_child.self).terminate();

						_terminateRequested = true;
					}
					instance(ACTIVE.this);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
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
							compensations);
					_fault = faultData;
					mapVariablesBack();
					Activity_Complete(_terminateRequested, comps);
				}

				public void cancelled() {
					completed(null, CompensationHandler.emptySet());
				}

				public void failure(String reason, Element data) {
					completed(null, CompensationHandler.emptySet());
				}
			}));
		}

	}
}
