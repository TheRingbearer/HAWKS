/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * @author Bo Ning
 */

package org.apache.ode.bpel.dao;

import java.util.Collection;
import java.util.Date;

public interface SnapshotDAO {

	/*
	 * Get the snapshot idendifier
	 * 
	 * @return snapshot identifier
	 */
	public Long getSnapshotID();

	/**
	 * Get the variables with the same name.
	 * 
	 * @return variable
	 */
	public SnapshotVariableDAO getVariable(String name);

	/*
	 * Get all the variables
	 */
	public Collection<SnapshotVariableDAO> getVariables();

	/*
	 * Get the Process Instance ID
	 */
	public Long getProcessinstanceID();

	/*
	 * Set the Process Instance ID
	 */
	// public void setProcessinstanceID(Long id);

	public SnapshotPartnerlinksDAO createSnapshotPartnerlink(int plinkModelId,
			String pLinkName, String myRole, String partnerRole);

	public SnapshotVariableDAO createSnapshotVariable(String name);

	public ProcessInstanceDAO getProcessInstanceDAO();

	public SnapshotPartnerlinksDAO getPartnerlink(int plinkModelId);

	public Collection<SnapshotPartnerlinksDAO> getPartnerLinks();

	public String getXpath();

	// @hahnml: Returns the version of the snapshot
	public Long getVersion();

	// @hahnml: Returns the timestamp when the snapshot was created
	public Date getCreationTime();
}
