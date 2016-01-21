package org.apache.ode.utils.fc;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class HashCodeGenerator {
	private static int lastId = 0;

	public synchronized static int generate() {
		return ++lastId;
	}
}
