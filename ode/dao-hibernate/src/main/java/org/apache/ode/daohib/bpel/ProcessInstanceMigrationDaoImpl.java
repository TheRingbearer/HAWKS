package org.apache.ode.daohib.bpel;

import org.apache.ode.bpel.dao.ProcessInstanceDAO;
import org.apache.ode.bpel.dao.ProcessInstanceMigrationDAO;
import org.apache.ode.bpel.iapi.Scheduler.JobDetails;
import org.apache.ode.daohib.SessionManager;
import org.apache.ode.daohib.bpel.hobj.HProcessInstanceMigration;

/**
 * @author hahnml
 * 
 *         Hibernate-based {@link ProcessInstanceMigrationDAO} implementation.
 */
public class ProcessInstanceMigrationDaoImpl extends HibernateDao implements
		ProcessInstanceMigrationDAO {

	private HProcessInstanceMigration _migration;

	private JobDetails _iterationJob;

	protected ProcessInstanceMigrationDaoImpl(SessionManager sessionManager,
			HProcessInstanceMigration migration) {
		super(sessionManager, migration);
		entering("ProcessInstanceMigrationDaoImpl.ProcessInstanceMigrationDaoImpl");
		_migration = migration;
	}

	public boolean isFinished() {
		entering("ProcessInstanceMigrationDaoImpl.isFinished");
		return _migration.isFinished();
	}

	public boolean isMigrated() {
		entering("ProcessInstanceMigrationDaoImpl.isMigrated");
		return _migration.isMigrated();
	}

	public boolean isSuspended() {
		entering("ProcessInstanceMigrationDaoImpl.isSuspended");
		return _migration.isSuspended();
	}

	public void setFinished(boolean finished) {
		entering("ProcessInstanceMigrationDaoImpl.setFinished");
		_migration.setFinished(finished);
		getSession().update(_migration);
	}

	public void setMigrated(boolean migrated) {
		entering("ProcessInstanceMigrationDaoImpl.setMigrated");
		_migration.setMigrated(migrated);
		getSession().update(_migration);
	}

	public void setSuspended(boolean suspended) {
		entering("ProcessInstanceMigrationDaoImpl.setSuspended");
		_migration.setSuspended(suspended);
		getSession().update(_migration);
	}

	public ProcessInstanceDAO getProcessInstance() {
		entering("ProcessInstanceMigrationDaoImpl.getProcessInstance");
		return new ProcessInstanceDaoImpl(_sm, _migration.getProcessInstance());
	}

	public void setIterated(boolean iterated) {
		entering("ProcessInstanceMigrationDaoImpl.setIterated");
		_migration.setIterated(iterated);
		getSession().update(_migration);
	}

	public boolean isIterated() {
		entering("ProcessInstanceMigrationDaoImpl.isIterated");
		return _migration.isIterated();
	}

	public void setIterationJobDetails(JobDetails job) {
		this._iterationJob = job;
	}

	public JobDetails getIterationJobDetails() {
		return this._iterationJob;
	}
}
