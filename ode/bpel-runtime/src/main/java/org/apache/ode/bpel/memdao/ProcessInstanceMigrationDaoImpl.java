package org.apache.ode.bpel.memdao;

import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.ProcessInstanceMigrationDAO;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;

/**
 * @author hahnml
 * 
 *         A simple in-memory implementation of the
 *         {@link ProcessInstanceMigrationDAO} interface.
 */
public class ProcessInstanceMigrationDaoImpl implements
		ProcessInstanceMigrationDAO {

	private ProcessInstanceDaoImpl _processInstance;

	private boolean _isFinished = false;
	private boolean _isMigrated = false;
	private boolean _isSuspended = false;
	private boolean _isIterated = false;
	private JobDetails _jobDetails = null;

	public ProcessInstanceMigrationDaoImpl(ProcessInstanceDaoImpl owner) {
		super();
		_processInstance = owner;
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

	public ProcessInstanceDAO getProcessInstance() {
		return _processInstance;
	}

	public void setIterated(boolean iterated) {
		this._isIterated = iterated;
	}

	public boolean isIterated() {
		return this._isIterated;
	}

	public void setIterationJobDetails(JobDetails job) {
		this._jobDetails = job;
	}

	public JobDetails getIterationJobDetails() {
		return this._jobDetails;
	}

}
