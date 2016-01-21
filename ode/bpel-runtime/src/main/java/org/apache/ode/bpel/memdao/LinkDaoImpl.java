package org.apache.ode.bpel.memdao;

import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;

public class LinkDaoImpl extends DaoBaseImpl implements LinkDAO {

	private int _linkModelId;
	private ProcessInstanceDaoImpl _processInstance;
	private LinkStateEnum _state;
	private String _xpath;
	
	public LinkDaoImpl(ProcessInstanceDaoImpl processInstanceDaoImpl,
			String xpath, int linkModelId) {
		_processInstance = processInstanceDaoImpl;
		_xpath = xpath;
		_linkModelId = linkModelId;
	}

	public int getModelId() {
		return _linkModelId;
	}

	public String getXPath() {
		return _xpath;
	}

	public void setState(LinkStateEnum state) {
		_state = state;
	}

	public LinkStateEnum getState() {
		return _state;
	}

	public ProcessInstanceDAO getProcessInstance() {
		return _processInstance;
	}

}
