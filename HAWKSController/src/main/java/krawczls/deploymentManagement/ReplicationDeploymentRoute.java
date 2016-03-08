package krawczls.deploymentManagement;

import org.apache.camel.builder.RouteBuilder;

public class ReplicationDeploymentRoute
extends RouteBuilder {
    public void configure() {
        this.from("activemq:queue:deploy").routeId("deploymentRoute").processRef("replicationDeploymentProcessor");
        this.from("activemq:queue:start").routeId("startRoute").processRef("replicationStartProcessor");
    }
}