package org.apache.ode.renderer;

import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OForEach;
import org.apache.ode.bpel.o.ORepeatUntil;
import org.apache.ode.bpel.o.OWhile;

public class WhileRenderer extends ScopeRenderer{

	public WhileRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		
		if (element instanceof OWhile){
			child = ((OWhile)element).activity;
			defaultName = "While";
		} else if (element instanceof ORepeatUntil){
			child = ((ORepeatUntil)element).activity;
			defaultName = "Repeat until";
		} else if (element instanceof OForEach){
			child = ((OForEach)element).innerScope.activity;
			defaultName = "For each";
		}
		 
	}

}
