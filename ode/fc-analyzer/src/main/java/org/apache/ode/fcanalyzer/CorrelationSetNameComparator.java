package org.apache.ode.fcanalyzer;

import java.util.Comparator;

import org.apache.ode.bpel.o.OScope.CorrelationSet;

public class CorrelationSetNameComparator implements Comparator<CorrelationSet>{

	public int compare(CorrelationSet o1, CorrelationSet o2) {
		return o1.name.compareTo(o2.name);
	}

}
