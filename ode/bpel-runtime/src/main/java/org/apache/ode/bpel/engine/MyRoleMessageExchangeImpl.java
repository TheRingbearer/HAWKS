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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.dao.MessageExchangeDAO;
import org.apache.ode.bpel.engine.replayer.Replayer;
import org.apache.ode.bpel.extensions.GenericController;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MessageExchange;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.bpel.iapi.Scheduler;
import org.apache.ode.bpel.iapi.ProcessConf.CLEANUP_CATEGORY;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.bpel.iapi.Scheduler.JobType;
import org.apache.ode.bpel.intercept.AbortMessageExchangeException;
import org.apache.ode.bpel.intercept.FaultMessageExchangeException;
import org.apache.ode.bpel.intercept.InterceptorInvoker;
import org.apache.ode.bpel.intercept.MessageExchangeInterceptor;
import org.apache.ode.bpel.intercept.MessageExchangeInterceptor.InterceptorContext;
import org.apache.ode.scheduler.simple.ZJobList;
import org.apache.ode.utils.ZZBool;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class MyRoleMessageExchangeImpl extends MessageExchangeImpl implements
		MyRoleMessageExchange {

	private static final Log __log = LogFactory
			.getLog(MyRoleMessageExchangeImpl.class);

	protected BpelProcess _process;

	protected static Map<String, ResponseCallback> _waitingCallbacks = new ConcurrentHashMap<String, ResponseCallback>();

	public MyRoleMessageExchangeImpl(BpelProcess process,
			BpelEngineImpl engine, MessageExchangeDAO mexdao) {
		super(engine, mexdao);
		_process = process;
	}

	public CorrelationStatus getCorrelationStatus() {
		return CorrelationStatus.valueOf(getDAO().getCorrelationStatus());
	}

	public void setCorrelationStatus(CorrelationStatus status) {
		getDAO().setCorrelationStatus(status.toString());
	}

	/**
	 * Process the message-exchange interceptors.
	 * 
	 * @param mex
	 *            message exchange
	 * @return <code>true</code> if execution should continue,
	 *         <code>false</code> otherwise
	 */
	private boolean processInterceptors(MyRoleMessageExchangeImpl mex,
			InterceptorInvoker invoker) {
		InterceptorContextImpl ictx = new InterceptorContextImpl(
				_engine._contexts.dao.getConnection(), mex._dao.getProcess(),
				null, _engine, _process);

		for (MessageExchangeInterceptor i : _engine.getGlobalInterceptors())
			if (!processInterceptor(i, mex, ictx, invoker))
				return false;

		return true;
	}
	
	// AH:
	public void setPortOperation(PortType port, Operation operation) {
		this._portType = port;
		this._operation = operation;
	}

	// EH: end

	boolean processInterceptor(MessageExchangeInterceptor i,
			MyRoleMessageExchangeImpl mex, InterceptorContext ictx,
			InterceptorInvoker invoker) {
		__log.debug(invoker + "--> interceptor " + i);
		try {
			invoker.invoke(i, mex, ictx);
		} catch (FaultMessageExchangeException fme) {
			__log.debug("interceptor " + i + " caused invoke on " + this
					+ " to be aborted with FAULT " + fme.getFaultName());
			mex.setFault(fme.getFaultName(), fme.getFaultData());
			return false;
		} catch (AbortMessageExchangeException ame) {
			__log.debug("interceptor " + i + " cause invoke on " + this
					+ " to be aborted with FAILURE: " + ame.getMessage());
			mex.setFailure(
					MessageExchange.FailureType.ABORTED,
					__msgs.msgInterceptorAborted(mex.getMessageExchangeId(),
							i.toString(), ame.getMessage()), null);
			return false;
		}
		return true;
	}

	// AH:

	public Future<String> invoke(Message request) {
		return invoke(request, JobType.INVOKE_INTERNAL, null);
	}

	@SuppressWarnings("unchecked")
	public Future<String> invoke(Message request, JobType type, Long instanceId) {

		if (request == null) {
			String errmsg = "Must pass non-null message to invoke()!";
			__log.fatal(errmsg);
			throw new NullPointerException(errmsg);
		}

		_dao.setRequest(((MessageImpl) request)._dao);
		_dao.setStatus(MessageExchange.Status.REQUEST.toString());

		if (!processInterceptors(this, InterceptorInvoker.__onBpelServerInvoked))
			return null;

		BpelProcess target = _process;

		if (__log.isDebugEnabled())
			__log.debug("invoke() EPR= " + _epr + " ==> " + target);

		if (target == null) {
			if (__log.isWarnEnabled())
				__log.warn(__msgs.msgUnknownEPR("" + _epr));

			setCorrelationStatus(MyRoleMessageExchange.CorrelationStatus.UKNOWN_ENDPOINT);
			setFailure(MessageExchange.FailureType.UNKNOWN_ENDPOINT, null, null);
			return null;
		} else {
			// @hahnml: CHECK Clear the jobList history of the last invoke!?
			ZJobList.getInstance().clearJobListHistory();

			// Schedule a new job for invocation
			JobDetails we = new JobDetails();
			we.setType(type);
			we.setInMem(target.isInMemory());
			we.setProcessId(target.getPID());
			we.setMexId(getDAO().getMessageExchangeId());
			if (instanceId != null) {
				we.setInstanceId(instanceId);
			}

			if (getOperation().getOutput() != null) {
				ResponseCallback callback = new ResponseCallback();
				_waitingCallbacks.put(getClientId(), callback);
			}

			setStatus(Status.ASYNC);
			Replayer replayer = Replayer.replayer.get();
			if (replayer == null) {
				if (target.isInMemory())
					_engine._contexts.scheduler.scheduleVolatileJob(true, we);
				else
					_engine._contexts.scheduler.schedulePersistedJob(we, null);
			} else {
				replayer.scheduler.schedulePersistedJob(we, null);
			}
			return new ResponseFuture(getClientId());
		}
	}
	// AH: end

	public void complete() {
	}

	/**
	 * Gets the BPEL process associated with this message exchange.
	 * 
	 * @return the BPEL process of this message exchange.
	 * 
	 * @author sonntamo
	 */
	public BpelProcess getBpelProcess() {
		return _process;
	}
	
	public QName getServiceName() {
		return getDAO().getCallee();
	}

	public void setClientId(String clientKey) {
		getDAO().setCorrelationId(clientKey);
	}

	public String getClientId() {
		return getDAO().getCorrelationId();
	}

	public String toString() {
		try {
			return "{MyRoleMex#" + getMessageExchangeId() + " [Client "
					+ getClientId() + "] calling " + getServiceName() + "."
					+ getOperationName() + "(...)}";
		} catch (Throwable t) {
			return "{MyRoleMex#???}";
		}
	}

	public boolean isAsynchronous() {
		return true;
	}

	public void release(boolean instanceSucceeded) {
		if (__log.isDebugEnabled())
			__log.debug("Releasing mex " + getMessageExchangeId());
		if (_process != null) {
			_dao.release(_process.isCleanupCategoryEnabled(instanceSucceeded,
					CLEANUP_CATEGORY.MESSAGES));
		}
		_dao = null;
	}

	/**
	 * Return a deep clone of the given message
	 * 
	 * @param message
	 * @return
	 */
	protected Message cloneMessage(Message message) {
		Message clone = createMessage(message.getType());
		clone.setMessage((Element) message.getMessage().cloneNode(true));
		Map<String, Node> headerParts = message.getHeaderParts();
		for (String partName : headerParts.keySet()) {
			clone.setHeaderPart(partName, (Element) headerParts.get(partName)
					.cloneNode(true));
		}
		Map<String, Node> parts = message.getHeaderParts();
		for (String partName : parts.keySet()) {
			clone.setHeaderPart(partName, (Element) parts.get(partName)
					.cloneNode(true));
		}
		return clone;
	}

	// AH: added string
	@SuppressWarnings("unchecked")
	static class ResponseFuture implements Future<String> {
		// AH: end
		private String _clientId;
		private boolean _done = false;

		public ResponseFuture(String clientId) {
			_clientId = clientId;
		}

		public boolean cancel(boolean mayInterruptIfRunning) {
			throw new UnsupportedOperationException();
		}

		// AH: changed Object to String
		public String get() throws InterruptedException, ExecutionException {
			try {
				return get(0, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				// If it's thrown it's definitely a bug
				throw new ExecutionException(e);
			}
		}

		// AH: Changed object to string
		public String get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException,
				TimeoutException {
			ResponseCallback callback = _waitingCallbacks.get(_clientId);
			if (callback != null) {
				//callback.waitResponse(timeout);
				//krawczls: changed timeout
				callback.waitResponse(10 * 60 * 1000);
				_done = true;
				if (callback._timedout)
					throw new TimeoutException("Message exchange " + this
							+ " timed out(" + timeout
							+ " ms) when waiting for a response!");
			}
			// AH: changed from null
			return callback.getMexId();
			// AH: end
		}

		public boolean isCancelled() {
			return false;
		}

		public boolean isDone() {
			return _done;
		}
	}

	// AH: added param
	@Override
	protected void responseReceived() {
		final String mexId = this.getMessageExchangeId();
		final String cid = getClientId();
		_engine._contexts.scheduler
				.registerSynchronizer(new Scheduler.Synchronizer() {
					public void afterCompletion(boolean success) {
						__log.debug("Received myrole mex response callback");
						if (success) {
							ResponseCallback callback = _waitingCallbacks
									.remove(cid);
							if (callback != null)
								callback.responseReceived(mexId);
						} else {
							__log.warn("Transaction is rolled back on sending back the response.");
						}
					}

					public void beforeCompletion() {
					}
				});
	}

	static class ResponseCallback {
		// AH:
		private String mexId = null;
		// AH: end
		private boolean _timedout;
		private boolean _waiting = true;

		// AH: added param
		synchronized boolean responseReceived(String mexId) {
			// AH:
			this.mexId = mexId;
			// AH: end
			if (_timedout) {
				return false;
			}
			_waiting = false;
			this.notify();
			return true;
		}

		synchronized void waitResponse(long timeout) {
			long etime = timeout == 0 ? Long.MAX_VALUE : System
					.currentTimeMillis() + timeout;
			long ctime;
			try {
				while (_waiting && (ctime = System.currentTimeMillis()) < etime) {
					this.wait(etime - ctime);
				}
			} catch (InterruptedException ie) {
				// ignore
			}
			_timedout = _waiting;
		}

		String getMexId() {
			return mexId;
		}
	}
}
