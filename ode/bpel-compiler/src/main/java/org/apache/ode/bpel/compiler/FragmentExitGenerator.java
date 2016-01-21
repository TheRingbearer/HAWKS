package org.apache.ode.bpel.compiler;

import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFragmentExit;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentExitGenerator extends DefaultActivityGenerator {

	public void compile(OActivity output, Activity src) {
		OFragmentExit fragmentExit = (OFragmentExit) output;
	}

	public OActivity newInstance(Activity src) {
		return new OFragmentExit(_context.getOProcess(), _context.getCurrent());
	}

}
