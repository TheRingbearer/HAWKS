package org.apache.ode.bpel.compiler;

import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFragmentSequence;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FragmentSequenceGenerator extends SequenceGenerator {

	public OActivity newInstance(Activity src) {
		return new OFragmentSequence(_context.getOProcess(),
				_context.getCurrent());
	}
}
