package org.apache.ode.bpel.extensions.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

//@stmz: new formatter to log events in XML style
public class Logging {

	public static Logger logger = Logger.getLogger("Log-XML");

	ArrayList<String> act_evts = new ArrayList<String>();
	ArrayList<String> scope_evts = new ArrayList<String>();
	ArrayList<String> link_evts = new ArrayList<String>();
	ArrayList<String> instance_evts = new ArrayList<String>();
	ArrayList<String> deploy_evts = new ArrayList<String>();

	Boolean enabled = true;

	public Logging() {
		listEvents();

		// Logger
		try {
			FileHandler fh2;
			fh2 = new FileHandler("event_log.txt");
			fh2.setFormatter(formatter);
			logger.addHandler(fh2);
			logger.setLevel(Level.ALL);
			logger.fine("<?xml version=\"1.0\"?>");
			logger.fine("<log>\n");
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Formatter for an XML log file
	public Formatter formatter = new Formatter() {

		@Override
		public String format(LogRecord record) {
			String message = formatMessage(record);
			String[] parts = message.split("\\!\\?\\%\\&\\$\\!");
			StringBuffer buf = new StringBuffer();

			if (enabled) {
				if (act_evts.contains(parts[0])) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <XPATH> ");
					buf.append(parts[2]);
					buf.append(" </XPATH>\n");
					/*
					 * buf.append(" <ID> "); buf.append(parts[1]);
					 * buf.append(" </ID>\n");
					 */
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[5]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[3]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_ID>\n");
					if (parts[0]
							.equals("Evaluating_TransitionCondition_Faulted")) {
						buf.append(" <LINK_XPATH> ");
						buf.append(parts[6]);
						buf.append(" </LINK_XPATH>\n");
					}
					buf.append("</EVENT>\n");
				}

				else if (link_evts.contains(parts[0])) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <XPATH> ");
					buf.append(parts[2]);
					buf.append(" </XPATH>\n");
					if (parts[0].equals("Link_Evaluated")) {
						buf.append(" <VALUE> ");
						buf.append(parts[6]);
						buf.append(" </VALUE>\n");
					}
					buf.append(" <LINK_NAME> ");
					buf.append(parts[1]);
					buf.append(" </LINK_NAME>\n");
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[5]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[3]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_ID>\n");
					buf.append("</EVENT>\n");
				}

				else if (instance_evts.contains(parts[0])) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[1]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[2]);
					buf.append(" </PROCESS_ID>\n");
					buf.append("</EVENT>\n");
				}

				else if (deploy_evts.contains(parts[0])) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[1]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append("</EVENT>\n");
				}

