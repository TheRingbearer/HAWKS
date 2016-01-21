package org.apache.ode.bpel.engine.fc;

import java.util.Collection;
import java.util.HashMap;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.util.fc.FragmentCompositionUtil;

public class DeploymentUnitNameGenerator {
	private HashMap<QName, Integer> unitNumberMap;

	public DeploymentUnitNameGenerator() {
		unitNumberMap = new HashMap<QName, Integer>();

	}

	public void registerProcess(BpelProcess process) {

		String duName = FragmentCompositionUtil.getDeploymentUnitName(process
				.getConf());
		QName rootQName = FragmentCompositionUtil.getRootProcessQName(process
				.getConf());

		if (unitNumberMap.containsKey(process.getPID())) {
			Integer max = unitNumberMap.get(process.getPID());
			Integer current = parseCurrentVersion(duName);

			if (max.compareTo(current) < 0) {
				unitNumberMap.put(rootQName, current);
			}
		} else {
			Integer current = parseCurrentVersion(duName);
			unitNumberMap.put(rootQName, current);
		}

	}

	public synchronized String getDeploymentUnitName(BpelProcess process) {
		String result;
		QName type = FragmentCompositionUtil.getRootProcessQName(process
				.getConf());
		Integer number;
		if (unitNumberMap.containsKey(type)) {
			number = unitNumberMap.get(type) + 1;
		} else {
			number = 1;
		}
		unitNumberMap.put(type, number);
		result = generateDeploymentUnitName(process, number);
		return result;
	}

	private String generateDeploymentUnitName(BpelProcess process, int version) {
		String duName = FragmentCompositionUtil.getDeploymentUnitName(process
				.getConf());
		String name = getNonversioniedDuName(duName);

		StringBuffer buffer = new StringBuffer();
		buffer.append(name);
		buffer.append("_");
		buffer.append(parseRootVersion(duName));
		buffer.append("_");
		buffer.append(version);
		return buffer.toString();
	}

	private String getNonversioniedDuName(String duName) {
		String result = null;
		// parse if it is not a root deployment unit
		int indexLast = duName.lastIndexOf('_');
		if (indexLast != -1) {
			String version = duName.substring(0, indexLast);
			int indexFirst = version.lastIndexOf('_');
			if (indexFirst != -1) {
				version = version.substring(indexFirst + 1);
				try {
					Integer.parseInt(version);
					result = duName.substring(0, indexFirst);
				} catch (NumberFormatException e) {

				}
			}
		}
		if (result == null) {
			int index = duName.lastIndexOf('-');
			if (index != -1) {
				result = duName.substring(0, index);
			}
		}
		return result;
	}

	private int parseRootVersion(String duName) {
		int result = 0;
		// parse if it is not a root deployment unit
		int indexLast = duName.lastIndexOf('_');
		if (indexLast != -1) {
			String version = duName.substring(0, indexLast);
			int indexFirst = version.lastIndexOf('_');
			if (indexFirst != -1) {
				version = version.substring(indexFirst + 1);
				try {
					result = Integer.parseInt(version);
				} catch (NumberFormatException e) {

				}
			}
		}
		// parse if it is root deployment unit
		if (result == 0) {
			int index = duName.lastIndexOf('-');
			if (index != -1) {
				String version = duName.substring(index + 1);
				try {
					result = Integer.parseInt(version);
				} catch (NumberFormatException e) {
				}
			}
		}
		return result;
	}

	private int parseCurrentVersion(String duName) {
		int result = 0;
		// parse if it is not a root deployment unit
		int indexBegin = duName.lastIndexOf('_');
		int indexEnd = duName.lastIndexOf('-');
		if (indexBegin != -1) {
			String version = duName.substring(indexBegin + 1, indexEnd);
			try {
				result = Integer.parseInt(version);
			} catch (NumberFormatException e) {

			}

		}

		return result;
	}
}
