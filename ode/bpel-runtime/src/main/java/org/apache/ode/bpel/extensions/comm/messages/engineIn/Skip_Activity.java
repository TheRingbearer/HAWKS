package org.apache.ode.bpel.extensions.comm.messages.engineIn;

/**
 * An incoming message, that tells the workflow to skip a specified activity.
 * 
 * @author krawczls
 *
 */
public class Skip_Activity extends IncomingMessageBase {
	
	private static final long serialVersionUID = 1L;

	private Long replyToMsgID;

	/**
	 * A default constructor.
	 */
	public Skip_Activity() {
		super();
	}

	/**
	 * Getter method for the field replyToMsgID.
	 * 
	 * @return
	 */
	public Long getReplyToMsgID() {
		return replyToMsgID;
	}

	/**
	 * Setter method for the field replyToMsgID.
	 * 
	 * @param replyToMsgID
	 */
	public void setReplyToMsgID(Long replyToMsgID) {
		this.replyToMsgID = replyToMsgID;
	}
}