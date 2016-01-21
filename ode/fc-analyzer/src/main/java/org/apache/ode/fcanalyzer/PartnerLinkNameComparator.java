package org.apache.ode.fcanalyzer;

import java.util.Comparator;

import org.apache.ode.bpel.o.OPartnerLink;

public class PartnerLinkNameComparator implements Comparator<OPartnerLink>{

	public int compare(OPartnerLink o1, OPartnerLink o2) {
		return o1.name.compareTo(o2.name);
	}

}
