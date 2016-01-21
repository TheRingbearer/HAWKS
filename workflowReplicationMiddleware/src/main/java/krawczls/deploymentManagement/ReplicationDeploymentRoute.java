package krawczls.deploymentManagement;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

public class ReplicationDeploymentRoute
extends RouteBuilder {
    public void configure() {
        this.from("activemq:queue:deploy").routeId("deploymentRoute").processRef("replicationDeploymentProcessor");
        this.from("activemq:queue:start").routeId("startRoute").processRef("replicationStartProcessor");
    }
}