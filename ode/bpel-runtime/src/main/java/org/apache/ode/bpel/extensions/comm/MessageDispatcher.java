package org.apache.ode.bpel.extensions.comm;

import java.io.Serializable;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.apache.ode.bpel.extensions.comm.messages.engineIn.Compensate_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Complete_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue_Loop;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Continue_Loop_Execution;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Fault_To_Scope;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Finish_Loop_Execution;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Read_Variable;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RegisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.RequestRegistrationInformation;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Resume_Instance;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Set_Link_State;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Skip_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Start_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Suppress_Fault;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Suspend_Instance;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Terminate_Activity;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.UnregisterRequestMessage;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_CorrelationSet;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_PartnerLink;
import org.apache.ode.bpel.extensions.comm.messages.engineIn.Write_Variable;
import org.apache.ode.bpel.extensions.handler.IncomingMessageHandler;
import org.apache.ode.bpel.extensions.sync.Constants;

//@stmz: receives incoming messages
public class MessageDispatcher implements MessageListener {

	public MessageDispatcher() {

	}

	// @stmz: for every type of incoming event call a specific method in the
	// IncomingMessageHandler
	public void onMessage(Message aMessage) {
		ObjectMessage message = (ObjectMessage) aMessage;
		IncomingMessageHandler incMess = IncomingMessageHandler.getInstance();

		try {
			Serializable aObject = message.getObject();
			if (aObject instanceof Compensate_Scope) {
				Compensate_Scope tmp = (Compensate_Scope) aObject;
				incMess.Compensate_Scope(tmp);
			} else if (aObject instanceof Complete_Activity) {
				Complete_Activity tmp = (Complete_Activity) aObject;
				incMess.Complete_Activity(tmp);
			} else if (aObject instanceof Continue_Loop_Execution) {
				Continue_Loop_Execution tmp = (Continue_Loop_Execution) aObject;
				incMess.Continue_Loop_Execution(tmp);
			} else if (aObject instanceof Continue_Loop) {
				Continue_Loop tmp = (Continue_Loop) aObject;
				incMess.Continue_Loop(tmp);
			} else if (aObject instanceof Continue) {
				Continue tmp = (Continue) aObject;
				incMess.Continue(tmp);
			} else if (aObject instanceof Fault_To_Scope) {
				Fault_To_Scope tmp = (Fault_To_Scope) aObject;
				incMess.Fault_To_Scope(tmp);
			} else if (aObject instanceof Finish_Loop_Execution) {
				Finish_Loop_Execution tmp = (Finish_Loop_Execution) aObject;
				incMess.Finish_Loop_Execution(tmp);
			} else if (aObject instanceof Read_Variable) {
				Read_Variable tmp = (Read_Variable) aObject;
				Destination destination = aMessage.getJMSReplyTo();
				incMess.Read_Variable(destination, tmp);
			} else if (aObject instanceof RegisterRequestMessage) {
				RegisterRequestMessage tmp = (RegisterRequestMessage) aObject;
				Destination destination = aMessage.getJMSReplyTo();
				incMess.RegisterRequestMessage(tmp, destination);
			} else if (aObject instanceof RequestRegistrationInformation) {
				RequestRegistrationInformation tmp = (RequestRegistrationInformation) aObject;
				Destination destination = aMessage.getJMSReplyTo();
				incMess.RequestRegistrationInformation(destination, tmp);
			} else if (aObject instanceof Set_Link_State) {
				Set_Link_State tmp = (Set_Link_State) aObject;
				incMess.Set_Link_State(tmp);
			} else if (aObject instanceof Skip_Activity){
				Skip_Activity tmp = (Skip_Activity) aObject;
				incMess.Skip_Activity(tmp);
			} else if (aObject instanceof Start_Activity) {
				Start_Activity tmp = (Start_Activity) aObject;
				incMess.Start_Activity(tmp);
			} else if (aObject instanceof Suppress_Fault) {
				Suppress_Fault tmp = (Suppress_Fault) aObject;
				incMess.Suppress_Fault(tmp);
			} else if (aObject instanceof Terminate_Activity) {
				Terminate_Activity tmp = (Terminate_Activity) aObject;
				incMess.Terminate_Activity(tmp);			
			} else if (aObject instanceof Write_Variable) {
				Write_Variable tmp = (Write_Variable) aObject;
				incMess.Write_Variable(tmp);
			} else if (aObject instanceof UnregisterRequestMessage) {
				UnregisterRequestMessage tmp = (UnregisterRequestMessage) aObject;
				Destination destination = aMessage.getJMSReplyTo();
				incMess.unregister(destination);
			} else if (aObject instanceof Suspend_Instance) {
				Suspend_Instance tmp = (Suspend_Instance) aObject;
				incMess.Suspend_Instance(tmp);
			} else if (aObject instanceof Resume_Instance) {
				Resume_Instance tmp = (Resume_Instance) aObject;
				incMess.Resume_Instance(tmp);
			} else if (aObject instanceof Write_PartnerLink) {
				Write_PartnerLink tmp = (Write_PartnerLink) aObject;
				incMess.Write_PartnerLink(tmp);
			} else if (aObject instanceof Write_CorrelationSet) {
				Write_CorrelationSet tmp = (Write_CorrelationSet) aObject;

			} else {
				if (Constants.DEBUG_LEVEL > 0) {
					System.out.println("\nIncoming Message is unknown.\n");
				}
			}

		} catch (JMSException e) {
			System.out.println("\nUnable to handle an incoming Message.\n");
			e.printStackTrace();
		}

	}

}
