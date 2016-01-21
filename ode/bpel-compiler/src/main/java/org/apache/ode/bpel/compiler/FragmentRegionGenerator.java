package org.apache.ode.bpel.compiler;

import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.compiler.bom.CorrelationSetToMap;
import org.apache.ode.bpel.compiler.bom.FragmentRegionActivity;
import org.apache.ode.bpel.compiler.bom.PartnerLinkToMap;
import org.apache.ode.bpel.compiler.bom.VariableToMap;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.fcanalyzer.ProcessElementFinder;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentRegionGenerator extends DefaultActivityGenerator {

	public void compile(OActivity output, Activity src) {
		FragmentRegionActivity act = (FragmentRegionActivity) src;
		OFragmentRegion oFragmentRegion = (OFragmentRegion) output;

		for (VariableToMap varToMap : act.getVariablesToMap()) {
			Variable var = ProcessElementFinder.findVariable(oFragmentRegion,
					varToMap.getName());
			if (var != null) {
				oFragmentRegion.variablesToMap.add(var);
			}
		}
		for (PartnerLinkToMap toMap : act.getPartnerLinksToMap()) {
			OPartnerLink var = ProcessElementFinder.findPartnerLink(
					oFragmentRegion, toMap.getName());
			if (var != null) {
				oFragmentRegion.partnerLinksToMap.add(var);
			}
		}
		for (CorrelationSetToMap toMap : act.getCorrelationSetsToMap()) {
			CorrelationSet var = ProcessElementFinder.findCorrelationSet(
					oFragmentRegion, toMap.getName());
			if (var != null) {
				oFragmentRegion.correlationSetsToMap.add(var);
			}
		}
	}

	public OActivity newInstance(Activity src) {
		return new OFragmentRegion(_context.getOProcess(),
				_context.getCurrent());
	}

}
