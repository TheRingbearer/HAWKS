package org.apache.ode.bpel.engine;

import javax.wsdl.Operation;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.util.XMLUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.ProcessState;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.engine.fc.FragmentCompositionAPI;
import org.apache.ode.bpel.engine.fc.FragmentCompositionAPIImpl;
import org.apache.ode.bpel.evt.ProcessInstanceStateChangeEvent;
import org.apache.ode.bpel.iapi.Message;
import org.apache.ode.bpel.iapi.MyRoleMessageExchange;
import org.apache.ode.il.DynamicService;
import org.apache.ode.jacob.JacobRunnable;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentCompositionEventBroker {

	private static final Log __log = LogFactory
			.getLog(FragmentCompositionEventBroker.class);

	public FragmentCompositionEventBroker() {
	}

	public void continueExecution(BpelProcess process,
			ProcessInstanceDAO procInstanceDAO, JacobRunnable runnable) {
		if (procInstanceDAO != null) {
			BpelRuntimeContextImpl instance = process.createRuntimeContext(
					procInstanceDAO, null, null);
			if (instance != null && process != null) {
				// if we have a message match, this instance should be marked
				// active if it isn't already
				if (procInstanceDAO.getState() == ProcessState.STATE_READY) {
					if (BpelProcess.__log.isDebugEnabled()) {
						BpelProcess.__log
								.debug("INPUTMSGMATCH: Changing process instance state from ready to active");
					}

					procInstanceDAO.setState(ProcessState.STATE_ACTIVE);

					// send event
					ProcessInstanceStateChangeEvent evt = new ProcessInstanceStateChangeEvent();
					evt.setOldState(ProcessState.STATE_READY);
					evt.setNewState(ProcessState.STATE_ACTIVE);
					instance.sendEvent(evt);
				}

				instance.getVPU().inject(runnable);
				instance.execute();
			}
		}
	}

	public void process(BpelProcess process, ProcessInstanceDAO procInstance,
			MyRoleMessageExchange mex) {

		FragmentCompositionAPIImpl api = new FragmentCompositionAPIImpl(
				process, procInstance);

		DynamicService<FragmentCompositionAPI> service = new DynamicService<FragmentCompositionAPI>(
				api);

		String operation = mex.getOperationName();
		Message message = mex.getRequest();
		OMElement payload;
		Operation op = mex.getOperation();
		FragmentCompositionResponse response;

		try {
			payload = XMLUtils.toOM(message.getMessage());
			response = new FragmentCompositionResponse(op, payload
					.getNamespace().getNamespaceURI(), mex, service);
			api.setFCResponseObject(response);
			try {
				service.invokeOneWay(operation, payload);
			} catch (Exception e) {
				response.throwException(e);
			}
		} catch (Exception e) {
			__log.error(e);
		}

	}

}
