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

package org.apache.ode.bpel.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.wsdl.Operation;
import javax.wsdl.PortType;

import org.apache.ode.bpel.dao.MessageExchangeDAO;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.iapi.BpelEngineException;
import org.apache.ode.bpel.iapi.EndpointReference;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.bpel.iapi.ProcessState;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A reliable MEP that delegates messages to a list of subscribers
 * 
 * @author $author$
 * @version $Revision$
 */
public class BrokeredMyRoleMessageExchangeImpl extends
		MyRoleMessageExchangeImpl {
	private List<MyRoleMessageExchange> subscribers;
	private MyRoleMessageExchange template;

	/**
	 * Creates a new BrokeredReliableMyRoleMessageExchangeImpl object.
	 * 
	 * @param process
	 * @param subscribers
	 * @param mexId
	 * @param oplink
	 * @param template
	 */
	public BrokeredMyRoleMessageExchangeImpl(BpelProcess process,
			BpelEngineImpl engine, List<MyRoleMessageExchange> subscribers,
			MessageExchangeDAO mexDao, MyRoleMessageExchange template) {
		super(process, engine, mexDao);
		this.subscribers = subscribers;
		this.template = template;
	}

	/**
	 * Propagate the invoke reliable call to each subscriber
	 */
	public Future invoke(Message request) {
		Future myFuture = null;

		// @sonntamo: implementation of concurrent workflow evolution. The
		// target model version is selected based on meta data the client has
		// sent to the engine in a message header.
		// get <metaData>-header
		Node metaDataHeader = null;
		for (String key : request.getHeaderParts().keySet()) {
			if (key.equals("metaData")) {
				metaDataHeader = request.getHeaderParts().get("metaData");
				break;
			}
		}
		boolean takeDefault = true;
		if (metaDataHeader != null) {

			// take process model version as indicated by the meta data
			for (MyRoleMessageExchange subscriber : subscribers) {
				BpelProcess process = ((MyRoleMessageExchangeImpl) subscriber)._process;

				// take meta data as it is stored for the given process
				Map<String, String> metaData = process._pconf.getMetaData();

				int metaDataSize = metaDataHeader.getChildNodes() != null ? metaDataHeader
						.getChildNodes().getLength() : 0;
				if (metaData.size() == metaDataSize) {
					// we store all meta data we find in the message header in this
					// map
					HashMap<String, String> mdMatch = new HashMap<String, String>();
					
					// now walk through all meta data properties given in the
					// message header and compare to the process
					NodeList childNodes = metaDataHeader.getChildNodes();
					for (int i = 0; i < childNodes.getLength(); i++) {
						Node node = childNodes.item(i);
						Node nameAttr = node.getAttributes().getNamedItemNS(
								"http://simtech.uni-stuttgart.de", "name");
						String name = nameAttr.getNodeValue();
						Node valueAttr = node.getAttributes().getNamedItemNS(
								"http://simtech.uni-stuttgart.de", "value");
						String value = valueAttr.getNodeValue();
						String val = metaData.get(name);

						// if the values match, we store the property in our
						// target
						// map
						if (val != null && val.equals(value)) {
							mdMatch.put(name, value);
						}
					}

					// if the maps match in size,
					// all properties are equal and hence
					// we have found our process to invoke
					if (mdMatch.size() == metaData.size()) {

						// meta data are matching, invoke process
						if (process.getConf().getState() == ProcessState.ACTIVE) {
							Future theirFuture = subscriber.invoke(request);
							if (myFuture == null) {
								myFuture = theirFuture;
							}

							takeDefault = false;
							break;
						}
					}
				}
			}
		}

		if (takeDefault) {
			// TODO: what is the default process??? with smallest version
			// number???
		}
		return myFuture;
	}

	/**
	 * Use the EPR of one of the subscribers as my EPR
	 * 
	 * @return type
	 * 
	 * @throws BpelEngineException
	 *             BpelEngineException
	 */
	@Override
	public EndpointReference getEndpointReference() throws BpelEngineException {
		return template.getEndpointReference();
	}

	/**
	 * Use the response from one of the subscribers as my response
	 * 
	 * @return type
	 */
	@Override
	public Message getResponse() {
		return template.getResponse();
	}

	@Override
	public Status getStatus() {
		return template.getStatus();
	}

	@Override
	public CorrelationStatus getCorrelationStatus() {
		return template.getCorrelationStatus();
	}

	@Override
	public int getSubscriberCount() {
		return subscribers != null ? subscribers.size() : 0;
	}

	@Override
	public void setSubscriberCount(int subscriberCount) {
		for (MyRoleMessageExchange subscriber : subscribers) {
			((MyRoleMessageExchangeImpl) subscriber)
					.setSubscriberCount(subscriberCount);
		}
	}

	@Override
	public PortType getPortType() {
		return template.getPortType();
	}

	@Override
	public Operation getOperation() {
		return template.getOperation();
	}
}
