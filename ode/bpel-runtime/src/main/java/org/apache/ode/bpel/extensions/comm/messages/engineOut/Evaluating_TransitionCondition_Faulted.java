package org.apache.ode.bpel.extensions.comm.messages.engineOut;

import javax.xml.namespace.QName;

public class Evaluating_TransitionCondition_Faulted extends
		ActivityEventMessage {

	private static final long serialVersionUID = 63461L;

	private QName faultName;

	private QName elementType;

	private QName messageType;

	private String faultMsg;

	private String linkXPath;

	public Evaluating_TransitionCondition_Faulted() {
		super();
	}

	public QName getFaultName() {
		return faultName;
	}

	public QName getElementType() {
		return elementType;
	}

	public QName getMessageType() {
		return messageType;
	}

	public String getFaultMsg() {
		return faultMsg;
	}

	public String getLinkXPath() {
		return linkXPath;
	}

	public void setFaultName(QName faultName) {
		this.faultName = faultName;
	}

	public void setElementType(QName elementType) {
		this.elementType = elementType;
	}

	public void setMessageType(QName messageType) {
		this.messageType = messageType;
	}

	public void setFaultMsg(String faultMsg) {
		this.faultMsg = faultMsg;
	}

	public void setLinkXPath(String linkXPath) {
		this.linkXPath = linkXPath;
	}

}
