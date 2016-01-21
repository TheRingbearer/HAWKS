package org.apache.ode.bpel.runtime;

import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.FragmentCompositionResponse;
import org.apache.ode.bpel.engine.fc.excp.FragmentCompositionException;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OFragmentSequence;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannel;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannelListener;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionResponseChannel;
import org.apache.ode.bpel.runtime.channels.FragmentEntryMappedChannel;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.utils.fc.Mapping;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FRAGMENTSEQUENCE extends SEQUENCE {
	private static final long serialVersionUID = 1L;
	private OFragmentSequence _ofSequence;
	private static final Log __log = LogFactory.getLog(FRAGMENTSEQUENCE.class);

	FRAGMENTSEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
		_ofSequence = (OFragmentSequence) _self.o;
	}

	FRAGMENTSEQUENCE(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, List<OActivity> remaining,
			Set<CompensationHandler> compensations, Boolean firstT) {
		super(self, scopeFrame, linkFrame, remaining, compensations, firstT);
		_ofSequence = (OFragmentSequence) _self.o;
	}

	public void execute() {
		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();
		Long instanceId = getBpelRuntimeContext().getPid();
		if (_firstTime) {
			// Event Activity_Executing
			ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
					_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					_self.o.getArt(), false);
			getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

			FragmentCompositionChannel compChannel = newChannel(
					FragmentCompositionChannel.class, "created in FSequence");

			fcManagementDAO.addGlueResponseChannel(instanceId,
					_ofSequence.getId(), compChannel.export());

		}

		ActivityInfo info = null;
		OActivity child = _remaining.get(0);
		if (child instanceof OFragmentExit || child instanceof OFragmentEntry) {
			instance(new COMPOSER());
		}
		info = new ActivityInfo(genMonotonic(), child,
				newChannel(TerminationChannel.class), newChannel(
						ParentScopeChannel.class, "sequence"
								+ FRAGMENTSEQUENCE.this._self.toString()));

		instance(createChild(info, _scopeFrame, _linkFrame));
		final ActivityInfo childInfo = info;
		instance(new ACTIVE(childInfo));
	}

	protected void createInstance(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, List<OActivity> remaining,
			Set<CompensationHandler> compensations, Boolean firstT) {
		instance(new FRAGMENTSEQUENCE(_self, _scopeFrame, _linkFrame,
				remaining, compensations, false));
	}

	private class COMPOSER extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;
		private FragmentCompositionChannel compChannel;

		public COMPOSER() {
			FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
					.getBpelProcess().getEngine().getFCManagementDAO();
			Long instanceId = getBpelRuntimeContext().getPid();
			String channel = fcManagementDAO.getChannel(instanceId,
					_self.o.getId());
			compChannel = importChannel(channel,
					FragmentCompositionChannel.class);
			
			// @hahnml: Set the OBase id
			oId = FRAGMENTSEQUENCE.this.oId;
		}

		public void run() {
			object(false, new FragmentCompositionChannelListener(compChannel) {
				private static final long serialVersionUID = -8140057051098543899L;

				public void glue(QName newFragmentName,
						FragmentCompositionResponse response) {

					if (FragmentCompositionUtil.isGlueAllowed(_ofSequence
							.getOwner())) {

						BpelEngineImpl engine = FragmentCompositionUtil
								.getEngine(FRAGMENTSEQUENCE.this);
						OProcess toGlue = engine.getProcess(newFragmentName)
								.getOProcessFromCBP();
						OFragmentScope scope = FragmentCompositionUtil
								.getScopeToGlue(_ofSequence, toGlue);
						if (scope != null) {
							glueOTree(scope);
							FragmentCompositionUtil.merge(engine,
									getBpelRuntimeContext().getBpelProcess(),
									engine.getProcess(newFragmentName), toGlue,
									_ofSequence);
							__log.info("Fragment "
									+ newFragmentName.getLocalPart()
									+ " is glued into " + _self.o);
							// instantiation happens in wireAndMap
							// to prevent execution of fragmentEntry before
							// fragmentExit is finished
							instance(new COMPOSER());
							response.returnValue(true);
						} else {
							instance(new COMPOSER());
							response.returnValue(false);
						}
					}
				}

				public void wireAndMap(int fragmentExitId, int fragmentEntryId,
						Mapping[] mappings, FragmentCompositionResponse response) {
					try {
						ElementMappingUtil.wireAndMap(FRAGMENTSEQUENCE.this,
								fragmentExitId, fragmentEntryId, mappings);
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(
										_ofSequence.getOwner());
						OBase exit = _ofSequence.getOwner().getChild(
								fragmentExitId);
						if (exit != null && exit instanceof OFragmentExit) {
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
						response.returnValue(true);
					} catch (FragmentCompositionException e) {
						instance(new COMPOSER());
						response.throwException(e);

					}
				}

				private void glueOTree(final OFragmentScope scope) {
					OProcess oldOwner = scope.activity.getOwner();
					scope.activity.getOwner()
							.setNewRoot(_ofSequence.getOwner());
					_ofSequence.getOwner().adoptChildren(oldOwner);
					_remaining.add(scope);
					_ofSequence.sequence.add(scope);
					scope.setParent(_ofSequence);
					scope.processScope = false;
					_ofSequence.getOwner().incrementGluedFragmentsCount();

				}

				public void ignoreFragmentExit(int fragmentExitId,
						FragmentCompositionResponse response) {

					OBase exit = _ofSequence.getOwner()
							.getChild(fragmentExitId);
					if (exit != null && exit instanceof OFragmentExit) {
						OFragmentExit fragmentExit = (OFragmentExit) exit;

						boolean exitWasAlreadyIgnored = fragmentExit.ignoredExit;
						fragmentExit.danglingExit = false;
						fragmentExit.ignoredExit = true;
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(_self.o.getOwner());
						Long instanceId = getBpelRuntimeContext().getPid();
						FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
								.getBpelProcess().getEngine()
								.getFCManagementDAO();
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
								_ofSequence.getOwner()
										.incrementGluedFragmentsCount();
							}
							response.returnValue(true);
						} else {
							instance(new COMPOSER());
							response.throwException(new FragmentCompositionException(
									"FragmentCompositionResponseChannel could not be found!"));
						}

					} else {
						response.returnValue(false);
					}

				}

				public void ignoreFragmentEntry(int fragmentEntryId,
						FragmentCompositionResponse response) {
					OBase entry = _ofSequence.getOwner().getChild(
							fragmentEntryId);
					if (entry != null && entry instanceof OFragmentEntry) {
						OFragmentEntry fragmentEntry = (OFragmentEntry) entry;

						fragmentEntry.danglingEntry = false;
						fragmentEntry.ignoredEntry = true;
						getBpelRuntimeContext().getBpelProcess()
								.serializeCompiledProcess(_self.o.getOwner());
						Long instanceId = getBpelRuntimeContext().getPid();
						FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
								.getBpelProcess().getEngine()
								.getFCManagementDAO();
						String channel = fcManagementDAO.getChannel(instanceId,
								fragmentEntry.getId());
						if (channel != null) {
							FragmentEntryMappedChannel respChannel = importChannel(
									channel, FragmentEntryMappedChannel.class);
							respChannel.ignoreEntry();
							fcManagementDAO.removeChannel(instanceId,
									fragmentEntry.getId());

							response.returnValue(true);
						} else {
							instance(new COMPOSER());
							response.throwException(new FragmentCompositionException(
									"FragmentCompositionResponseChannel could not be found!"));
						}

					} else {
						response.returnValue(false);
					}

				}
			});
		}
	}

	public String toString() {
		return "<T:Act:FragmentSequence:" + _ofSequence.name + ">";
	}

	protected void onActivityComplete() {
		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();
		Long instanceId = getBpelRuntimeContext().getPid();
		fcManagementDAO.removeChannel(instanceId, _self.o.getId());
	}
}
