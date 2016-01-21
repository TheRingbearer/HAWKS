package org.apache.ode.dao.fc.jpa;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "ODE_ELEMENT_MAPPING")
@NamedQueries({
		@NamedQuery(name = ElementMappingDAOImpl.GET_DATA, query = "select g.pk.elementId, g.mappingData from ElementMappingDAOImpl as g where g.pk.instanceId = :instanceId and g.pk.activityId = :activityId"),
		@NamedQuery(name = ElementMappingDAOImpl.REMOVE_MAPPINGS, query = "delete from ElementMappingDAOImpl as g where g.pk.instanceId = :instanceId and g.pk.activityId = :activityId") })
public class ElementMappingDAOImpl {
	public static final String GET_DATA = "GET_MAPPING_DATA";
	public static final String REMOVE_MAPPINGS = "REMOVE_MAPPINGS";

	@EmbeddedId
	private ElementMappingPK pk;

	@Column(name = "MAPPING_DATA")
	private String mappingData;

	public ElementMappingDAOImpl() {

	}

	public ElementMappingDAOImpl(Long instanceId, int elementId,
			int variableId, String data) {
		pk = new ElementMappingPK(instanceId, elementId, variableId);
		this.mappingData = data;
	}

}