				else if (scope_evts.contains(parts[0])) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <XPATH> ");
					buf.append(parts[2]);
					buf.append(" </XPATH>\n");
					/*
					 * buf.append(" <ID> "); buf.append(parts[1]);
					 * buf.append(" </ID>\n");
					 */
					buf.append(" <SCOPE_ID> ");
					buf.append(parts[6]);
					buf.append(" </SCOPE_ID>\n");
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[5]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[3]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_ID>\n");
					buf.append("</EVENT>\n");
				}

				else if (parts[0].equals("Scope_Handling_Fault")) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <XPATH> ");
					buf.append(parts[2]);
					buf.append(" </XPATH>\n");
					/*
					 * buf.append(" <ID> "); buf.append(parts[1]);
					 * buf.append(" </ID>\n");
					 */
					buf.append(" <SCOPE_ID> ");
					buf.append(parts[6]);
					buf.append(" </SCOPE_ID>\n");
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[5]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[3]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_ID>\n");
					buf.append(" <FAULT_NAME> ");
					buf.append(parts[7]);
					buf.append(" </FAULT_NAME>\n");
					buf.append(" <FAULT_MESSAGE> ");
					buf.append(parts[8]);
					buf.append(" </FAULT_MESSAGE>\n");
					buf.append(" <MESSAGE_TYPE> ");
					buf.append(parts[9]);
					buf.append(" </MESSAGE_TYPE>\n");
					buf.append(" <ELEMENT_TYPE> ");
					buf.append(parts[10]);
					buf.append(" </ELEMENT_TYPE>\n");
					buf.append("</EVENT>\n");
				}

				else if (parts[0].equals("Variable_Modification")) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <ACTIVITY_XPATH> ");
					buf.append(parts[1]);
					buf.append(" </ACTIVITY_XPATH>\n");
					/*
					 * buf.append(" <ACTIVITY_ID> "); buf.append(parts[2]);
					 * buf.append(" </ACTIVITY_ID>\n");
					 */
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[3]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[5]);
					buf.append(" </PROCESS_ID>\n");
					buf.append(" <VARIABLE_NAME> ");
					buf.append(parts[7]);
					buf.append(" </VARIABLE_NAME>\n");
					buf.append(" <VARIABLE_XPATH> ");
					buf.append(parts[8]);
					buf.append(" </VARIABLE_XPATH>\n");
					buf.append(" <VARIABLE_VALUE> ");
					buf.append(parts[6]);
					buf.append(" </VARIABLE_VALUE>\n");
					buf.append("</EVENT>\n");
				}

				else if (parts[0].equals("PartnerLink_Modification")) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <ACTIVITY_XPATH> ");
					buf.append(parts[1]);
					buf.append(" </ACTIVITY_XPATH>\n");
					/*
					 * buf.append(" <ACTIVITY_ID> "); buf.append(parts[2]);
					 * buf.append(" </ACTIVITY_ID>\n");
					 */
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[3]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[4]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[5]);
					buf.append(" </PROCESS_ID>\n");
					buf.append(" <PARTNERLINK_NAME> ");
					buf.append(parts[7]);
					buf.append(" </PARTNERLINK_NAME>\n");
					buf.append(" <PARTNERLINK_XPATH> ");
					buf.append(parts[8]);
					buf.append(" </PARTNERLINK_XPATH>\n");
					buf.append(" <PARTNERLINK_VALUE> ");
					buf.append(parts[6]);
					buf.append(" </PARTNERLINK_VALUE>\n");
					buf.append("</EVENT>\n");
				}

				else if (parts[0].equals("CorrelationSet_Modification")) {
					buf.append("<EVENT>\n");
					buf.append(" <EVENT_TYPE> ");
					buf.append(parts[0]);
					buf.append(" </EVENT_TYPE>\n");
					buf.append(" <PROCESS_NAME> ");
					buf.append(parts[1]);
					buf.append(" </PROCESS_NAME>\n");
					buf.append(" <PROCESS_ID> ");
					buf.append(parts[2]);
					buf.append(" </PROCESS_ID>\n");
					buf.append(" <PARENT_SCOPE_ID> ");
					buf.append(parts[3]);
					buf.append(" </PARENT_SCOPE_ID>\n");
					buf.append(" <CORRELATIONSET_XPATH> ");
					buf.append(parts[4]);
					buf.append(" </CORRELATIONSET_XPATH>\n");
					buf.append("</EVENT>\n");
				}

				else {
					buf.append(message);
				}

				buf.append("\n");
			}
			return buf.toString();

		}

	};

	public void listEvents() {
		act_evts.add("Activity_Ready");
		act_evts.add("Activity_Executing");
		act_evts.add("Activity_Executed");
		act_evts.add("Activity_Complete");
		act_evts.add("Activity_Faulted");
		act_evts.add("Activity_Terminated");
		act_evts.add("Activity_Dead_Path");
		act_evts.add("Activity_Join_Failure");
		act_evts.add("Iteration_Complete");
		act_evts.add("Loop_Condition_True");
		act_evts.add("Loop_Condition_False");
		act_evts.add("Evaluating_TransitionCondition_Faulted");

		link_evts.add("Link_Ready");
		link_evts.add("Link_Evaluated");
		link_evts.add("Link_Set_True");
		link_evts.add("Link_Set_False");

		instance_evts.add("Process_Instantiated");
		instance_evts.add("Instance_Running");
		instance_evts.add("Instance_Suspended");
		instance_evts.add("Instance_Terminated");
		instance_evts.add("Instance_Faulted");
		instance_evts.add("Instance_Completed");

		deploy_evts.add("Process_Deployed");
		deploy_evts.add("Process_Redeployed");
		deploy_evts.add("Process_Undeployed");
		deploy_evts.add("Process_Active");
		deploy_evts.add("Process_Retired");
		deploy_evts.add("Process_Disabled");

		scope_evts.add("Scope_Activity_Ready");
		scope_evts.add("Scope_Activity_Terminated");
		scope_evts.add("Scope_Activity_Executing");
		scope_evts.add("Scope_Handling_Event");
		scope_evts.add("Scope_Event_Handling_Ended");
		scope_evts.add("Scope_Activity_Faulted");
		scope_evts.add("FaultHandling_NoHandler");
		scope_evts.add("Scope_Complete_With_Fault");
		scope_evts.add("Scope_Activity_Executed");
		scope_evts.add("Scope_Activity_Complete");
		scope_evts.add("Scope_Compensating");
		scope_evts.add("Scope_Compensated");
		scope_evts.add("Scope_Handling_Termination");
	}

	public void endLogging() {
		logger.fine("</log>\n");
	}

}
