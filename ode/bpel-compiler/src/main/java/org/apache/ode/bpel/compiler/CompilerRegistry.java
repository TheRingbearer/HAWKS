package org.apache.ode.bpel.compiler;

import java.util.HashMap;

import javax.xml.namespace.QName;

/**
 * @author hahnml
 *
 */
public class CompilerRegistry {
	
	private static CompilerRegistry instance = null;
	
	private HashMap<QName, BpelCompiler> _compiler = new HashMap<QName, BpelCompiler>();
	
	public static CompilerRegistry getInstance() {
		if (instance == null) {
			instance = new CompilerRegistry();
		}
		
		return instance;
	}
	
	public void registerCompiler(BpelCompiler compiler) {
		_compiler.put(compiler.getOProcess().getQName(), compiler);
	}
	
	public BpelCompiler getCompiler(QName processName) {
		return _compiler.get(processName);
	}
}
