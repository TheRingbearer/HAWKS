package org.apache.ode.bpel.extensions.events;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.evt.BpelEvent;

public class BpelActivityEvent extends BpelEvent {

	private String xpath_act;
	// @haupt
	private String activity_name;
	private String xpath_surrounding_scope;
	private Long ID_act;
	private Long ID_scope;
	private QName process_name;
	private Long process_ID;
	private Boolean artificial;
	private Boolean isScope;
	// @hahnml: OActivity id
	private int oBaseId;

	/**
	 * 
	 */
	public BpelActivityEvent(String act_name, int oBase_id, String act_loc, Long id,
			String scope_loc, Long scope_id, QName proc_name, Long proc_id,
			Boolean art, Boolean scop) {
		xpath_act = act_loc;
		// @haupt
		activity_name = act_name;
		ID_act = id;
		xpath_surrounding_scope = scope_loc;
		ID_scope = scope_id;
		process_name = proc_name;
		process_ID = proc_id;
		artificial = art;
		isScope = scop;
		// @hahnml
		oBaseId = oBase_id;
	}

	private static final long serialVersionUID = 1L;

	@Override
	public TYPE getType() {
		return TYPE.activityLifecycle;
	}

	public String getXpath_act() {
		return xpath_act;
	}

	// @haupt
	public String getActivity_name() {
		return activity_name;
	}

	public String getXpath_surrounding_scope() {
		return xpath_surrounding_scope;
	}

	public Long getID_act() {
		return ID_act;
	}

	public Long getID_scope() {
		return ID_scope;
	}

	public QName getProcess_name() {
		return process_name;
	}

	public Long getProcess_ID() {
		return process_ID;
	}

	public Boolean getArtificial() {
		return artificial;
	}

	public Boolean getIsScope() {
		return isScope;
	}

	//@hahnml
	public int getOBaseId() {
		return oBaseId;
	}

}
