package org.apache.ode.mediator;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public interface Mediator {
	public Node mediateVariable(QName fromDataType, QName toDataType, Node data) throws MediationException;
	public Node mediateCorrelationSet(CSetMediationInfo from, CSetMediationInfo to, Node data) throws MediationException;
}
