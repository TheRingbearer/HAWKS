package org.apache.ode.dao.jpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.SnapshotDAO;
import org.apache.ode.bpel.dao.SnapshotPartnerlinksDAO;
import org.apache.ode.bpel.dao.SnapshotVariableDAO;

@Entity
@Table(name = "ODE_SNAPSHOT_DATA")
@NamedQueries({ @NamedQuery(name = SnapshotDAOImpl.COUNT_SNAPSHOTS_BY_XPATH_AND_INSTANCEID, query = "select count(s._version) from SnapshotDAOImpl as s where s._xpath = :xpath and s._processInstanceId = :instanceID") })
public class SnapshotDAOImpl extends OpenJPADAO implements SnapshotDAO {

	public final static String COUNT_SNAPSHOTS_BY_XPATH_AND_INSTANCEID = "COUNT_SNAPSHOTS_BY_XPATH_AND_INSTANCEID";

	@Id
	@Column(name = "SNAPSHOT_ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long _id;
	@Basic
	@Column(name = "PROCESSINSTANCE_ID")
	private Long _processInstanceId;

	@Basic
	@Column(name = "ACTIVITY_PATH")
	private String _xpath;

	@Basic
	@Column(name = "SNAPSHOT_VERSION")
	private Long _version;

	@Basic
	@Column(name = "SNAPSHOT_CREATED")
	private Date _creationTime;

	@OneToMany(targetEntity = SnapshotVariableDAOImpl.class, mappedBy = "_snapshot", fetch = FetchType.LAZY, cascade = {
			CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST })
	private Collection<SnapshotVariableDAO> _variables = new ArrayList<SnapshotVariableDAO>();
	@OneToMany(targetEntity = SnapshotPartnerlinksDAOImpl.class, mappedBy = "_snapshot", fetch = FetchType.LAZY, cascade = {
			CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST })
	private Collection<SnapshotPartnerlinksDAO> _partnerlinks = new ArrayList<SnapshotPartnerlinksDAO>();

	@ManyToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	@Column(name = "PROCESSINSTANCE_ID")
	private ProcessInstanceDAOImpl _processInstanceDao;

	public SnapshotDAOImpl() {

	}

	public SnapshotDAOImpl(Long piid, String xpath, ProcessInstanceDAOImpl pi,
			Long version) {
		_processInstanceId = piid;
		_xpath = xpath;
		_processInstanceDao = pi;
		_version = version;
		_creationTime = new Date();
	}

	public SnapshotPartnerlinksDAO createSnapshotPartnerlink(int plinkModelId,
			String pLinkName, String myRole, String partnerRole) {
		SnapshotPartnerlinksDAOImpl pl = new SnapshotPartnerlinksDAOImpl(
				plinkModelId, pLinkName, myRole, partnerRole);
		pl.setSnapshotDAO(this);
		_partnerlinks.add(pl);
		return pl;
	}

	public SnapshotVariableDAO createSnapshotVariable(String name) {
		SnapshotVariableDAOImpl var = new SnapshotVariableDAOImpl(this, name);
		var.setSnapshotDAO(this);
		_variables.add(var);
		return var;
	}

	public ProcessInstanceDAO getProcessInstanceDAO() {
		return _processInstanceDao;
	}

	public String getXpath() {
		return _xpath;
	}

	public Long getSnapshotID() {

		return _id;
	}

	public Long getProcessinstanceID() {

		return _processInstanceId;
	}

	// get the variables, which has the same name.
	public SnapshotVariableDAO getVariable(String variablename) {
		SnapshotVariableDAO ret = null;
		for (SnapshotVariableDAO var : _variables) {
			if (var.getName().equals(variablename))
				return var;
		}
		ret = new SnapshotVariableDAOImpl(this, variablename);
		_variables.add(ret);
		return ret;
	}

	// get all the variables
	public Collection<SnapshotVariableDAO> getVariables() {
		return _variables;
	}

	public SnapshotPartnerlinksDAO getPartnerlink(int plinkModelId) {
		for (SnapshotPartnerlinksDAO par : _partnerlinks) {
			if (par.getPartnerLinkModelId() == plinkModelId)
				return par;
		}
		return null;
	}

	public Collection<SnapshotPartnerlinksDAO> getPartnerLinks() {
		return _partnerlinks;
	}

	public Long getVersion() {
		return _version;
	}

	public Date getCreationTime() {
		return _creationTime;
	}
}
