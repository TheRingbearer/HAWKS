package org.apache.ode.dao.jpa;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.ProcessInstanceMigrationDAO;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;

@Entity
@Table(name = "ODE_INSTANCE_MIGRATION")
/**
 * @author hahnml
 * 
 *         OpenJPA implementation of the {@link ProcessInstanceDAO} interface.
 * 
 */
public class ProcessInstanceMigrationDAOImpl extends OpenJPADAO implements
		ProcessInstanceMigrationDAO {

	@Id
	@Column(name = "MIGRATION_ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	@SuppressWarnings("unused")
	private Long _id;

	@OneToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST })
	@Column(name = "PROCESSINSTANCE_ID")
	private ProcessInstanceDAOImpl _processInstance;

	@Basic
	@Column(name = "MIGRATED")
	private boolean _isMigrated;

	@Basic
	@Column(name = "SUSPENDED")
	private boolean _isSuspended;

	@Basic
	@Column(name = "FINISHED")
	private boolean _isFinished;

	@Basic
	@Column(name = "ITERATED")
	private boolean _isIterated;

	@Lob
	@Column(name = "ITERATION_JOB")
	private JobDetails _iterationJobDetails;

	public ProcessInstanceMigrationDAOImpl() {
	}

	public ProcessInstanceMigrationDAOImpl(ProcessInstanceDAOImpl pi) {
		_processInstance = pi;
	}

	public ProcessInstanceDAO getProcessInstance() {
		return _processInstance;
	}

	public boolean isFinished() {
		return _isFinished;
	}

	public boolean isMigrated() {
		return _isMigrated;
	}

	public boolean isSuspended() {
		return _isSuspended;
	}

	public void setFinished(boolean finished) {
		this._isFinished = finished;
	}

	public void setMigrated(boolean migrated) {
		this._isMigrated = migrated;
	}

	public void setSuspended(boolean suspended) {
		this._isSuspended = suspended;
	}

	public void setIterated(boolean iterated) {
		this._isIterated = iterated;
	}

	public boolean isIterated() {
		return this._isIterated;
	}

	public JobDetails getIterationJobDetails() {
		return this._iterationJobDetails;
	}

	public void setIterationJobDetails(JobDetails iterationJobDetails) {
		this._iterationJobDetails = iterationJobDetails;
	}
}
