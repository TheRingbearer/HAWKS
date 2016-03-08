# HAWKS
HAWKS: A System for Highly Available Workflow Executions

HAWKS ensures high availability of workflow executions by using replication, while ensuring that the outcome is the same as in a non-replicated execution of this workflow. The implemention builds on the open-source workflow execution engine Apache ODE. 



The HAWKS system

ODE: Apache ODE with Synchronization Unit in /ode/bpel-runtime/src/main/java/org/apache/ode/bpel/extensions/sync

HAWKSController: The HAWKS Controller, which is responsbile for routing messages between multiple Apache ODEs participating in a replicated workflow execution



Example files

Processes: Exemplary processes

Producer and Deployer: Example files for showing how to deploy workflow models and trigger workflow executions using HAWKS