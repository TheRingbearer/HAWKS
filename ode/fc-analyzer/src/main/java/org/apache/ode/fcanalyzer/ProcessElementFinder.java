package org.apache.ode.fcanalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OPartnerLink;
import org.apache.ode.bpel.o.OProcess;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OScope.CorrelationSet;
import org.apache.ode.bpel.o.OScope.Variable;

public class ProcessElementFinder {
	public static Variable findVariable(OActivity currentActivity, String varName){
		Variable result = null;
		while (currentActivity != null && result == null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				result = scope.getLocalVariable(varName);
			}
		}
		
		return result;
	}
	public static List<Variable> getVisibleVariables(OActivity currentActivity){
		HashSet<String> names = new HashSet<String>();
		ArrayList<Variable> vars = new ArrayList<Variable>();
		while (currentActivity != null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				for (Variable var: scope.variables.values()){
					if (!names.contains(var.name)){
						names.add(var.name);
						vars.add(var);
					}
				}	
			}
		}
		return vars;
	}
	
	public static List<CorrelationSet> getVisibleCorrelationSets(OActivity currentActivity){
		HashSet<String> names = new HashSet<String>();
		ArrayList<CorrelationSet> sets = new ArrayList<CorrelationSet>();
		while (currentActivity != null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				for (CorrelationSet set: scope.correlationSets.values()){
					if (!names.contains(set.name)){
						names.add(set.name);
						sets.add(set);
					}
				}
			}
		}
		return sets;
	}
	
	public static List<OPartnerLink> getVisiblePartnerLinks(OActivity currentActivity){
		HashSet<String> names = new HashSet<String>();
		ArrayList<OPartnerLink> links = new ArrayList<OPartnerLink>();
		while (currentActivity != null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				for (OPartnerLink link: scope.partnerLinks.values()){
					if (!names.contains(link.name)){
						names.add(link.name);
						links.add(link);
					}
				}
			}
		}
		return links;
	}
	
	public static OPartnerLink findPartnerLink(OActivity currentActivity, String partnerLinkName){
		OPartnerLink result = null;
		while (currentActivity != null && result == null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				result = scope.partnerLinks.get(partnerLinkName);
			}
		}
		
		return result;
	}
	public static CorrelationSet findCorrelationSet(OActivity currentActivity, String correlationSetName){
		CorrelationSet result = null;
		while (currentActivity != null && result == null){
			currentActivity = currentActivity.getParent();
			if (currentActivity instanceof OScope){
				OScope scope = (OScope) currentActivity;
				result = scope.correlationSets.get(correlationSetName);
			}
		}
		
		return result;
	}
	
	public static List<Variable> getVariablesToMap(OProcess process, int elementId){
		List<OScope.Variable> vars; 
		
		OBase temp = process.getChild(elementId);
		if (temp != null && temp instanceof OFragmentEntry){
			vars = ((OFragmentEntry)temp).variablesToMap;
		} else if (temp != null && temp instanceof OFragmentRegion) {
			vars = ((OFragmentRegion)temp).variablesToMap;
		} else {
			vars = new ArrayList<OScope.Variable>();
		}
		return vars;
	}
	
	public static List<OPartnerLink> getPartnerLinksToMap(OProcess process, int elementId){
		List<OPartnerLink> links; 
		
		OBase temp = process.getChild(elementId);
		if (temp != null && temp instanceof OFragmentEntry){
			links = ((OFragmentEntry)temp).partnerLinksToMap;
		} else if (temp != null && temp instanceof OFragmentRegion) {
			links = ((OFragmentRegion)temp).partnerLinksToMap;
		} else {
			links = new ArrayList<OPartnerLink>();
		}
		return links;
	}
	
	public static List<CorrelationSet> getCorrelationSetsToMap(OProcess process, int elementId){
		List<CorrelationSet> sets; 
		
		OBase temp = process.getChild(elementId);
		if (temp != null && temp instanceof OFragmentEntry){
			sets = ((OFragmentEntry)temp).correlationSetsToMap;
		} else if (temp != null && temp instanceof OFragmentRegion) {
			sets = ((OFragmentRegion)temp).correlationSetsToMap;
		} else {
			sets = new ArrayList<CorrelationSet>();
		}
		return sets;
	}
}
