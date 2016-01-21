package org.apache.ode.bpel.o;

import java.util.ArrayList;
import java.util.List;

import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class OFragmentEntry extends OActivity {
	public boolean danglingEntry;
	public boolean ignoredEntry;
	public final List<Variable> variablesToMap = new ArrayList<Variable>();
	public final List<OPartnerLink> partnerLinksToMap = new ArrayList<OPartnerLink>();
	public final List<CorrelationSet> correlationSetsToMap = new ArrayList<CorrelationSet>();

	public OFragmentEntry(OProcess owner, OActivity parent) {
		super(owner, parent);
		ignoredEntry = false;
		danglingEntry = true;
	}

}
