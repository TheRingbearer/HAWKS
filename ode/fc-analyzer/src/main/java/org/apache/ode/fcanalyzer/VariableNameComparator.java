package org.apache.ode.fcanalyzer;

import java.util.Comparator;

import org.apache.ode.bpel.o.OScope.Variable;
/**
 * 
 * @author Alex Hummel
 *
 */
public class VariableNameComparator implements Comparator<Variable>{

	public int compare(Variable var1, Variable var2) {
		return var1.name.compareTo(var2.name);
	}



}
