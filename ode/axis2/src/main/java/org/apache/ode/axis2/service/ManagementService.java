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

package org.apache.ode.axis2.service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.transaction.TransactionManager;
import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPFault;
import org.apache.axiom.soap.SOAPFaultCode;
import org.apache.axiom.soap.SOAPFaultDetail;
import org.apache.axiom.soap.SOAPFaultReason;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.receivers.AbstractMessageReceiver;
import org.apache.axis2.util.MessageContextBuilder;
import org.apache.axis2.util.Utils;
import org.apache.axis2.util.XMLUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.axis2.OdeFault;
import org.apache.ode.axis2.hooks.ODEAxisService;
import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.BpelServerImpl;
import org.apache.ode.bpel.engine.FragmentManagementImpl;
import org.apache.ode.bpel.engine.MyRoleMessageExchangeImpl;
import org.apache.ode.bpel.engine.ProcessAndInstanceManagementImpl;
import org.apache.ode.bpel.extensions.handler.IncomingMessageHandler;
import org.apache.ode.bpel.fcapi.FragmentManagement;
import org.apache.ode.bpel.iapi.BpelServer;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MessageExchange.Status;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.bpel.iapi.ProcessStore;
import org.apache.ode.bpel.iapi.Scheduler.JobType;
import org.apache.ode.bpel.pmapi.InstanceManagement;
import org.apache.ode.bpel.pmapi.ProcessManagement;
import org.apache.ode.il.DynamicService;
import org.apache.ode.utils.GUID;
import org.apache.ode.utils.Namespaces;
import org.apache.ode.utils.fc.FCConstants;
import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Axis2 wrapper for process and instance management interfaces.
 */
public class ManagementService {

	private TransactionManager txManager;
	private static final Log __log = LogFactory.getLog(ManagementService.class);

	public static final QName PM_SERVICE_NAME = new QName(
			"http://www.apache.org/ode/pmapi", "ProcessManagementService");
	public static final String PM_PORT_NAME = "ProcessManagementPort";
	public static final String PM_AXIS2_NAME = "ProcessManagement";

	public static final QName IM_SERVICE_NAME = new QName(
			"http://www.apache.org/ode/pmapi", "InstanceManagementService");
	public static final String IM_PORT_NAME = "InstanceManagementPort";
	public static final String IM_AXIS2_NAME = "InstanceManagement";

	// AH:

	public static final QName FM_SERVICE_NAME = new QName(
			"http://www.apache.org/ode/fmapi/", "FragmentManagement");
	public static final String FM_PORT_NAME = "FragmentManagementHttpSoap11Endpoint";
	public static final String FM_AXIS2_NAME = "FragmentManagement";

	public static final String FC_AXIS2_NAME = "FragmentComposition";

	private FragmentManagement _fragmentMgmt;
	// AH: end

	private ProcessManagement _processMgmt;
	private InstanceManagement _instanceMgmt;

	// AH:
	private Definition fcServiceDefinition;

	public ManagementService(TransactionManager txManager) {
		this.txManager = txManager;
	}

	// AH: end

