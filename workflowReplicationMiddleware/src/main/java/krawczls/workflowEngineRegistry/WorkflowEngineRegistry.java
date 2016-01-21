package krawczls.workflowEngineRegistry;

import java.util.ArrayList;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import krawczls.messages.Process;
import krawczls.workflowEngineRegistry.WorkflowEngine;


public class WorkflowEngineRegistry {
    public WorkflowEngine getOrCreateWorkflowEngine(String workflowEngineIp) {
        System.out.println("Check engines.");
        WorkflowEngine workflowEngine = new WorkflowEngine();
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        em.getTransaction().commit();
        em.close();
        emf.close();
        if (workflowEngine == null) {
            workflowEngine = this.createWorkflowEngine(workflowEngineIp);
        }
        return workflowEngine;
    }

    public WorkflowEngine createWorkflowEngine(String workflowEngineIp) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        WorkflowEngine workflowEngine = new WorkflowEngine();
        em.getTransaction().begin();
        workflowEngine.setWorkflowEngineQueue("de.unistuttgart.rep." + workflowEngineIp);
        workflowEngine.setWorkflowEngineIp(workflowEngineIp);
        em.persist(workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
        System.out.println("Created new workflow engine " + workflowEngineIp + " in registry.");
        return workflowEngine;
    }

    public void deleteWorkflowEngine(String workflowEngineIp) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        WorkflowEngine workflowEngine = (WorkflowEngine)em.find(WorkflowEngine.class, workflowEngineIp);
        em.remove(workflowEngine);
        em.close();
        emf.close();
        System.out.println("Deleted workflow engine " + workflowEngineIp + " in registry.");
    }

    public String getEndpointUrl(String workflowEngineIp) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        em.close();
        emf.close();
        return workflowEngine.getWorkflowEngineQueue();
    }

    public int checkIfDeployed(String workflowEngineIp, String processName) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine engine = em.find(WorkflowEngine.class, workflowEngineIp);
        em.getTransaction().commit();
        em.close();
        emf.close();
        if (engine == null) {
            return -1;
        }
        System.out.println("processsize: " + engine.getProcesses().size());
        int i = 0;
        while (i < engine.getProcesses().size()) {
            System.out.println("processname: " + engine.getProcesses().get(i).getProcessName());
            if (processName.equals(engine.getProcesses().get(i).getProcessName())) {
                System.out.println(engine.getProcesses().get(i).getProcessFileName());
                return 1;
            }
            ++i;
        }
        return 0;
    }

    public void addNewProcess(String workflowEngineIp, Process process) {
        WorkflowEngine workflowEngine = this.getOrCreateWorkflowEngine(workflowEngineIp);
        ArrayList list = new ArrayList();
        list = workflowEngine.getProcesses();
        list.add(process);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        workflowEngine.setProcesses(list);
        em.merge((Object)workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
    }

    public void addNewActiveProcessInstance(String workflowEngineIp, String replicatedWorkflowID) {
        WorkflowEngine workflowEngine = this.getOrCreateWorkflowEngine(workflowEngineIp);
        ArrayList list = workflowEngine.getActiveProcessInstances();
        if (list == null) {
            list = new ArrayList();
        }
        list.add(replicatedWorkflowID);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        workflowEngine.setActiveProcessInstances(list);
        em.merge((Object)workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
    }

    public void addNewRole(String workflowEngineIp, int role) {
        WorkflowEngine workflowEngine = this.getOrCreateWorkflowEngine(workflowEngineIp);
        ArrayList list = workflowEngine.getRoleInActiveProcesses();
        if (list == null) {
            list = new ArrayList();
        }
        list.add(role);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        workflowEngine.setRoleInActiveProcesses(list);
        em.merge((Object)workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
    }

    public void deleteActiveProcessInstanceAndRole(String workflowEngineIp, String replicatedWorkflowID) {
        WorkflowEngine workflowEngine = this.getOrCreateWorkflowEngine(workflowEngineIp);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        ArrayList<String> list = workflowEngine.getActiveProcessInstances();
        int index = -1;
        int i = 0;
        while (i < list.size()) {
            if (replicatedWorkflowID.equals(list.get(i))) {
                index = i;
                list.remove(i);
            }
            ++i;
        }
        workflowEngine.setActiveProcessInstances(list);
        ArrayList<Integer> listRole = workflowEngine.getRoleInActiveProcesses();
        listRole.remove(index);
        workflowEngine.setRoleInActiveProcesses(listRole);
        em.merge((Object)workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
    }

    public void updateWorkflowEngineTimeOfLastHeartbeat(String workflowEngineIp) {
        WorkflowEngine workflowEngine = this.getOrCreateWorkflowEngine(workflowEngineIp);
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        workflowEngine.setTimeOfLastHeartbeat(new Date().getTime());
        em.merge((Object)workflowEngine);
        em.getTransaction().commit();
        em.close();
        emf.close();
        System.out.println("Updated heartbeat of workflow engine " + workflowEngineIp + " in registry.");
    }

    public ArrayList<WorkflowEngine> getAllActiveEngines() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        ArrayList<WorkflowEngine> list = new ArrayList<WorkflowEngine>();
        Query query = em.createQuery("from WorkflowEngine");
        for (Object o : query.getResultList()) {
            if (o instanceof WorkflowEngine) {
                list.add((WorkflowEngine)o);
                continue;
            }
            throw new Exception("Expected object to be an instance of WorkflowEngine. Received object: " + o);
        }
        ArrayList<WorkflowEngine> resultList = new ArrayList<WorkflowEngine>();
        int i = 0;
        while (i < list.size()) {
            if (((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat() + 60000 < new Date().getTime()) {
                System.out.println(String.valueOf(((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat()) + " + 60000 < " + new Date().getTime());
                em.remove((Object)((WorkflowEngine)list.get(i)));
            } else {
                resultList.add((WorkflowEngine)list.get(i));
            }
            ++i;
        }
        em.getTransaction().commit();
        em.close();
        emf.close();
        return resultList;
    }
}