package org.apache.ode.bpel.runtime;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.FragmentCompositionResponseDummy;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionChannel;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionResponseChannel;
import org.apache.ode.bpel.runtime.channels.FragmentCompositionResponseChannelListener;
import org.apache.ode.bpel.runtime.channels.FragmentEntryMappedChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;
import org.apache.ode.fc.dao.FCManagementDAO;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FRAGMENTEXIT extends ACTIVITY {
	static final long serialVersionUID = -1L;
	private static final Log __log = LogFactory.getLog(FRAGMENTEXIT.class);
	private FragmentCompositionResponseChannel compResponseChannel;

	// final QName process_name =
	// getBpelRuntimeContext().getBpelProcess().getPID();
	// final Long process_ID = getBpelRuntimeContext().getPid();

	public FRAGMENTEXIT(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	@Override
	public void run() {
		if (__log.isDebugEnabled()) {
			__log.debug("<fragmentExit name=" + _self.o + ">");
		}
		// State of the Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 1024111188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					FRAGMENTEXIT.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					FRAGMENTEXIT.this.Activity_Completed();
					// wtf?? is this
				}
			}

		};
		TerminationChannelListener termChan = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 154654344L;

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

		// Event Activity_Ready
		// @stmz: in the end GenericController gets notified about this event,
		// so this extension is responsible for
		// unblocking. This is done via the LinkStatusChannel signal, that is
		// passed via the event itself
		ActivityReady evt = new ActivityReady(_self.o.name, _self.o.getId(), _self.o.getXpath(),
				_self.aId, sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
				process_name, process_ID, _self.o.getArt(), false, signal,
				_self.self, FRAGMENTEXIT.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

	}

	public void Activity_Completed() {
		dpe(_self.o.outgoingLinks);

		LinkStatusChannel signal2 = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver2 = new LinkStatusChannelListener(
				signal2) {
			private static final long serialVersionUID = 265611588855L;

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
		TerminationChannelListener termChan2 = new TerminationChannelListener(
				_self.self) {
			private static final long serialVersionUID = 54744562L;

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

		object(false, (termChan2).or(receiver2));

		// Event Activity_Executed
		ActivityExecuted evt2 = new ActivityExecuted(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false, signal2);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt2);
	}

	public void execute() {
		// Event Activity_Executing
		ActivityExecuting evt = new ActivityExecuting(_self.o.name, _self.o.getId(),
				_self.o.getXpath(), _self.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				_self.o.getArt(), false);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);
		OFragmentExit exit = (OFragmentExit) _self.o;
		/* activity logic begin */

		compResponseChannel = newChannel(FragmentCompositionResponseChannel.class);
		Long instanceId = getBpelRuntimeContext().getPid();
		FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
				.getBpelProcess().getEngine().getFCManagementDAO();

		fcManagementDAO.addFragmentExitChannel(instanceId, exit.getId(),
				compResponseChannel.export());
		instance(new WAITING());
		if (!exit.danglingExit) {
			// this is for loops around the exit = automatical wire and map
			BpelEngineImpl engine = getBpelRuntimeContext().getBpelProcess()
					.getEngine();

			int containerId = FragmentCompositionUtil
					.findEnclosingFragmentContainer(exit.getOwner(),
							exit.getId());
			String channel = fcManagementDAO
					.getChannel(instanceId, containerId);
			// Runnable onSuccess = new CleanUpChannelsTask(exit.getOwner(),
			// engine, getBpelRuntimeContext().getPid());
			FragmentCompositionResponseDummy dummy = new FragmentCompositionResponseDummy();
			// dummy.setOnSuccessAction(onSuccess);
			if (exit.ignoredExit) {
				if (channel != null) {
					FragmentCompositionChannel fcChannel = importChannel(
							channel, FragmentCompositionChannel.class);
					fcChannel.ignoreFragmentExit(exit.getId(), dummy);
				}
			} else {

				if (channel != null) {
					FragmentCompositionChannel fcChannel = importChannel(
							channel, FragmentCompositionChannel.class);
					fcChannel.wireAndMap(exit.getId(), exit.fragmentEntryId,
							exit.mappings, dummy);
				}
			}
		}

	}

	private class WAITING extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;
		
		WAITING() {
			// @hahnml: Set the OBase id
			oId = FRAGMENTEXIT.this.oId;
		}

		public void run() {
			object(false, new FragmentCompositionResponseChannelListener(
					compResponseChannel) {
				private static final long serialVersionUID = -5890554138001498791L;

				public void fragmentCompositionCompleted() {

					Long instanceId = getBpelRuntimeContext().getPid();
					FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
							.getBpelProcess().getEngine().getFCManagementDAO();
					int fragmentEntryId = ((OFragmentExit) _self.o).fragmentEntryId;
					String channelId = fcManagementDAO.getChannel(instanceId,
							fragmentEntryId);
					OBase entry = _self.o.getOwner().getChild(fragmentEntryId);
					if (channelId != null && entry instanceof OFragmentEntry) {
						FragmentEntryMappedChannel channel = importChannel(
								channelId, FragmentEntryMappedChannel.class);
						channel.fragmentEntryMapped();
						fcManagementDAO.removeChannel(instanceId,
								fragmentEntryId);
					} else {
						// else it is mapped to fragmentRegion
					}
					__log.debug("Completed fragmentExit: " + _self.o);
					// Event Activity_Executed
					Activity_Completed();

				}

			}.or(new TerminationChannelListener(_self.self) {
				private static final long serialVersionUID = 4399496341785922396L;

				public void terminate() {
					// Activity Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					dpe(_self.o.outgoingLinks);

					// getBpelRuntimeContext().cancel(_glueResponseChannel);
					// onCancel() will be executed next
					instance(WAITING.this);
				}
				
				//krwczk: TODO -implement skip
				public void skip() {
					
				}
			}));
		}

		public void Activity_Completed() {
			LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
			LinkStatusChannelListener receiver = new LinkStatusChannelListener(
					signal) {
				private static final long serialVersionUID = 64634233L;

				public void linkStatus(boolean value) {

					// Event Activity_Complete
					ActivityComplete evt = new ActivityComplete(_self.o.name, _self.o.getId(),
							_self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_self.parent
							.completed(null, CompensationHandler.emptySet());
				}

			};
			TerminationChannelListener termChan = new TerminationChannelListener(
					_self.self) {
				private static final long serialVersionUID = 86237698L;

				public void terminate() {

					// Event Activity_Terminated
					ActivityTerminated evt = new ActivityTerminated(
							_self.o.name, _self.o.getId(), _self.o.getXpath(), _self.aId,
							sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
							process_name, process_ID, _self.o.getArt(), false);
					getBpelRuntimeContext().getBpelProcess().getEngine()
							.fireEvent(evt);
					_terminatedActivity = true;
					_self.parent
							.completed(null, CompensationHandler.emptySet());
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
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(evt2);
		}

	}

}
