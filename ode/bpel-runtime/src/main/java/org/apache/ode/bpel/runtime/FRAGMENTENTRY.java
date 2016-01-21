package org.apache.ode.bpel.runtime;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.extensions.events.ActivityComplete;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.extensions.events.ActivityExecuting;
import org.apache.ode.bpel.extensions.events.ActivityReady;
import org.apache.ode.bpel.extensions.events.ActivityTerminated;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.runtime.channels.FragmentEntryMappedChannel;
import org.apache.ode.bpel.runtime.channels.FragmentEntryMappedChannelListener;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannelListener;
import org.apache.ode.bpel.runtime.channels.TerminationChannelListener;
import org.apache.ode.bpel.util.Pair;
import org.apache.ode.fc.dao.FCManagementDAO;
import org.apache.ode.fc.dao.MappingInfo;
import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FRAGMENTENTRY extends ACTIVITY {
	private static final Log __log = LogFactory.getLog(FRAGMENTENTRY.class);

	private class ACTIVE extends BpelJacobRunnable {
		private static final long serialVersionUID = 1L;

		private FragmentEntryMappedChannel channel;

		private ACTIVE(FragmentEntryMappedChannel channel) {
			this.channel = channel;

			// @hahnml: Set the OBase id
			oId = FRAGMENTENTRY.this.oId;
		}

		public void run() {
			object(false, new FragmentEntryMappedChannelListener(channel) {

				public void fragmentEntryMapped() {
					ElementMappingUtil.executeMapping(FRAGMENTENTRY.this);
					__log.debug("Mapped fragmentEntry: " + _self.o);
					Activity_Completed();
				}

				public void ignoreEntry() {
					__log.debug("Ignored fragmentEntry: " + _self.o);
					Activity_Completed();
				}
			});
		}
	}

	public FRAGMENTENTRY(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		super(self, scopeFrame, linkFrame);
	}

	@Override
	public void run() {
		if (__log.isDebugEnabled()) {
			__log.debug("<fragmentEntry name=" + _self.o + ">");
		}
		// State of the Activity is Ready
		LinkStatusChannel signal = newChannel(LinkStatusChannel.class);
		LinkStatusChannelListener receiver = new LinkStatusChannelListener(
				signal) {
			private static final long serialVersionUID = 1024111188875L;

			public void linkStatus(boolean value) {
				if (value) // Incoming Event Start_Activity received
				{
					FRAGMENTENTRY.this.execute();
				} else // Incoming Event Complete_Activity received
				{
					FRAGMENTENTRY.this.Activity_Completed();
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
				_self.self, FRAGMENTENTRY.this);
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evt);

	}

	public void Activity_Completed() {
		// dpe(_self.o.outgoingLinks);

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

		FragmentEntryMappedChannel channel = newChannel(FragmentEntryMappedChannel.class);
		Long instanceId = getBpelRuntimeContext().getPid();
		instance(new ACTIVE(channel));

		if (((OFragmentEntry) _self.o).danglingEntry) {
			FCManagementDAO fcManagementDAO = getBpelRuntimeContext()
					.getBpelProcess().getEngine().getFCManagementDAO();
			fcManagementDAO.addFragmentEntryChannel(instanceId,
					_self.o.getId(), channel.export());
		} else {
			channel.fragmentEntryMapped();
		}
	}

}
