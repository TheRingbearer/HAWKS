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

import org.w3c.dom.Node;

public class VariableModificationEvent extends VariableEvent {
	private static final long serialVersionUID = 1L;

	private transient Node newValue;

	// @stmz
	private String act_xpath;
	private Long activityID;
	private String ScopeXPath;
	private String var_Xpath;
	private Long ScopeID;

	public VariableModificationEvent(String path, Long id, String scope_xpath,
			String var_xpath, Long scop_id) {
		super();
		act_xpath = path;
		activityID = id;
		ScopeXPath = scope_xpath;
		var_Xpath = var_xpath;
		ScopeID = scop_id;
	}

	public VariableModificationEvent(String varName, String path, Long id,
			String scope_xpath, String var_xpath, Long scop_id) {
		super(varName);
		act_xpath = path;
		activityID = id;
		ScopeXPath = scope_xpath;
		var_Xpath = var_xpath;
		ScopeID = scop_id;
	}

	public Node getNewValue() {
		return newValue;
	}

	public void setNewValue(Node newValue) {
		this.newValue = newValue;
	}

	public String getAct_xpath() {
		return act_xpath;
	}

	public Long getActivityID() {
		return activityID;
	}

	public String getScopeXPath() {
		return ScopeXPath;
	}

	public String getVar_Xpath() {
		return var_Xpath;
	}

	public Long getScopeID() {
		return ScopeID;
	}
}
