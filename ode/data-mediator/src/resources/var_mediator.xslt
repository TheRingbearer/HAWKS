<xsl:stylesheet version='1.0'
	xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:optypes="http://opal.simtech.ustutt.de/processes/types"
	xmlns:wstypes="http://resinf.simtech.ustutt.de/ws/resmgr/types"
	xmlns:omtypes="http://opal.simtech.ustutt.de/ws/opalmgr/types">
	
	<xsl:variable name="personType" as="xs:QName"
		select="QName('http://person','person')" />
	<xsl:variable name="addressType" as="xs:QName"
		select="QName('http://person','address')" />
	<xsl:variable name="cityType" as="xs:QName"
		select="QName('http://person','city')" />
	<xsl:variable name="integerType" as="xs:QName"
		select="QName('http://www.w3.org/2001/XMLSchema','integer')" />
	<xsl:variable name="booleanType" as="xs:QName"
		select="QName('http://www.w3.org/2001/XMLSchema','boolean')" />	

	<xsl:variable name="callback" as="xs:QName"
		select="QName('http://resinf.simtech.ustutt.de/ws/resmgr/types','callbackBaseURL')" />		
	<xsl:variable name="snapshots" as="xs:QName"
		select="QName('http://resinf.simtech.ustutt.de/ws/resmgr/types','snapshotCount')" />	
	<xsl:variable name="ctxID" as="xs:QName"
		select="QName('http://resinf.simtech.ustutt.de/ws/resmgr/types','ctxID')" />	

	<xsl:variable name="simID" as="xs:QName"
		select="QName('http://opal.simtech.ustutt.de/ws/opalmgr/types','SimIDType')" />	
		
	<xsl:variable name="opalMainRequest" as="xs:QName"
		select="QName('http://opal.simtech.ustutt.de/processes/types','opalMainProcRequest')" />	
		
	<xsl:variable name="ticketId" as="xs:QName"
		select="QName('http://resinf.simtech.ustutt.de/ws/resmgr/types','serviceTicketID')" />	
	
	<xsl:variable name="acquireServiceResponse" as="xs:QName"
		select="QName('http://resinf.simtech.ustutt.de/ws/resmgr/types','acquireServiceResponse')" />	

		
	<xsl:param name="from" as="xs:QName" />
	<xsl:param name="to" as="xs:QName" />
	
	<xsl:output method="xml" />

	<xsl:template match="/">
		
		<xsl:if test="$from = $booleanType and $to = $integerType">
			<xsl:element name="temporary-simple-type-wrapper">
			<xsl:if test="xs:boolean(/)">
				<xsl:text>1</xsl:text>
			</xsl:if>
			<xsl:if test="xs:boolean(/) != true()">
				<xsl:text>0</xsl:text>
			</xsl:if>
			
			</xsl:element>	
		</xsl:if>
		<xsl:if test="$from = $integerType and $to = $booleanType">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:if test="xs:integer(/) &gt; xs:integer(0)"><xsl:text>true</xsl:text></xsl:if>
				<xsl:if test="xs:integer(/) &lt; xs:integer(0) or xs:integer(/) = xs:integer(0)"><xsl:text>false</xsl:text></xsl:if>
			</xsl:element>	
		</xsl:if>

		<xsl:if test="$from = $integerType and $to = $booleanType">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:if test="xs:integer(/) &gt; xs:integer(0)"><xsl:text>true</xsl:text></xsl:if>
				<xsl:if test="xs:integer(/) &lt; xs:integer(0) or xs:integer(/) = xs:integer(0)"><xsl:text>false</xsl:text></xsl:if>
			</xsl:element>	
		</xsl:if>

		<xsl:if test="$from = $opalMainRequest and $to = $callback">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:value-of select="//parameters/optypes:opalMainProcRequest/optypes:callbackBaseURL"/>
			</xsl:element>	
		</xsl:if>
	
		<xsl:if test="$from = $opalMainRequest and $to = $snapshots">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:value-of select="//parameters/optypes:opalMainProcRequest/optypes:snapshotCount"/>
			</xsl:element>	
		</xsl:if>
		<xsl:if test="$from = $opalMainRequest and $to = $ctxID">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:value-of select="//parameters/optypes:opalMainProcRequest/optypes:ctxID"/>
			</xsl:element>	
		</xsl:if>

		<xsl:if test="$from = $opalMainRequest and $to = $simID">
			<xsl:element name="temporary-simple-type-wrapper">
				<xsl:value-of select="//parameters/optypes:opalMainProcRequest/optypes:simID"/>
			</xsl:element>	
		</xsl:if>		
		
		<xsl:if test="$from = $acquireServiceResponse and $to = $ticketId">
	
			<xsl:element name="message">
				<xsl:element name="parameters">
					<xsl:element name="wstypes:serviceTicketID">
						<xsl:value-of select="//parameters/wstypes:acquireServiceResponse/wstypes:serviceTicket/wstypes:serviceTicketID"/>
					</xsl:element>
				</xsl:element>
			</xsl:element>	
		</xsl:if>

	</xsl:template>


</xsl:stylesheet>
