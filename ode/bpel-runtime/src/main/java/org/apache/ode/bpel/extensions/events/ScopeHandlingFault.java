package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OVarType;
import org.apache.ode.bpel.runtime.channels.LinkStatusChannel;
import org.w3c.dom.Element;

public class ScopeHandlingFault extends BpelScopeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private LinkStatusChannel chan;
	private QName fault_name;
	private Element faultMSG;
	private QName messageType;
	private QName elementType;
	private Boolean ignore;
	private String link_name;

	public ScopeHandlingFault(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop, Long selfID, LinkStatusChannel signal,
			QName fault, Element msg, QName msgType, QName elemType, Boolean ign) {
		super(act_name, oBase_id, act_loc, id, scope_loc, scope_id, proc_name, proc_id,
				art, scop, selfID);
		// TODO Auto-generated constructor stub
		chan = signal;
		fault_name = fault;
		faultMSG = msg;
		messageType = msgType;
		elementType = elemType;
		ignore = ign;
	}

	public LinkStatusChannel getChan() {
		return chan;
	}

	public QName getFault_name() {
		return fault_name;
	}

	public Element getFaultMSG() {
		return faultMSG;
	}

	public QName getMessageType() {
		return messageType;
	}

	public QName getElementType() {
		return elementType;
	}

	public Boolean getIgnore() {
		return ignore;
	}

	public String getLink_name() {
		return link_name;
	}

	public void setLink_name(String link_name) {
		this.link_name = link_name;
	}

}
