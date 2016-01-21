package org.apache.ode.fc.dao;

public class MappingInfo {
	private int variableId;
	private String mappingData;

	public MappingInfo(int variableId, String mappingData) {
		this.variableId = variableId;
		this.mappingData = mappingData;
	}

	public int getVariableId() {
		return variableId;
	}

	public String getMappingData() {
		return mappingData;
	}
}