package org.apache.ode.bpel.compiler.bom;

import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentEntryActivity extends Activity {

	public FragmentEntryActivity(Element el) {
		super(el);
	}

	public List<VariableToMap> getVariablesToMap() {
		BpelObject vars = getFirstChild(ElementsToMap.class);
		if (vars == null)
			return Collections.emptyList();
		return vars.getChildren(VariableToMap.class);
	}

	public List<PartnerLinkToMap> getPartnerLinksToMap() {
		BpelObject vars = getFirstChild(ElementsToMap.class);
		if (vars == null)
			return Collections.emptyList();
		return vars.getChildren(PartnerLinkToMap.class);
	}

	public List<CorrelationSetToMap> getCorrelationSetsToMap() {
		BpelObject vars = getFirstChild(ElementsToMap.class);
		if (vars == null)
			return Collections.emptyList();
		return vars.getChildren(CorrelationSetToMap.class);
	}
}
