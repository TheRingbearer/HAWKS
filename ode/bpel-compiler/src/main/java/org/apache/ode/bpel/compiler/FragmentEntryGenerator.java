package org.apache.ode.bpel.compiler;

import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.compiler.bom.CorrelationSetToMap;
import org.apache.ode.bpel.compiler.bom.FragmentEntryActivity;
import org.apache.ode.bpel.compiler.bom.PartnerLinkToMap;
import org.apache.ode.bpel.compiler.bom.VariableToMap;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.fcanalyzer.ProcessElementFinder;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentEntryGenerator extends DefaultActivityGenerator {

	public void compile(OActivity output, Activity src) {
		FragmentEntryActivity act = (FragmentEntryActivity) src;
		OFragmentEntry oFragmentEntry = (OFragmentEntry) output;

		for (VariableToMap varToMap : act.getVariablesToMap()) {
			Variable var = ProcessElementFinder.findVariable(oFragmentEntry,
					varToMap.getName());
			if (var != null) {
				oFragmentEntry.variablesToMap.add(var);
			}
		}
		for (PartnerLinkToMap toMap : act.getPartnerLinksToMap()) {
			OPartnerLink var = ProcessElementFinder.findPartnerLink(
					oFragmentEntry, toMap.getName());
			if (var != null) {
				oFragmentEntry.partnerLinksToMap.add(var);
			}
		}
		for (CorrelationSetToMap toMap : act.getCorrelationSetsToMap()) {
			CorrelationSet var = ProcessElementFinder.findCorrelationSet(
					oFragmentEntry, toMap.getName());
			if (var != null) {
				oFragmentEntry.correlationSetsToMap.add(var);
			}
		}
	}

	public OActivity newInstance(Activity src) {
		return new OFragmentEntry(_context.getOProcess(), _context.getCurrent());
	}

}
