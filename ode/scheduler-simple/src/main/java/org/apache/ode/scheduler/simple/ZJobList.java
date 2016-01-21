package org.apache.ode.scheduler.simple;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.iapi.Scheduler;

//@stmz: contains a list of jobs, that need to be executed
//check BpelEngineImpl to see how that works
public class ZJobList {
	private static final Log _log = LogFactory.getLog(ZJobList.class);

	private List<Scheduler.JobInfo> jobs;
	private static ZJobList instance;

	/**
	 * @hahnml: Set of Jobs which are scheduled persisted, deleted from DB and
	 *          saved in the jobList where they are waiting to be processed
	 *          since last LoadImmediateTask.
	 */
	private List<String> jobListHistory;

	private ZJobList() {
		jobs = Collections
				.synchronizedList(new LinkedList<Scheduler.JobInfo>());
		jobListHistory = Collections.synchronizedList(new LinkedList<String>());
	}

	public static ZJobList getInstance() {
		if (instance == null) {
			instance = new ZJobList();
		}
		return instance;
	}

	public void addJobInfo(Scheduler.JobInfo info) {
		synchronized (instance) {
			if (_log.isDebugEnabled()) {
				_log.debug("Try to add " + info + " to jobList.");
			}
			if (!jobListHistory.contains(info.jobName.trim())
					&& !jobListContainsJobWithDetails(info.jobDetail)) {
				jobs.add(info);
				jobListHistory.add(info.jobName.trim());
				if (_log.isDebugEnabled()) {
					_log.debug("Added successfully " + info + " to jobList.");
				}
			}
		}
	}

	public Scheduler.JobInfo getJobInfo() {
		Scheduler.JobInfo info = null;
		synchronized (instance) {
			if (!jobs.isEmpty()) {
				info = (Scheduler.JobInfo) jobs.get(0);
				jobs.remove(0);
				if (_log.isDebugEnabled()) {
					_log.debug("Job: " + info + " was removed from jobList.");
				}
			}
		}
		return info;
	}

	public List<Scheduler.JobInfo> getJobs() {
		return jobs;
	}

	public List<String> getJobListHistory() {
		return jobListHistory;
	}

	public void clearJobListHistory() {
		synchronized (instance) {
			jobListHistory.clear();
			if (_log.isDebugEnabled()) {
				_log.debug("JobList history was cleared.");
			}
			// @hahnml: CHECK if some jobs still in jobList waiting for
			// execution, we add them to the history list again to prevent
			// double insertion of jobs.
			for (Scheduler.JobInfo info : jobs) {
				jobListHistory.add(info.jobName.trim());
				if (_log.isDebugEnabled()) {
					_log.debug("Add job: " + info.jobName.trim()
							+ " to jobList history again.");
				}
			}
		}
	}

	public boolean containsHistory(String jobId) {
		synchronized (instance) {
			if (_log.isDebugEnabled()) {
				_log.debug("Check if jobList history contains job: "
						+ jobId.trim());
			}
			return jobListHistory.contains(jobId.trim());
		}
	}

	private boolean jobListContainsJobWithDetails(Scheduler.JobDetails details) {
		synchronized (instance) {
			if (_log.isDebugEnabled()) {
				_log.debug("Check if jobDetails " + details
						+ " are known already");
			}
			for (Scheduler.JobInfo info : jobs) {
				if (info.jobDetail == details) {
					if (_log.isDebugEnabled()) {
						_log.debug("JobDetails " + info.jobDetail + " of job "
								+ info + " are known already.");
					}
					return true;
				}
			}
			return false;
		}
	}
}
