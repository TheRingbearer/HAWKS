package org.apache.ode.bpel.engine;

import java.util.Map;

import javax.wsdl.Fault;
import javax.wsdl.Operation;
import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.util.XMLUtils;
import org.apache.ode.bpel.engine.fc.FragmentCompositionAPI;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.il.DynamicService;
import org.apache.ode.utils.fc.FCConstants;
import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentCompositionResponse {
	private Operation operation;
	private MyRoleMessageExchange mex;
	private DynamicService<FragmentCompositionAPI> service;
	private String namespace;
	protected Runnable onSuccess;

	public FragmentCompositionResponse(Operation operation, String namespace,
			MyRoleMessageExchange mex,
			DynamicService<FragmentCompositionAPI> service) {
		this.operation = operation;
		this.mex = mex;
		this.service = service;
		this.namespace = namespace;
		onSuccess = null;
	}

	public void returnValue(Object value) {
		if (operation != null) {
			javax.wsdl.Message wsdlOutMess = operation.getOutput().getMessage();
			QName outMessageName = wsdlOutMess.getQName();
			Message odeResponse = mex.createMessage(outMessageName);

			OMElement result = service.convertResponse(operation.getName(),
					namespace, value);
			if (result != null) {
				try {
					odeResponse.setMessage(XMLUtils.toDOM(result));
				} catch (Exception e) {
					throwException(e);
				}
			}

			if (onSuccess != null) {
				onSuccess.run();
			}
			((MyRoleMessageExchangeImpl) mex).setResponse(odeResponse);
			((MyRoleMessageExchangeImpl) mex).responseReceived();

		}
	}

	public void throwException(Exception e) {
		if (operation != null) {
			Map faults = operation.getFaults();
			Fault fault = null;
			String message;
			if (e.getCause() != null) {
				fault = (Fault) faults.get(e.getCause().getClass()
						.getSimpleName());
				message = e.getCause().getMessage();
			} else {
				fault = (Fault) faults.get(e.getClass().getSimpleName());
				message = e.getMessage();
			}
			if (fault != null) {

				MyRoleMessageExchangeImpl mexImpl = (MyRoleMessageExchangeImpl) mex;
				QName faultMessageName = new QName(
						FCConstants.FC_TARGET_NAMESPACE, fault.getName());
				Message odeResponse = mex.createMessage(faultMessageName);

				Document xmldoc = new DocumentImpl();
				Element response = xmldoc.createElement("message");
				response.setTextContent(message);
				odeResponse.setMessage(response);

				mexImpl.setFault(faultMessageName, odeResponse);
				((MyRoleMessageExchangeImpl) mex).responseReceived();

			}
		}
	}

	public void setOnSuccessAction(Runnable onSuccess) {
		this.onSuccess = onSuccess;
	}
}
