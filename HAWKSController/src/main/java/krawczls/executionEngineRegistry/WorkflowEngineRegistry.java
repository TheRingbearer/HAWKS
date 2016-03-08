package krawczls.executionEngineRegistry;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
//import javax.persistence.EntityManagerFactory;
//import javax.persistence.Persistence;
import javax.persistence.Query;

import constants.Constants;

import krawczls.deploymentManagement.ReplicationStartContext;
import krawczls.executionEngineRegistry.WorkflowEngine;
import krawczls.messages.Process;


public class WorkflowEngineRegistry {

    public void createWorkflowEngineIfItDoesNotExist(String workflowEngineIp) {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine workflowEngine = new WorkflowEngine();
        try {
        	workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        }
        catch(Exception e) {
        	e.printStackTrace();
        }
        if (workflowEngine == null) {
        	workflowEngine = new WorkflowEngine();
            workflowEngine.setWorkflowEngineIp(workflowEngineIp);
            //TODO check why this gives a null pointer
            //System.out.println("Setting time of first heartbeat.");
            //workflowEngine.setTimeOfFirstHeartbeat(new Date().getTime());
            em.persist(workflowEngine);
            //System.out.println("Created new workflow engine " + workflowEngineIp + " in registry.");
        }
        em.getTransaction().commit();
        em.close();
        //emf.close();
    }

