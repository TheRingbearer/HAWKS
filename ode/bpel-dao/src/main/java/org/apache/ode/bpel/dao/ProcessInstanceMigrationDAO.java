package org.apache.ode.bpel.dao;

import org.apache.ode.bpel.iapi.Scheduler.JobDetails;

/**
 * @author hahnml
 * 
 * 
 */
public interface ProcessInstanceMigrationDAO {

	/**
	 * Provides the finished flag of the instance, which shows if the instance
	 * is finished or kept alive.
	 * 
	 * @return The finished flag of the instance.
	 */
	boolean isFinished();

	/**
	 * Sets the finished flag of the instance to the given value.
	 * 
	 * @param finish
	 *            The boolean to set the finished flag to.
	 */
	void setFinished(boolean finished);

	/**
	 * Provides the migrated flag of the instance, which shows if the instance
	 * is migrated or not.
	 * 
	 * @return The migrated flag of the instance.
	 */
	boolean isMigrated();

	/**
	 * Sets the migrated flag of the instance to the given value.
	 * 
	 * @param migrated
	 *            The boolean to set the migrated flag to.
	 */
	void setMigrated(boolean migrated);

	/**
	 * Provides the suspended flag of the instance, which shows if the instance
	 * is suspended or not to keep a process instance alive for changing it.
	 * 
	 * @return The suspended flag of the instance.
	 */
	boolean isSuspended();

	/**
	 * Sets the suspended flag of the instance to the given value.
	 * 
	 * @param suspended
	 *            The boolean to set the suspended flag to.
	 */
	void setSuspended(boolean suspended);

	/**
	 * Get the process instance to which this migration belongs.
	 * 
	 * @return owner {@link ProcessInstanceDAO}
	 */
	ProcessInstanceDAO getProcessInstance();

	/**
	 * Sets the iterated flag of the instance to the given value.
	 * 
	 * @param iterated
	 *            The boolean to set the iterated flag to.
	 */
	void setIterated(boolean iterated);

	/**
	 * Provides the iterated flag of the instance, which shows if the instance
	 * was iterated or not.
	 * 
	 * @return The iterated flag of the instance.
	 */
	boolean isIterated();

	/**
	 * Sets the job details with which the instance was iterated.
	 * 
	 * @param job
	 *            The details of the iteration job.
	 */
	void setIterationJobDetails(JobDetails job);

	/**
	 * Provides the job details with which this instance was iterated.
	 * 
	 * @return The job details of the iteration.
	 */
	JobDetails getIterationJobDetails();
}
