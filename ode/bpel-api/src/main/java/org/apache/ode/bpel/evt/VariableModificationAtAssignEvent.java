package org.apache.ode.bpel.evt;

/**
 * @author hahnml
 * 
 */
public class VariableModificationAtAssignEvent extends
		VariableModificationEvent {

	private static final long serialVersionUID = 6914372634587L;

	private int numberOfCopyStatement;

	public VariableModificationAtAssignEvent(String varName, String path,
			Long id, String scopeXpath, String varXpath, Long scopId, 
			int numberOfCopyStatement) {
		super(varName, path, id, scopeXpath, varXpath, scopId);
		this.setNumberOfCopyStatement(numberOfCopyStatement);
	}

	public void setNumberOfCopyStatement(int numberOfCopyStatement) {
		this.numberOfCopyStatement = numberOfCopyStatement;
	}

	public int getNumberOfCopyStatement() {
		return numberOfCopyStatement;
	}
}
