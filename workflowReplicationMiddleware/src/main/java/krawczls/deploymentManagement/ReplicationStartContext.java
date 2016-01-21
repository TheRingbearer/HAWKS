package krawczls.deploymentManagement;

import krawczls.deploymentManagement.ReplicationStartContext;
import javax.jms.Connection;
import javax.jms.Session;
import krawczls.deploymentManagement.ReplicationDeploymentProcessor;
import krawczls.deploymentManagement.ReplicationDeploymentRoute;
import krawczls.deploymentManagement.ReplicationStartContext;
import krawczls.deploymentManagement.ReplicationStartProcessor;
import krawczls.workflowEngineManagement.WorkflowEngineProcessor;
import krawczls.workflowEngineManagement.WorkflowEngineRoute;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.Registry;


public class ReplicationStartContext {
    public static void main(String[] args) throws Exception {
        System.out.println("Replication Context started");
        try {
            ReplicationStartContext.thread((Runnable)new ReplicationStartContextThread(), false);
            Thread.sleep(1000);
        }
        catch (Exception e) {
            System.out.println("Replication Context could not be started.");
        }
    }

    public static void thread(Runnable runnable, boolean daemon) {
        Thread brokerThread = new Thread(runnable);
        brokerThread.setDaemon(daemon);
        brokerThread.start();
    }
    
    public static class ReplicationStartContextThread
    implements Runnable {
        public void run() {
            ReplicationStartContext.ReplicationStartContextThread replicationStartContextThread = this;
            synchronized (replicationStartContextThread) {
                try {
                    SimpleRegistry registry = new SimpleRegistry();
                    registry.put("replicationDeploymentProcessor", new ReplicationDeploymentProcessor());
                    registry.put("replicationStartProcessor", new ReplicationStartProcessor());
                    registry.put("workflowEngineProcessor", new WorkflowEngineProcessor());
                    DefaultCamelContext context = new DefaultCamelContext(registry);
                    ActiveMQConnectionFactory connection_factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
                    Connection connection = connection_factory.createConnection();
                    connection.start();
                    Session session = connection.createSession(false, 1);
                    session.createQueue("deploy");
                    session.createQueue("start");
                    session.createQueue("de.unistuttgart.rep");
                    context.addRoutes(new ReplicationDeploymentRoute());
                    context.addRoutes(new WorkflowEngineRoute());
                    context.start();
                    System.out.println("context running");
                    boolean x = true;
                    while (x) {
                        this.wait(10000);
                    }
                    context.stop();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}