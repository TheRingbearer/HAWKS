package org.apache.ode.bpel.o;

import java.util.ArrayList;
import java.util.List;

import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.utils.fc.Mapping;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class OFragmentRegion extends OActivity {
	public OActivity child;
	public boolean danglingEntry;
	public boolean danglingExit;
	public int fragmentEntryId;
	public Mapping[] mappings;

	public final List<Variable> variablesToMap = new ArrayList<Variable>();
	public final List<OPartnerLink> partnerLinksToMap = new ArrayList<OPartnerLink>();
	public final List<CorrelationSet> correlationSetsToMap = new ArrayList<CorrelationSet>();

	public OFragmentRegion(OProcess owner, OActivity parent) {
		super(owner, parent);
		child = null;
		danglingExit = true;
		danglingEntry = true;
	}

}
