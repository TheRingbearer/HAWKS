package org.apache.ode.bpel.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.transaction.TransactionManager;
import javax.xml.namespace.QName;

import org.apache.ode.bpel.fcapi.FragmentManagement;
import org.apache.ode.bpel.fcapi.ManagementFault;
import org.apache.ode.bpel.fcapi.StringListDocument;
import org.apache.ode.bpel.fcapi.TStringList;
import org.apache.ode.bpel.iapi.BpelServer;
import org.apache.ode.bpel.iapi.ProcessState;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentManagementImpl implements FragmentManagement {
	private BpelServer server;
	private TransactionManager txManager;

	public FragmentManagementImpl(BpelServer server,
			TransactionManager txManager) {
		this.server = server;
		this.txManager = txManager;
	}

	public StringListDocument getAvailableFragments() throws ManagementFault {
		StringListDocument ret = StringListDocument.Factory.newInstance();
		TStringList list = ret.addNewStringList();
		boolean success = true;
		try {
			txManager.begin();
			HashMap<QName, BpelProcess> processes = ((BpelEngineImpl) server
					.getEngine())._activeProcesses;
			try {
				txManager.commit();
			} catch (Exception e) {
				success = false;
				throw new ManagementFault("Could not commit the transaction");
			}
			Set<QName> names = processes.keySet();
			Iterator<QName> iterator = names.iterator();
			while (iterator.hasNext()) {
				QName name = iterator.next();
				BpelProcess prc = processes.get(name);
				if (prc._pconf.getState().equals(ProcessState.ACTIVE)
						&& !isCompositeFragment(prc.getOProcess())) {
					if (prc.getOProcess().procesScope.activity instanceof OFragmentScope) {
						// this is a process fragment
						list.addElement(name.toString());
					}
				}
			}

		} catch (Exception e) {
			success = false;
			throw new ManagementFault("Could not begin the transaction");

		} finally {
			if (!success) {
				try {
					txManager.rollback();
				} catch (Exception e) {
					throw new ManagementFault(
							"Could not rollback the transaction");
				}
			}
		}

		return ret;
	}

	public StringListDocument getAvailableNonStartFragments()
			throws ManagementFault {
		StringListDocument ret = StringListDocument.Factory.newInstance();
		TStringList list = ret.addNewStringList();
		boolean success = true;
		try {
			txManager.begin();
			HashMap<QName, BpelProcess> processes = ((BpelEngineImpl) server
					.getEngine())._activeProcesses;
			try {
				txManager.commit();
			} catch (Exception e) {
				success = false;
				throw new ManagementFault("Could not commit the transaction");
			}
			Set<QName> names = processes.keySet();
			Iterator<QName> iterator = names.iterator();
			while (iterator.hasNext()) {
				QName name = iterator.next();
				BpelProcess prc = processes.get(name);
				if (prc._pconf.getState().equals(ProcessState.ACTIVE)
						&& !isCompositeFragment(prc.getOProcess())) {
					if (prc.getOProcess().procesScope.activity instanceof OFragmentScope) {
						// this is a process fragment
						if (!isStartFragment(prc.getOProcess())) {
							list.addElement(name.toString());
						}
					}
				}
			}

		} catch (Exception e) {
			success = false;
			throw new ManagementFault("Could not begin the transaction");

		} finally {
			if (!success) {
				try {
					txManager.rollback();
				} catch (Exception e) {
					throw new ManagementFault(
							"Could not rollback the transaction");
				}
			}
		}

		return ret;
	}

	private boolean isStartFragment(OProcess process) {
		boolean result = true;
		List<OBase> children = process.getChildren();
		for (OBase child : children) {
			if (child instanceof OFragmentEntry) {
				result = false;
				break;
			}
		}
		return result;
	}

	public StringListDocument getAvailableStartFragments()
			throws ManagementFault {
		StringListDocument ret = StringListDocument.Factory.newInstance();
		TStringList list = ret.addNewStringList();
		boolean success = true;
		try {
			txManager.begin();
			HashMap<QName, BpelProcess> processes = ((BpelEngineImpl) server
					.getEngine())._activeProcesses;
			try {
				txManager.commit();
			} catch (Exception e) {
				success = false;
				throw new ManagementFault("Could not commit the transaction");
			}
			Set<QName> names = processes.keySet();
			Iterator<QName> iterator = names.iterator();
			while (iterator.hasNext()) {
				QName name = iterator.next();
				BpelProcess prc = processes.get(name);
				if (prc._pconf.getState().equals(ProcessState.ACTIVE)
						&& !isCompositeFragment(prc.getOProcess())) {
					if (prc.getOProcess().procesScope.activity instanceof OFragmentScope) {
						// this is a process fragment
						if (isStartFragment(prc.getOProcess())) {
							list.addElement(name.toString());
						}
					}
				}
			}

		} catch (Exception e) {
			success = false;
			throw new ManagementFault("Could not begin the transaction");

		} finally {
			if (!success) {
				try {
					txManager.rollback();
				} catch (Exception e) {
					throw new ManagementFault(
							"Could not rollback the transaction");
				}
			}
		}

		return ret;
	}

	private boolean isCompositeFragment(OProcess process) {
		return process.getGluedFragmentsCount() > 0;
	}
}