	public void enableService(AxisConfiguration axisConfig, BpelServer server,
			ProcessStore _store, String rootpath) {
		ProcessAndInstanceManagementImpl pm = new ProcessAndInstanceManagementImpl(
				server, _store);
		_processMgmt = pm;
		_instanceMgmt = pm;

		// AH:
		_fragmentMgmt = new FragmentManagementImpl(server, txManager);
		// AH: End

		Definition def;
		try {
			WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
			wsdlReader.setFeature("javax.wsdl.verbose", false);

			File wsdlFile = new File(rootpath + "/pmapi.wsdl");
			def = wsdlReader.readWSDL(wsdlFile.toURI().toString());
			AxisService processService = ODEAxisService.createService(
					axisConfig, PM_SERVICE_NAME, PM_PORT_NAME, PM_AXIS2_NAME,
					def, new DynamicMessageReceiver<ProcessManagement>(
							_processMgmt));

			/*
			 * XXX: Reparsing the WSDL is a workaround for WSCOMMONS-537 (see
			 * also ODE-853). When WSCOMMONS-537 is fixed we can safely remove
			 * the following line.
			 */
			def = wsdlReader.readWSDL(wsdlFile.toURI().toString());
			/* end XXX */

			AxisService instanceService = ODEAxisService.createService(
					axisConfig, IM_SERVICE_NAME, IM_PORT_NAME, IM_AXIS2_NAME,
					def, new DynamicMessageReceiver<InstanceManagement>(
							_instanceMgmt));
			
			axisConfig.addService(processService);
			axisConfig.addService(instanceService);

			// AH:
			File fcmwsdlFile = new File(rootpath + "/fcmapi.wsdl");
			Definition fcdef = wsdlReader.readWSDL(fcmwsdlFile.toURI()
					.toString());

			AxisService fmService = ODEAxisService.createService(axisConfig,
					FM_SERVICE_NAME, FM_PORT_NAME, FM_AXIS2_NAME, fcdef,
					new DynamicMessageReceiver<FragmentManagement>(
							_fragmentMgmt));

			File fcWsdlFile = new File(rootpath + "/fcapi.wsdl");
			fcServiceDefinition = wsdlReader.readWSDL(fcWsdlFile.toURI()
					.toString());

			/*
			 * AxisService fragCompService = ODEAxisService.createService(
			 * axisConfig, FC_SERVICE_NAME, FC_PORT_NAME, FC_AXIS2_NAME, fcDef,
			 * new DynamicMessageReceiver<FragmentComposition>( _fragmentComp));
			 */
			AxisService fragCompService = ODEAxisService.createService(
					axisConfig, FCConstants.FC_SERVICE_NAME,
					FCConstants.FC_PORT_NAME, FC_AXIS2_NAME,
					fcServiceDefinition,
					new FragmentCompositionAPIMessageReceiver(server,
							fcServiceDefinition));

			axisConfig.addService(fragCompService);
			axisConfig.addService(fmService);
			// AH: end

			// @stmz
			IncomingMessageHandler incMess = IncomingMessageHandler
					.getInstance();
		} catch (WSDLException e) {
			__log.error("Couldn't start-up management services!", e);
		} catch (IOException e) {
			__log.error("Couldn't start-up management services!", e);
		}
	}

	public ProcessManagement getProcessMgmt() {
		return _processMgmt;
	}

	public InstanceManagement getInstanceMgmt() {
		return _instanceMgmt;
	}

	// AH:
	public Definition getFcServiceDefinition() {
		return fcServiceDefinition;
	}

	class FragmentCompositionAPIMessageReceiver extends AbstractMessageReceiver {
		private BpelServerImpl server;
		private Definition def;
		// private SoapMessageConverter converter;
		private final Long timeout = 60000L;

		public FragmentCompositionAPIMessageReceiver(BpelServer server,
				Definition def) {
			this.server = (BpelServerImpl) server;
			this.def = def;
			// converter = new SoapMessageConverter(def, FC_SERVICE_NAME,
			// FC_PORT_NAME);
		}

