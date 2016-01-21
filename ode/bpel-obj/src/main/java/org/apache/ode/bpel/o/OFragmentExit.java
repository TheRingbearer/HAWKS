package org.apache.ode.bpel.o;

import org.apache.ode.utils.fc.Mapping;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class OFragmentExit extends OActivity {
	public boolean danglingExit;

	public int fragmentEntryId;
	public Mapping[] mappings;
	public boolean ignoredExit;

	public OFragmentExit(OProcess owner, OActivity parent) {
		super(owner, parent);
		danglingExit = true;
	}

}
