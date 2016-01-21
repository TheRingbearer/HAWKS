package krawczls.workflowEngineManagement;

import org.apache.camel.builder.RouteBuilder;

public class WorkflowEngineRoute
extends RouteBuilder {
    public void configure() {
        this.from("activemq:queue:de.unistuttgart.rep").routeId("WorkflowEngineMessageRoute").processRef("workflowEngineProcessor");
    }
}