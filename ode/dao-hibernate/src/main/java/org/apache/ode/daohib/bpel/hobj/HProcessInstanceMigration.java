package org.apache.ode.daohib.bpel.hobj;

/**
 * Hibernate table representing a BPEL process instance migration.
 * 
 * @hibernate.class table="BPEL_INSTANCE_MIGRATION"
 * 
 */
public class HProcessInstanceMigration extends HObject {

	/** Foreign key to owner {@link HProcessInstance}. */
	private HProcessInstance _processInstance;

	private boolean _isFinished;
	private boolean _isSuspended;
	private boolean _isMigrated;
	private boolean _isIterated;

	public HProcessInstanceMigration() {
		super();
	}

	/**
	 * The process instance finished flag.
	 * 
	 * @hibernate.property column="FINISHED"
	 */
	public boolean isFinished() {
		return _isFinished;
	}

	public void setFinished(boolean finished) {
		_isFinished = finished;
	}

	/**
	 * The process instance suspended flag.
	 * 
	 * @hibernate.property column="SUSPENDED"
	 */
	public boolean isSuspended() {
		return _isSuspended;
	}

	public void setSuspended(boolean suspended) {
		_isSuspended = suspended;
	}

	/**
	 * The process instance migrated flag.
	 * 
	 * @hibernate.property column="MIGRATED"
	 */
	public boolean isMigrated() {
		return _isMigrated;
	}

	public void setMigrated(boolean migrated) {
		_isMigrated = migrated;
	}

	/**
	 * Get the {@link HProcessInstance} to which this migration object belongs.
	 * 
	 * @hibernate.one-to-one column="PIID" foreign-key="none"
	 */
	public HProcessInstance getProcessInstance() {
		return _processInstance;
	}

	public void setProcessInstance(HProcessInstance instance) {
		_processInstance = instance;
	}

	/**
	 * The process instance iterated flag.
	 * 
	 * @hibernate.property column="ITERATED"
	 */
	public boolean isIterated() {
		return _isIterated;
	}

	public void setIterated(boolean iterated) {
		_isIterated = iterated;
	}
}