		@Override
		protected void invokeBusinessLogic(MessageContext messageContext)
				throws AxisFault {

			// OMElement response;
			Future responseFuture = null;
			String operation = messageContext.getAxisOperation().getName()
					.getLocalPart();
			OMElement payload = messageContext.getEnvelope().getBody()
					.getFirstElement();
			boolean success = true;
			// ODEService::onAxisMessageExchange(...)
			MyRoleMessageExchange odeMex = null;
			try {

				// AH: route FragmentComposition message to the right process
				QName instanceField = FCConstants.FC_INSTANCE_ID_FIELD;
				Long instanceId = null;
				OMElement element = null;
				if (payload != null) {
					element = (OMElement) payload.getChildrenWithName(
							instanceField).next();
				}

				if (element != null) {
					try {
						instanceId = Long.parseLong(element.getText());
					} catch (NumberFormatException e) {
						__log.error("Routed: svcQname "
								+ FCConstants.FC_SERVICE_NAME
								+ "InstanceId couldn't be parsed!");
					}
				}

				// AH: end

				txManager.begin();

				String messageId = new GUID().toString();
				odeMex = ((BpelEngineImpl) (server.getEngine()))
						.createFragmentMessageExchange("" + messageId,
								FCConstants.FC_SERVICE_NAME, operation,
								instanceId);

				// hier ist odeMex relativ leer operation ect muss gesetzt
				// werden
				// if (odeMex.getOperation() != null) {
				// Preparing message to send to ODE
				QName portName = FCConstants.FC_PORT_TYPE_NAME;
				PortType port = def.getPortType(portName);
				Operation wsdlOperation = port.getOperation(operation, null,
						null);
				((MyRoleMessageExchangeImpl) odeMex).setPortOperation(port,
						wsdlOperation);

				Message odeRequest = null;
				if (payload != null) {
					odeRequest = odeMex.createMessage(payload.getQName());
					odeRequest.setMessage(XMLUtils.toDOM(payload));
				}
				/*
				 * _converter.parseSoapRequest(odeRequest, messageContext
				 * .getEnvelope(), odeMex.getOperation());
				 * readHeader(messageContext, odeMex);
				 */
				responseFuture = ((MyRoleMessageExchangeImpl) odeMex).invoke(
						odeRequest, JobType.INVOKE_FC, instanceId);
				// }
				__log.debug("Commiting ODE MEX " + odeMex);
				try {
					if (__log.isDebugEnabled())
						__log.debug("Commiting transaction.");
					txManager.commit();
				} catch (Exception e) {
					__log.error("Commit failed", e);
					success = false;
				}
			} catch (Exception e) {
				if (e.getMessage() != null
						&& e.getMessage().contains("NoSuchInstance")) {
					MessageContext outMsgContext = MessageContextBuilder
							.createOutMessageContext(messageContext);
					if (outMsgContext != null) {
						SOAPFactory soapFactory = getSOAPFactory(messageContext);
						SOAPEnvelope envelope = soapFactory
								.getDefaultEnvelope();
						outMsgContext.setEnvelope(envelope);
						QName type = new QName(FCConstants.FC_TARGET_NAMESPACE,
								"InstanceNotFoundException");

						Document xmldoc = new DocumentImpl();
						Element response = xmldoc.createElement("message");
						response.setTextContent(e.getMessage());

						envelope.getBody().addFault(
								buildFault(type, response,
										getSOAPFactory(messageContext)));
						AxisEngine.send(outMsgContext);
					}
				} else {
					__log.error("Exception occured while invoking ODE", e);
					String message = e.getMessage();
					if (message == null) {
						message = "An exception occured while invoking ODE.";
					}
					throw new OdeFault(message, e);
				}

				success = false;

			} finally {
				if (!success) {
					if (odeMex != null)
						odeMex.release(success);
					try {
						txManager.rollback();
					} catch (Exception e) {
						throw new OdeFault("Rollback failed", e);
					}
				}
			}

			// waiting for response
			if (odeMex != null && odeMex.getOperation().getOutput() != null) {
				// Waits for the response to arrive
				try {
					responseFuture.get(timeout, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
					String errorMsg = "Timeout or execution error when waiting for response to MEX "
							+ odeMex + " " + e.toString();
					__log.error(errorMsg, e);
					throw new OdeFault(errorMsg);
				}
				MessageContext outMsgContext = MessageContextBuilder
						.createOutMessageContext(messageContext);
				if (outMsgContext != null) {
					SOAPFactory soapFactory = getSOAPFactory(messageContext);
					SOAPEnvelope envelope = soapFactory.getDefaultEnvelope();
					outMsgContext.setEnvelope(envelope);

					// Hopefully we have a response
					__log.debug("Handling response for MEX " + odeMex);
					boolean commit = false;
					try {
						if (__log.isDebugEnabled())
							__log.debug("Starting transaction.");
						txManager.begin();
					} catch (Exception ex) {
						throw new OdeFault("Error starting transaction!", ex);
					}
					try {
						// Refreshing the message exchange
						odeMex = (MyRoleMessageExchange) server.getEngine()
								.getMessageExchange(
										odeMex.getMessageExchangeId());

						if (odeMex.getStatus().equals(Status.RESPONSE)) {
							envelope.getBody().addChild(
									XMLUtils.toOM(odeMex.getResponse()
											.getMessage()));
						} else if (odeMex.getStatus().equals(Status.FAULT)) {
							Message fault = odeMex.getFaultResponse();
							envelope.getBody().addFault(
									buildFault(fault.getType(),
											fault.getMessage(),
											getSOAPFactory(messageContext)));
						}
						/*
						 * converter.createSoapResponse(outMsgContext,
						 * odeMex.getResponse(), odeMex .getOperation());
						 */
						AxisEngine.send(outMsgContext);
						commit = true;
					} catch (AxisFault af) {
						__log.warn("MEX produced a fault " + odeMex, af);
						commit = true;
						throw af;
					} catch (Exception e) {
						__log.error("Error processing response for MEX "
								+ odeMex, e);
						throw new OdeFault(
								"An exception occured when invoking ODE.", e);
					} finally {
						odeMex.release(commit);
						if (commit) {
							try {
								if (__log.isDebugEnabled())
									__log.debug("Comitting transaction.");
								txManager.commit();
							} catch (Exception e) {
								throw new OdeFault("Commit failed!", e);
							}
						} else {
							try {
								txManager.rollback();
							} catch (Exception ex) {
								throw new OdeFault("Rollback failed!", ex);
							}
						}
					}
				}
				if (!success) {
					throw new OdeFault(
							"Message was either unroutable or timed out!");
				}
			} else {
				// One ways cleanup
				if (odeMex != null)
					odeMex.release(true);
			}

			/*
			 * response = service.invoke(messageContext.getAxisOperation()
			 * .getName().getLocalPart(), );
			 */
			/*
			 * } catch (Exception e) { // Building a nicely formatted fault
			 * SOAPFactory soapFactory = getSOAPFactory(messageContext);
			 * envelope.getBody().addFault(toSoapFault(e, soapFactory)); }
			 */
			// AxisEngine.send(outMsgContext);
		}

		private SOAPFault buildFault(QName type, Element faultMessage,
				SOAPFactory soapFactory) {
			SOAPFault fault = soapFactory.createSOAPFault();
			SOAPFaultCode code = soapFactory.createSOAPFaultCode(fault);
			code.setText(new QName(Namespaces.SOAP_ENV_NS, "Server"));

			try {
				String faultString = faultMessage.getTextContent();
				SOAPFaultDetail soapDetail = soapFactory
						.createSOAPFaultDetail(fault);
				OMElement detail = soapFactory.createOMElement(type);
				detail.setText(faultString);
				soapDetail.addDetailEntry(detail);

				SOAPFaultReason reason = soapFactory
						.createSOAPFaultReason(fault);
				reason.setText(faultString);

			} catch (Exception e) {
				__log.error(e);
			}
			return fault;

		}
	}

