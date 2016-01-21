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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.evar.ExternalVariableModuleException;
import org.apache.ode.bpel.evt.EventContext;
import org.apache.ode.bpel.evt.ScopeEvent;
import org.apache.ode.bpel.evt.VariableModificationEvent;
import org.apache.ode.bpel.extensions.events.ActivityExecuted;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.runtime.channels.FaultData;
import org.apache.ode.bpel.runtime.common.extension.ExtensionContext;
import org.apache.ode.utils.Namespaces;
import org.w3c.dom.Node;

/**
 * @author Tammo van Lessen (University of Stuttgart)
 */
public class ExtensionContextImpl implements ExtensionContext {
	private static final Log __log = LogFactory
			.getLog(ExtensionContextImpl.class);

	private BpelRuntimeContext _context;
	private ScopeFrame _scopeFrame;
	private ActivityInfo _activityInfo;
	
	//@hahnml
	private ACTIVITY _activity;

	private boolean hasCompleted = false;

	//@hahnml: Changed to ACTIVITY to get the whole stuff with one parameter
	public ExtensionContextImpl(ACTIVITY activity, BpelRuntimeContext context) {
		_activityInfo = activity._self;
		_context = context;
		_scopeFrame = activity._scopeFrame;
		_activity = activity;
	}
	
	//@sonntamo
	public ExtensionContextImpl(ActivityInfo activityInfo, ScopeFrame scopeFrame, BpelRuntimeContext context) {
		_activityInfo = activityInfo;
		_scopeFrame = scopeFrame;
		_context = context;
	}

	public Long getProcessId() {
		return _context.getPid();
	}

	public Map<String, OScope.Variable> getVisibleVariables()
			throws FaultException {
		Map<String, OScope.Variable> visVars = new HashMap<String, OScope.Variable>();

		OActivity current = _scopeFrame.oscope;
		while (current != null) {
			if (current instanceof OScope) {
				for (String varName : ((OScope) current).variables.keySet()) {
					if (!visVars.containsKey(varName)) {
						visVars.put(varName,
								((OScope) current).variables.get(varName));
					}
				}
			}
			current = current.getParent();
		}

		return visVars;
	}

	public String readMessageProperty(OScope.Variable variable,
			OProcess.OProperty property) throws FaultException {
		VariableInstance vi = _scopeFrame.resolve(variable);
		return _context.readProperty(vi, property);
	}

	public Node readVariable(OScope.Variable variable) throws FaultException {
		VariableInstance vi = _scopeFrame.resolve(variable);
		return _context.fetchVariableData(vi, _scopeFrame, true);
	}

	public void writeVariable(String variableName, Node value)
			throws FaultException, ExternalVariableModuleException {
		OScope.Variable var = getVisibleVariable(variableName);
		if (var == null) {
			throw new RuntimeException("Variable '" + variableName
					+ "' not visible.");
		}
		writeVariable(var, value);
	}

	public Node readVariable(String variableName) throws FaultException {
		OScope.Variable var = getVisibleVariable(variableName);
		if (var == null) {
			throw new RuntimeException("Variable '" + variableName
					+ "' not visible.");
		}

		return readVariable(var);
	}

	public void writeVariable(OScope.Variable variable, Node value)
			throws FaultException, ExternalVariableModuleException {
		VariableInstance vi = _scopeFrame.resolve(variable);
		_context.initializeVariable(vi, _scopeFrame, value);
		VariableModificationEvent vme = new VariableModificationEvent(
				variable.name, null, null, variable.declaringScope.getXpath(),
				variable.getXpath(), vi.scopeInstance);
		vme.setNewValue(value);
		sendEvent(vme);
	}

	public OScope.Variable getVisibleVariable(String varName) {
		return _scopeFrame.oscope.getVisibleVariable(varName);
	}

	public boolean isVariableVisible(String varName) {
		return _scopeFrame.oscope.getVisibleVariable(varName) != null;
	}

	public String getActivityName() {
		return _activityInfo.o.name;
	}

	public OActivity getOActivity() {
		return _activityInfo.o;
	}

	public void sendEvent(ScopeEvent event) {
		if (event.getLineNo() == -1 && _activityInfo.o.debugInfo != null) {
			event.setLineNo(_activityInfo.o.debugInfo.startLine);
		}
		_scopeFrame.fillEventInfo(event);
		
		//@hahnml: Populate BpelEventContext, to be used by Registered Event Listeners
		EventContext eventContext = new EventContextImpl(_scopeFrame.oscope,
				_scopeFrame.scopeInstanceId, _context);
		event.eventContext = eventContext;
		
		_context.sendEvent(event);
	}

	public void complete() {
		if (!hasCompleted) {
			
			_activityInfo.parent
					.completed(null, CompensationHandler.emptySet());
			hasCompleted = true;
		} else {
			if (__log.isWarnEnabled()) {
				__log.warn("Activity '" + _activityInfo.o.name
						+ "' has already been completed.");
			}
		}
	}

	public void completeWithFault(Throwable t) {
		if (!hasCompleted) {
			StringWriter sw = new StringWriter();
			t.printStackTrace(new PrintWriter(sw));
			FaultData fault = new FaultData(new QName(
					Namespaces.WSBPEL2_0_FINAL_EXEC,
					"subLanguageExecutionFault"), _activityInfo.o, sw
					.getBuffer().toString());
			_activityInfo.parent.completed(fault,
					CompensationHandler.emptySet());
			hasCompleted = true;
		} else {
			if (__log.isWarnEnabled()) {
				__log.warn("Activity '" + _activityInfo.o.name
						+ "' has already been completed.");
			}
		}
	}

	public void completeWithFault(FaultException ex) {
		if (!hasCompleted) {
			FaultData fault = new FaultData(ex.getQName(), _activityInfo.o,
					ex.getMessage());
			_activityInfo.parent.completed(fault,
					CompensationHandler.emptySet());
			hasCompleted = true;
		} else {
			if (__log.isWarnEnabled()) {
				__log.warn("Activity '" + _activityInfo.o.name
						+ "' has already been completed.");
			}
		}

	}
	
	//@hahnml
	public void complete(Set<CompensationHandler> compensations) {
		if (!hasCompleted) {
			_activityInfo.parent
					.completed(null, compensations);
			hasCompleted = true;
		} else {
			if (__log.isWarnEnabled()) {
				__log.warn("Activity '" + _activityInfo.o.name
						+ "' has already been completed.");
			}
		}
	}
	//@hahnml
	public void completeWithFault(FaultData fault, Set<CompensationHandler> compensations) {
		if (!hasCompleted) {
			_activityInfo.parent.completed(fault,
					compensations);
			hasCompleted = true;
		} else {
			if (__log.isWarnEnabled()) {
				__log.warn("Activity '" + _activityInfo.o.name
						+ "' has already been completed.");
			}
		}

	}

	public BpelRuntimeContext getRuntimeInstance() {
		return _context;
	}

	public URI getDUDir() {
		return _context.getBaseResourceURI();
	}

	public void printToConsole(String msg) {
		LogFactory.getLog("org.apache.ode.extension.Console").info(msg);
	}
	
	//@hahnml
	public ScopeFrame getScopeFrame() {
		return _scopeFrame;
	}
	public ActivityInfo getActivityInfo() {
		return _activityInfo;
	}
	public LinkFrame getLinkFrame() {
		return _activity._linkFrame;
	}
	public void doDPE(OActivity activity) {
		_activity.dpe(activity);
	}

	public void storeSnapshot() {
		_activity.storeSnapshot();
	}
}
