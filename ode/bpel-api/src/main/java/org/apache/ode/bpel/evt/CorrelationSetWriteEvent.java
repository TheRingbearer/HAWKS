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
package org.apache.ode.bpel.evt;

import org.apache.ode.bpel.common.CorrelationKey;

/**
 * Correlation was set event.
 * 
 */
public class CorrelationSetWriteEvent extends CorrelationSetEvent {
	private static final long serialVersionUID = 1L;
	private CorrelationKey _key;

	// @stmz
	private String XPath;
	private Boolean outside;

	// @hahnml
	private String act_XPath;

	public Boolean getOutside() {
		return outside;
	}

	public void setOutside(Boolean outside) {
		this.outside = outside;
	}

	public CorrelationSetWriteEvent(String csetName, CorrelationKey key,
			Boolean out, String act_XPath) {
		super(csetName);
		_key = key;
		outside = out;
		this.act_XPath = act_XPath;
	}

	/**
	 * Correlation key.
	 * 
	 * @return Returns the key.
	 */
	public CorrelationKey getKey() {
		return _key;
	}

	public void setKey(CorrelationKey key) {
		_key = key;
	}

	public String getXPath() {
		return XPath;
	}

	public void setXPath(String path) {
		XPath = path;
	}

	public void setAct_XPath(String act_XPath) {
		this.act_XPath = act_XPath;
	}

	public String getAct_XPath() {
		return act_XPath;
	}
}
