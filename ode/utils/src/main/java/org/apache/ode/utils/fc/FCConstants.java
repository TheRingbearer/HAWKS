package org.apache.ode.utils.fc;

import javax.xml.namespace.QName;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class FCConstants {
	public static final String FC_TARGET_NAMESPACE = "http://www.apache.org/ode/fcapi/";
	public static final QName FC_SERVICE_NAME = new QName(FC_TARGET_NAMESPACE,
			"FragmentComposition");
	public static final String FC_PORT_NAME = "FragmentCompositionHttpSoap11Endpoint";

	public static final QName FC_PORT_TYPE_NAME = new QName(
			FC_TARGET_NAMESPACE, "FragmentCompositionPortType");
	public static final QName FC_INSTANCE_ID_FIELD = new QName(
			FC_TARGET_NAMESPACE, "instanceId");

	public static final QName FC_FRAGMENT_EXIT_FIELD = new QName(
			FC_TARGET_NAMESPACE, "fragmentExitName");
	public static final QName FC_FRAGMENT_ENTRY_FIELD = new QName(
			FC_TARGET_NAMESPACE, "fragmentEntryName");

	public static final QName FC_MAP_TO_VAR_FIELD = new QName(
			FC_TARGET_NAMESPACE, "mapToVar");
	public static final QName FC_MAP_FROM_VAR_FIELD = new QName(
			FC_TARGET_NAMESPACE, "mapFromVar");

	public static final QName FC_VARIABLE_MAPPING = new QName(
			FC_TARGET_NAMESPACE, "variableMapping");
	public static final QName FC_NEW_FRAGMENT_FIELD = new QName(
			FC_TARGET_NAMESPACE, "newFragmentName");

	public static final QName FC_REQUEST = new QName(FC_TARGET_NAMESPACE,
			"request");
}
