package org.apache.ode.bpel.util.fc;

import java.io.File;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.engine.BpelEngineImpl;
import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.engine.BpelRuntimeContextImpl;
import org.apache.ode.bpel.iapi.ProcessConf;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentFlow;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OFragmentSequence;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.runtime.BpelJacobRunnable;
import org.apache.ode.bpel.runtime.ProcessMerger;
import org.apache.ode.fcanalyzer.FragmentEntryExitFinder;

public class FragmentCompositionUtil {

	public static boolean isInside(OActivity outerActivity,
			OActivity innerActivity) {
		boolean result = false;
		if (innerActivity.getId() != outerActivity.getId()) {
			while (innerActivity != null
					&& innerActivity.getId() != outerActivity.getId()) {
				innerActivity = innerActivity.getParent();
			}
			if (innerActivity != null
					&& innerActivity.getId() == outerActivity.getId()) {
				result = true;
			}
		}
		return result;
	}

	public static OFragmentScope getScopeToGlue(OActivity parent,
			OProcess oProcess) {
		OFragmentScope oScopeToGlue = null;

		if (oProcess != null
				&& oProcess.procesScope.activity instanceof OFragmentScope) {
			oScopeToGlue = (OFragmentScope) oProcess.procesScope.activity;

		}

		// return oScopeToGlue.clone(parent.getOwner(), parent);
		return oScopeToGlue;// .clone(parent.getOwner(), parent);
	}

	public static BpelEngineImpl getEngine(BpelJacobRunnable runnable) {
		BpelRuntimeContextImpl runtime = (BpelRuntimeContextImpl) runnable
				.getBpelRuntimeContext();
		return (BpelEngineImpl) runtime.getBpelProcess().getEngine();
	}

	public static boolean isGlueAllowed(OProcess process) {
		int gluedCount = process.getGluedFragmentsCount();
		int allowedToGlueCount = FragmentEntryExitFinder
				.getFragmentRegionsCount(process)
				+ FragmentEntryExitFinder.getFragmentExitsCount(process);
		return gluedCount < allowedToGlueCount;
	}

	public static void merge(BpelEngineImpl engine, BpelProcess mergeInto,
			BpelProcess toMerge, OProcess process, OActivity hostActivity) {
		ProcessMerger procMerger = new ProcessMerger();
		File ddFile = WsdlXsdMerger.findDeploymentDescriptorFile(toMerge
				.getConf().getFiles());

		// procMerger.prepareForMerging(ddFile, process.getQName());
		procMerger.merge(mergeInto.getOProcess(), process, hostActivity);

		List<String> correlators = process.getCorrelators();
		for (String correlator : correlators) {
			if (mergeInto.getProcessDAO().getCorrelator(correlator) == null) {
				mergeInto.getProcessDAO().addCorrelator(correlator);
			}

		}

		mergeInto.serializeCompiledProcess(mergeInto.getOProcess());
		/*
		 * ProcessDAO dao = mergeInto.getProcessDAO(); List<String> correlators
		 * = process.getCorrelators(); for (String correlator : correlators) {
		 * dao.addCorrelator(correlator); }
		 */
		WsdlXsdMerger merger = new WsdlXsdMerger();
		merger.merge(mergeInto.getConf(), toMerge.getConf());

		// String duName = dao.getProcessId().getLocalPart();
		String duName = getDeploymentUnitName(mergeInto.getConf());
		List<ProcessConf> procConfs = engine.getProcessConfLoader().reload(
				duName);
		for (ProcessConf conf : procConfs) {
			if (conf.getType().equals(mergeInto.getConf().getType())) {
				engine.getProcessRegistry().reregister(conf);
				break;
			}
		}

		// mergeInto.setRoles(toMerge.getOProcess());
		/*
		 * for (Endpoint e : toMerge.getServiceNames()) {
		 * engine.addRoutingsToFragment(e.serviceName, mergeInto); }
		 */

	}

	public static String getDeploymentUnitName(ProcessConf conf) {
		String name = "";
		List<File> files = conf.getFiles();

		for (File file : files) {
			if (file.getName().equals("deploy.xml")) {
				name = file.getParentFile().getName();

				break;
			}
		}

		return name;
	}

	public static QName getRootProcessQName(ProcessConf conf) {
		QName result;

		QName current = conf.getProcessId();
		String localPart = current.getLocalPart();
		int indexVer = localPart.lastIndexOf('-');
		if (indexVer != -1) {
			localPart = localPart.substring(0, indexVer);
		}

		String duName = FragmentCompositionUtil.getDeploymentUnitName(conf);
		int indexLast = duName.lastIndexOf('_');
		int version = 0;
		if (indexLast != -1) {
			String temp = duName.substring(0, indexLast);
			int indexFirst = temp.lastIndexOf('_');
			if (indexFirst != -1) {
				String verString = temp.substring(indexFirst + 1);
				try {
					version = Integer.parseInt(verString);
				} catch (NumberFormatException e) {
				}
			}
		}
		if (version == 0) {
			result = current;
		} else {
			StringBuffer buffer = new StringBuffer();
			buffer.append(localPart);
			buffer.append("-");
			buffer.append(version);
			result = new QName(current.getNamespaceURI(), buffer.toString());
		}
		return result;
	}

	public static int findEnclosingFragmentContainer(OProcess process,
			int fragmentExitId, int fragmentEntryId) {
		int result = -1;

		OBase exit = process.getChild(fragmentExitId);
		if (exit != null && exit instanceof OFragmentRegion) {
			result = exit.getId();
		} else {
			OBase entry = process.getChild(fragmentEntryId);
			if (entry != null && entry instanceof OFragmentRegion) {
				// the fragment entry is the region so we have to find
				// the element responsible for fragment exit.
				// to fire wire and map

				if (exit != null && exit instanceof OFragmentExit) {
					result = ((OFragmentExit) exit).getParent().getId();
				}
			} else if (entry != null && entry instanceof OFragmentEntry) {
				OActivity fs = entry.getOldOwner().procesScope.activity;
				if (fs instanceof OFragmentScope) {
					result = fs.getParent().getId();
				}

			}
		}
		return result;
	}

	public static int findEnclosingFragmentContainer(OProcess process,
			int elementId) {
		int result = -1;
		OBase element = process.getChild(elementId);
		if (element != null && element instanceof OActivity) {
			OActivity current = (OActivity) element;
			while (current != null) {
				if (current instanceof OFragmentSequence
						|| current instanceof OFragmentFlow) {
					result = current.getId();
					break;
				}
				current = current.getParent();
			}
		}

		return result;
	}

}
