package org.apache.ode.daohib.bpel;

import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.daohib.SessionManager;
import org.apache.ode.daohib.bpel.hobj.HLink;

/**
 * @author hahnml
 * 
 *         Hibernate-based {@link LinkDAO} implementation.
 */
public class LinkDaoImpl extends HibernateDao implements
		LinkDAO {
	
	private HLink _link;

	protected LinkDaoImpl(SessionManager sessionManager, HLink hobj) {
		super(sessionManager, hobj);
		entering("LinkDaoImpl.LinkDaoImpl");
		_link = hobj;
	}

	public int getModelId() {
		return _link.getModelId();
	}

	public String getXPath() {
		return _link.getXPath();
	}

	public void setState(LinkStateEnum state) {
		entering("LinkDaoImpl.setState");
		_link.setState(state.toString());
		getSession().update(_link);
	}

	public LinkStateEnum getState() {
		return LinkStateEnum.valueOf(_link.getState());
	}

	public ProcessInstanceDAO getProcessInstance() {
		entering("LinkDaoImpl.getProcessInstance");
		return new ProcessInstanceDaoImpl(_sm, _link.getInstance());
	}
}
