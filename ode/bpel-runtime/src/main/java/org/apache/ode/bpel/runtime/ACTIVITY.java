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

import java.io.Serializable;
import java.util.Collection;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.dao.PartnerLinkDAO;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.ScopeDAO;
import org.apache.ode.bpel.dao.SnapshotDAO;
import org.apache.ode.bpel.dao.SnapshotPartnerlinksDAO;
import org.apache.ode.bpel.dao.SnapshotVariableDAO;
import org.apache.ode.bpel.dao.XmlDataDAO;
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.evt.ActivityEvent;
import org.apache.ode.bpel.evt.EventContext;
import org.apache.ode.bpel.evt.ScopeEvent;
import org.apache.ode.bpel.evt.VariableReadEvent;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.extensions.events.ActivityDeadPath;
import org.apache.ode.bpel.extensions.events.LinkEvaluated;
import org.apache.ode.bpel.extensions.events.LinkReady;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OConstants;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.bpel.o.OMessageVarType;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OMessageVarType.Part;
import org.apache.ode.bpel.runtime.channels.ParentScopeChannel;
import org.apache.ode.bpel.runtime.channels.TerminationChannel;

import org.apache.ode.jacob.IndexedObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Base template for activities.
 */
public abstract class ACTIVITY extends BpelJacobRunnable implements
		IndexedObject {
	
	
	private static final Log __log = LogFactory.getLog(ACTIVITY.class);

	// changed by Bo Ning(from protected to public)
	public ActivityInfo _self;

	/**
	 * Permeability flag, if <code>false</code> we defer outgoing links until
	 * successful completion.
	 */
	protected boolean _permeable = true;

	public ScopeFrame _scopeFrame;

	public LinkFrame _linkFrame;

	// changed by Bo Ning: deleted "final"
	QName process_name;
	Long process_ID;

	// @stmz
	protected ScopeFrame sFrame;
	protected ScopeFrame pFrame;
	protected Boolean _terminatedActivity;
	protected Boolean _skippedActivity;

	public ACTIVITY(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame) {
		assert self != null;
		assert scopeFrame != null;
		assert linkFrame != null;

		process_name = getBpelRuntimeContext().getBpelProcess().getPID();
		process_ID = getBpelRuntimeContext().getPid();
		
		//@hahnml: Set the id to the JacobObject
		this.oId = self.o.getId();

		_self = self;
		_scopeFrame = scopeFrame;
		_linkFrame = linkFrame;
		_terminatedActivity = false;
		_skippedActivity = false;

		getFrames();
	}

	// added by Bo Ning
	public ACTIVITY(ActivityInfo self, ScopeFrame scopeFrame,
			LinkFrame linkFrame, QName processname, Long pid) {
		assert self != null;
		assert scopeFrame != null;
		assert linkFrame != null;
		
		//@hahnml: Set the id to the JacobObject
		this.oId = self.o.getId();

		process_name = processname;
		process_ID = pid;
		_self = self;
		_scopeFrame = scopeFrame;
		_linkFrame = linkFrame;
		_terminatedActivity = false;
		_skippedActivity = false;

		getFrames();
	}

	// @stmz: identify the scope we are part of, but ignore the scopes, that are
	// not relevant
	// for example, a catch block is handled as a Scope activity in ODE
	public void getFrames() {
		ScopeFrame s = _scopeFrame;
		sFrame = _scopeFrame;
		while (s != null) {
			if (!s.ignore) {
				sFrame = s;
				break;
			}
			s = s.parent;
		}

		pFrame = s.parent;
		ScopeFrame p = s.parent;
		while (p != null) {
			if (!p.ignore) {
				pFrame = p;
				break;
			}
			p = p.parent;
		}
	}

	public Object getKey() {
		return new Key(_self.o, _self.aId);
	}

	protected void sendVariableReadEvent(VariableInstance var) {
		VariableReadEvent vre = new VariableReadEvent();
		vre.setVarName(var.declaration.name);
		sendEvent(vre);
	}

	protected void sendEvent(ActivityEvent event) {
		event.setActivityName(_self.o.name);
		event.setActivityType(_self.o.getType());
		event.setActivityDeclarationId(_self.o.getId());
		event.setActivityId(_self.aId);
		if (event.getLineNo() == -1) {
			event.setLineNo(getLineNo());
		}
		sendEvent((ScopeEvent) event);
	}

	protected void sendEvent(ScopeEvent event) {
		if (event.getLineNo() == -1 && _self.o.debugInfo != null) {
			event.setLineNo(_self.o.debugInfo.startLine);
		}
		_scopeFrame.fillEventInfo(event);
		fillEventContext(event);
		getBpelRuntimeContext().sendEvent(event);
	}

	/**
	 * Populate BpelEventContext, to be used by Registered Event Listeners
	 * 
	 * @param event
	 *            ScopeEvent
	 */
	protected void fillEventContext(ScopeEvent event) {
		EventContext eventContext = new EventContextImpl(_scopeFrame.oscope,
				_scopeFrame.scopeInstanceId, getBpelRuntimeContext());
		event.eventContext = eventContext;
	}

	protected void dpe(Collection<OLink> links) {
		// Dead path all of the outgoing links (nothing has been activated yet!)
		for (OLink link : links) {
			if (__log.isDebugEnabled())
				__log.debug("DPE on link " + link.name);
//			_linkFrame.resolve(link).pub.linkStatus(false);

			// Link_Ready
			LinkReady lnk_event = new LinkReady(link.getXpath(), link.getId(), link.name,
					sFrame.oscope.getXpath(), sFrame.scopeInstanceId,
					process_name, process_ID, link.source.getXpath(),
					link.target.getXpath());
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(lnk_event);

			// Link_Evaluated
			/**
			 * @haupt FIXME Exception bei DPE Ursache (?): Mehr als ein Aufruf
			 *        von i.next() in der Schleife:
			 *        _linkFrame.resolve(i.next()).pub Fix:
			 *        _linkFrame.resolve(olink).pub
			 * 
			 *        Apache Repository Checkout v1.1.1 sagt: // Dead path all
			 *        of the outgoing links (nothing has been activated yet!)
			 *        for (OLink link : links) { if (__log.isDebugEnabled())
			 *        __log.debug("DPE on link " + link.name);
			 *        _linkFrame.resolve(link).pub.linkStatus(false); }
			 */
			LinkEvaluated link_event = new LinkEvaluated(link.getXpath(), link.getId(),
					link.name, sFrame.oscope.getXpath(),
					sFrame.scopeInstanceId, process_name, process_ID,
					link.source.getXpath(), link.target.getXpath(), false,
					_linkFrame.resolve(link).pub);
			getBpelRuntimeContext().getBpelProcess().getEngine()
					.fireEvent(link_event);
			
			//@hahnml: Update the status in the LinkDAO
			LinkDAO dao = getBpelRuntimeContext().getProcessInstanceDao().getLink(link.getId());
			dao.setState(LinkStateEnum.FALSE);
		}
	}

	protected OConstants getConstants() {
		return _self.o.getOwner().constants;
	}

	/**
	 * Perform dead-path elimination on an activity that was
	 * <em>not started</em>.
	 * 
	 * @param activity
	 */
	protected void dpe(OActivity activity) {
		// we need the concrete instance of this activity for the event
		// Activity_Dead_Path
		ActivityInfo child;
		child = new ActivityInfo(genMonotonic(), activity,
				newChannel(TerminationChannel.class,
						"ACTIVITY DPE " + activity.toString()), newChannel(
						ParentScopeChannel.class,
						"ACTIVITY DPE " + activity.toString()));
		dpe(activity, child);
	}

	protected void dpe(OActivity activity, ActivityInfo act_info) {
		ActivityDeadPath evtDead = new ActivityDeadPath(_self.o.name, _self.o.getId(),
				activity.getXpath(), act_info.aId, sFrame.oscope.getXpath(),
				sFrame.scopeInstanceId, process_name, process_ID,
				activity.getArt(), (activity instanceof OScope));
		getBpelRuntimeContext().getBpelProcess().getEngine().fireEvent(evtDead);

		dpe(activity.sourceLinks);
		dpe(activity.outgoingLinks);
		// TODO: register listeners for target / incoming links
	}

	protected EvaluationContext getEvaluationContext() {
		return new ExprEvaluationContextImpl(_scopeFrame,
				getBpelRuntimeContext());
	}

	private int getLineNo() {
		if (_self.o.debugInfo != null && _self.o.debugInfo.startLine != -1) {
			return _self.o.debugInfo.startLine;
		}
		return -1;
	}

	//
	// Syntactic sugar for methods that used to be on BpelRuntimeContext..
	//

	Node fetchVariableData(VariableInstance variable, boolean forWriting)
			throws FaultException {
		return _scopeFrame.fetchVariableData(getBpelRuntimeContext(), variable,
				forWriting);
	}

	Node fetchVariableData(VariableInstance var, OMessageVarType.Part part,
			boolean forWriting) throws FaultException {
		return _scopeFrame.fetchVariableData(getBpelRuntimeContext(), var,
				part, forWriting);
	}

	Node initializeVariable(VariableInstance lvar, Node val)
			throws ExternalVariableModuleException {
		return _scopeFrame.initializeVariable(getBpelRuntimeContext(), lvar,
				val);
	}

	void commitChanges(VariableInstance lval, Node lvalue)
			throws ExternalVariableModuleException {
		_scopeFrame.commitChanges(getBpelRuntimeContext(), lval, lvalue);
	}

	Node getPartData(Element message, Part part) {
		return _scopeFrame.getPartData(message, part);
	}

	// added by Bo Ning
	// to create a snapshotDAO for every activity here, before the ActivityReady
	// Event of this activity begins!
	public SnapshotDAO storeSnapshot() {

		String xpath;
		ProcessInstanceDAO pi;
		xpath = this._self.o.getXpath();
		pi = getBpelRuntimeContext().getProcessInstanceDao();
		// create a snapshot for this processInstanceDAO now!
		SnapshotDAO snapshotdao;
		snapshotdao = pi.createSnapshot(xpath, pi.getInstanceId());

		// create a collection for the SnapshotPartnerlinks
		Collection<PartnerLinkDAO> partnerlinks;
		// create a collection for the SnapshotVariables
		Collection<XmlDataDAO> variables;
		SnapshotPartnerlinksDAO sp;
		SnapshotVariableDAO var;

		for (ScopeDAO scope : pi.getScopes()) {
			partnerlinks = scope.getPartnerLinks();
			Long scopeinstanceID;
			scopeinstanceID = scope.getScopeInstanceId();
			variables = scope.getVariables();
			// put the content of the SnapshotPartnerlinks and SnapshotVariables
			// into the SnapshotDAO
			for (PartnerLinkDAO partnerlink : partnerlinks) {
				sp = snapshotdao.createSnapshotPartnerlink(
						partnerlink.getPartnerLinkModelId(),
						partnerlink.getPartnerLinkName(),
						partnerlink.getMyRoleName(),
						partnerlink.getPartnerRoleName());
				sp.setMyEPR(partnerlink.getMyEPR2());
				sp.setMyRoleServiceName(partnerlink.getMyRoleServiceName());
				sp.setMySessionId(partnerlink.getMySessionId());
				sp.setPartnerEPR(partnerlink.getPartnerEPR2());
				sp.setPartnerSessionId(partnerlink.getPartnerSessionId());
				sp.setScopeInstanceId(scopeinstanceID);

			}
			for (XmlDataDAO variable : variables) {
				var = snapshotdao.createSnapshotVariable(variable.getName());
				var.set(variable.get());
				var.setScopeInstanceId(scopeinstanceID);
			}
		}
		return snapshotdao;
	}

	//
	// End syntactic sugar.
	//

	public static final class Key implements Serializable {
		private static final long serialVersionUID = 1L;

		final OActivity type;

		final long aid;

		public Key(OActivity type, long aid) {
			this.type = type;
			this.aid = aid;
		}

		// @hahnml: Provide the type to the outside
		public OActivity getType() {
			return this.type;
		}

		@Override
		public String toString() {
			return type + "::" + aid;
		}
	}

	public ScopeFrame get_scopeFrame() {
		return _scopeFrame;
	}

	// added by Bo Ning
	public LinkFrame get_linkFrame() {
		return _linkFrame;
	}
}
