package org.apache.ode.dao.jpa;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.apache.ode.bpel.dao.LinkDAO;
import org.apache.ode.bpel.dao.LinkStateEnum;
import org.apache.ode.bpel.dao.ProcessInstanceDAO;

@Entity
@Table(name = "ODE_LINK")
public class LinkDAOImpl extends OpenJPADAO implements LinkDAO {

	@Id
	@Column(name = "LINK_ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	@SuppressWarnings("unused")
	private Long _id;
	
	@Basic
	@Column(name = "LINK_MODEL_ID")
	private int _linkModelId;

	@Basic
	@Column(name = "LINK_XPATH")
	private String _xpath;
	@Basic
	@Column(name = "LINK_STATE")
	private String _linkState;

	@ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	@Column(name = "PROCESS_INSTANCE_ID")
	private ProcessInstanceDAOImpl _processInstance;

	public LinkDAOImpl() {
	}

	public LinkDAOImpl(String xpath, int linkModelId,
			ProcessInstanceDAOImpl processInstanceDAOImpl) {
		_xpath = xpath;
		_linkModelId = linkModelId;
		_processInstance = processInstanceDAOImpl;
	}

	public int getModelId() {
		return _linkModelId;
	}

	public String getXPath() {
		return _xpath;
	}

	public void setState(LinkStateEnum state) {
		_linkState = state.toString();
	}

	public LinkStateEnum getState() {
		return LinkStateEnum.valueOf(_linkState);
	}

	public ProcessInstanceDAO getProcessInstance() {
		return _processInstance;
	}

}
