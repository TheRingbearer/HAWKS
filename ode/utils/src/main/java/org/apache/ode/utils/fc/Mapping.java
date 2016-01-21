package org.apache.ode.utils.fc;

import java.io.Serializable;

public class Mapping implements Serializable {
	public enum ElementType {
		VARIABLE, PARTNER_LINK, CORRELATION_SET
	};

	private ElementType type;
	private String fromVar;
	private String toVar;

	public Mapping(ElementType type, String fromVar, String toVar) {
		this.type = type;
		this.fromVar = fromVar;
		this.toVar = toVar;
	}

	public String getFromVar() {
		return fromVar;
	}

	public String getToVar() {
		return toVar;
	}

	public ElementType getType() {
		return type;
	}
}