	// AH: end

	class DynamicMessageReceiver<T> extends AbstractMessageReceiver {
		T _service;

		public DynamicMessageReceiver(T service) {
			_service = service;
		}

		public void invokeBusinessLogic(MessageContext messageContext)
				throws AxisFault {
			DynamicService<T> service = new DynamicService<T>(_service);
			MessageContext outMsgContext = Utils
					.createOutMessageContext(messageContext);
			outMsgContext.getOperationContext()
					.addMessageContext(outMsgContext);
			SOAPFactory soapFactory = getSOAPFactory(messageContext);
			SOAPEnvelope envelope = soapFactory.getDefaultEnvelope();
			outMsgContext.setEnvelope(envelope);

			OMElement response;
			try {
				response = service.invoke(messageContext.getAxisOperation()
						.getName().getLocalPart(), messageContext.getEnvelope()
						.getBody().getFirstElement());
				if (response != null) {
					envelope.getBody().addChild(response);
				}
			} catch (Exception e) {
				// Building a nicely formatted fault
				envelope.getBody().addFault(toSoapFault(e, soapFactory));
			}
			AxisEngine.send(outMsgContext);
		}

		private SOAPFault toSoapFault(Exception e, SOAPFactory soapFactory) {
			SOAPFault fault = soapFactory.createSOAPFault();
			SOAPFaultCode code = soapFactory.createSOAPFaultCode(fault);
			code.setText(new QName(Namespaces.SOAP_ENV_NS, "Server"));
			SOAPFaultReason reason = soapFactory.createSOAPFaultReason(fault);
			reason.setText(e.toString());

			OMElement detail = soapFactory.createOMElement(new QName(
					Namespaces.ODE_PMAPI_NS, e.getClass().getSimpleName()));
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			detail.setText(stack.toString());
			SOAPFaultDetail soapDetail = soapFactory
					.createSOAPFaultDetail(fault);
			soapDetail.addDetailEntry(detail);
			return fault;
		}
	}

}
