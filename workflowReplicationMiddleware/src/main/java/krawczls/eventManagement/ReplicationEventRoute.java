package krawczls.eventManagement;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

public class ReplicationEventRoute
extends RouteBuilder {
    public void configure() throws Exception {
        this.from("activemq:topic:org.apache.ode.events?disableReplyTo=true").routeId("ReplicationEventRoute1").multicast().to(new String[]{"activemq:topic:de.unistuttgart.rep.events", "direct:events"});
        this.from("direct:events").routeId("ReplicationEventRoute2").beanRef("eventMessageConsumer", "onMessage");
    }
}