<xsl:stylesheet version='1.0'
	xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:fn="http://myfunctions" xmlns:ps="http://person" xmlns:ad="http://person">

	<xsl:variable name="counterGet" as="xs:QName"
		select="QName('http://example.com/bpel/counter','counter_get')" />
	<xsl:variable name="counterStart" as="xs:QName"
		select="QName('http://example.com/bpel/counter','counter_start')" />

	<xsl:variable name="firstProp" as="xs:QName"
		select="QName('http://example.com/bpel/counter','firstProp')" />

	<xsl:variable name="mainStart" as="xs:QName"
		select="QName('http://opal.simtech.ustutt.de/processes/main','OpalMainStart')" />
	<xsl:variable name="mainMC" as="xs:QName"
		select="QName('http://opal.simtech.ustutt.de/processes/main','OpalMC')" />		

	<xsl:variable name="mainMedia" as="xs:QName"
		select="QName('http://opal.simtech.ustutt.de/processes/main','OpalMedia')" />		


	<xsl:param name="fromProcess" as="xs:QName" />
	<xsl:param name="toProcess" as="xs:QName" />

	<xsl:param name="fromCSetName" as="xs:string" />
	<xsl:param name="toCSetName" as="xs:string" />


	<xsl:param name="fromScopeName" as="xs:string" />
	<xsl:param name="toScopeName" as="xs:string" />

	<xsl:output method="xml" />

	<xsl:template match="/CorrelationSet">
		<xsl:element name="CorrelationSet">
			<xsl:apply-templates select="property" />
		</xsl:element>
	</xsl:template>
	<xsl:template match="/CorrelationSet/property">
		
		<xsl:if	test="$fromCSetName = $toCSetName">
			<xsl:copy-of select="." />
		</xsl:if>
		
		
	</xsl:template>

	

</xsl:stylesheet>
