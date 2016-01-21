package org.apache.ode.bpel.compiler;

import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFragmentFlow;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentFlowGenerator extends FlowGenerator {
	public OActivity newInstance(Activity src) {
		return new OFragmentFlow(_context.getOProcess(), _context.getCurrent());
	}
}