    public void deleteWorkflowEngine(String workflowEngineIp) {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory((String)"workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        WorkflowEngine workflowEngine = (WorkflowEngine)em.find(WorkflowEngine.class, workflowEngineIp);
        em.remove(workflowEngine);
        em.close();
        //emf.close();
        //System.out.println("Deleted workflow engine " + workflowEngineIp + " in registry.");
    }

    public int checkIfDeployed(String workflowEngineIp, String processName) {
    	int deployed;
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine engine = em.find(WorkflowEngine.class, workflowEngineIp);
        if (engine == null) {
            deployed = -1;
        }
        else {
        	deployed = 0;
        	for(int i = 0; i < engine.getProcesses().size(); i++) {
        		if (processName.equals(engine.getProcesses().get(i).getProcessName())) {
        			deployed = 1;
            	}
            }
        }
        em.getTransaction().commit();
        em.close();
        //emf.close();
        return deployed;
    }

    public void addNewProcess(String workflowEngineIp, Process process) {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        ArrayList<Process> list = workflowEngine.getProcesses();
        list.add(process);
        workflowEngine.setProcesses(list);
        em.merge(workflowEngine);
        em.getTransaction().commit();
        em.close();
        //emf.close();
    }

    public void addNewActiveProcessInstanceRoleAndFinish(String workflowEngineIp, String replicatedWorkflowID, int role, boolean finish) {
        //Get from database
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        
        //add new process instance
        workflowEngine.addProcessInstance(replicatedWorkflowID, role);
        
        // write to database
        em.merge(workflowEngine);
        em.getTransaction().commit();
        em.close();
        //emf.close();
        if(Constants.DEBUG_LEVEL > 0) {
        	System.out.println("Add new active process instance for workflow engine " + workflowEngineIp);
        }
    }

    public boolean updateFinish(String workflowEngineIp, String replicatedWorkflowID, boolean finish, EntityManagerFactory emf) {
    	boolean alreadyFinished = false;
	    try {	
	        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
	        EntityManager em = emf.createEntityManager();
	        em.getTransaction().begin();
	        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
	        
	        if (workflowEngine.isCurrentlyExecutingWorkflow(replicatedWorkflowID)) {
	        	workflowEngine.updateFinishProcess(replicatedWorkflowID);
	        } else {
	        	alreadyFinished = true;
	        }
	        
	        em.merge(workflowEngine);
	        em.getTransaction().commit();
	        em.close();
	        //emf.close();
	        if(Constants.DEBUG_LEVEL > 0) {
	        	System.out.println("Updated finished flag of workflow " + replicatedWorkflowID + " in engine " + workflowEngineIp);
	        }
	    }
	    catch(Exception e) {
	    	e.printStackTrace();
	    }
	    return alreadyFinished;
    }
    
    
    /**
     * A function that checks whether the majority of workflow engines have finished a particular
     * workflow instance. If that is the case it returns the boolean value true, otherwise the value false.
     * 
     * @param replicatedWorkflowID
     * @param emf
     * @return
     * @throws Exception
     */
    public boolean checkIfMajorityFinished(String replicatedWorkflowID, EntityManagerFactory emf) throws Exception {
    	try {
	        EntityManager em = emf.createEntityManager();
	        em.getTransaction().begin();
	        ArrayList<WorkflowEngine> workflowEngines = getAllEnginesOfCertainProcess(replicatedWorkflowID, emf);
	        int majority = (workflowEngines.size()/2)+1;
	        
	        Iterator<WorkflowEngine> enginesIterator = workflowEngines.iterator();
	        while (majority > 0 && enginesIterator.hasNext()) { 
	        	if (enginesIterator.next().isProcessFinished(replicatedWorkflowID)) {
	        		majority--;
	        	}
	        }
	        em.getTransaction().commit();
	        em.close();
	        if (majority <= 0) {
	        	return true;
	        }
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return false;
    }
    
    /**
     * A function that checks whether all workflow engines have finished a particular workflow instance.
     * If that is the case it returns the boolean value true, otherwise the value false.
     * 
     * @param replicatedWorkflowID
     * @param emf
     * @return
     * @throws Exception
     */
    public boolean checkIfAllFinished(String replicatedWorkflowID, EntityManagerFactory emf) throws Exception {
    	boolean finished = true;
    	try {
	        EntityManager em = emf.createEntityManager();
	        em.getTransaction().begin();
	        ArrayList<WorkflowEngine> workflowEngines = getAllEnginesOfCertainProcess(replicatedWorkflowID, emf);
	        Iterator<WorkflowEngine> engineIterator = workflowEngines.iterator();
	        while (finished && engineIterator.hasNext()) {
	        	if (!engineIterator.next().isProcessFinished(replicatedWorkflowID)) {
	        		finished = false;
	        	}
	        }
	        em.getTransaction().commit();
	        em.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return finished;
    }
    
    /**
     * A procedure that deletes all traces of a particular workflow instance, from the registry entry of
     * a particular workflow engine.
     * 
     * @param workflowEngineIp
     * @param replicatedWorkflowID
     * @param emf
     */
    public void deleteActiveProcessInstanceRoleAndFinish(String workflowEngineIp, 
    		String replicatedWorkflowID, 
    		EntityManagerFactory emf) {
    	try {
	        EntityManager em = emf.createEntityManager();
	        em.getTransaction().begin();
	        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
	        
	        //remove active process instance
	        workflowEngine.finishProcess(replicatedWorkflowID);
	        
	        em.merge(workflowEngine);
	        em.getTransaction().commit();
	        em.close();
	        //emf.close();
	        if(Constants.DEBUG_LEVEL > 0) {
	        	System.out.println("Delete active process instance and role of workflow engine " + workflowEngineIp);
	        }
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }

    /**
     * A procedure that updates the heartbeat counter of a particular workflow engine in the registry.
     * If the heartbeat counter isn't updated for a particular amount of time, the engine is implicitly
     * marked for deletion. 
     * 
     * @param workflowEngineIp
     */
    public void updateWorkflowEngineTimeOfLastHeartbeat(String workflowEngineIp) {
    	createWorkflowEngineIfItDoesNotExist(workflowEngineIp);
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
        em.getTransaction().begin();
        WorkflowEngine workflowEngine = em.find(WorkflowEngine.class, workflowEngineIp);
        workflowEngine.setTimeOfLastHeartbeat(new Date().getTime());
        em.merge(workflowEngine);
        em.getTransaction().commit();
        em.close();
        //emf.close();
        if(Constants.DEBUG_LEVEL > 0) {
        	System.out.println("Updated heartbeat of workflow engine " + workflowEngineIp);
        }
    }

    
    public ArrayList<WorkflowEngine> getAllEnginesOfCertainProcess(String replicatedWorkflowID, EntityManagerFactory emf) throws Exception {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        ArrayList<WorkflowEngine> workflowEngines = new ArrayList<WorkflowEngine>();
        Query query = em.createQuery("from WorkflowEngine");
        for (Object o : query.getResultList()) {
            if (o instanceof WorkflowEngine) {
                workflowEngines.add((WorkflowEngine)o);
            } else {
            	throw new Exception("Expected object to be an instance of WorkflowEngine. Received object: " + o);
            }
        }
        
        ArrayList<WorkflowEngine> resultEngines = new ArrayList<WorkflowEngine>();
        for(WorkflowEngine currentEngine : workflowEngines) {
        	if (currentEngine.isCurrentlyExecutingWorkflow(replicatedWorkflowID)) {
        		resultEngines.add(currentEngine);
        	}
        }
        em.getTransaction().commit();
        em.close();
        //emf.close();
        return resultEngines;
    }
    
    public ArrayList<WorkflowEngine> getAllActiveEngines() throws Exception {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
        EntityManager em = ReplicationStartContext.emf.createEntityManager();
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
            if (((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat() + 10000000 < new Date().getTime()) {
                //System.out.println(String.valueOf(((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat()) + " + 10000000 < " + new Date().getTime());
                em.remove(((WorkflowEngine)list.get(i)));
            } 
            else {
                resultList.add((WorkflowEngine)list.get(i));
            }
            ++i;
        }
        em.getTransaction().commit();
        em.close();
        //emf.close();
        return resultList;
    }
    
    public ArrayList<WorkflowEngine> getAllActiveEngines(EntityManagerFactory emf) throws Exception {
        //EntityManagerFactory emf = Persistence.createEntityManagerFactory("workflowEngineRegistry");
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
            if (((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat() + 10000000 < new Date().getTime()) {
            	if(Constants.DEBUG_LEVEL > 0) {
                	System.out.println(String.valueOf(((WorkflowEngine)list.get(i)).getTimeOfLastHeartbeat()) + " + 10000000 < " + new Date().getTime());
            	}
                em.remove(((WorkflowEngine)list.get(i)));
            } else {
                resultList.add((WorkflowEngine)list.get(i));
            }
            ++i;
        }
        em.getTransaction().commit();
        em.close();
        //emf.close();
        return resultList;
    }
}