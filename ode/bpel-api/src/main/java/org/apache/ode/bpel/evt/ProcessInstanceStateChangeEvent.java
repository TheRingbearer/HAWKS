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

/**
 * Event indicating the start (post creation) of a new process instance.
 */
public class ProcessInstanceStateChangeEvent extends ProcessInstanceEvent {
	private static final long serialVersionUID = 5145501369806670539L;
	private short _oldState;
	private short _newState;

	private String details;

	// stmz
	private String state = null;

	public ProcessInstanceStateChangeEvent() {
		super();
	}

	public short getOldState() {
		return _oldState;
	}

	public void setOldState(short state) {
		_oldState = state;
	}

	public short getNewState() {
		return _newState;
	}

	public void setNewState(short state) {
		_newState = state;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}
}
