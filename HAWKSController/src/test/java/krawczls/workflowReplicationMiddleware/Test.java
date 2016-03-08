package krawczls.workflowReplicationMiddleware;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Test {
	public static void main(String[] args) {
		HashMap<String, Set<Integer>> strToIntSet = new HashMap<String, Set<Integer>>();
		HashSet<Integer> intSet = new HashSet<Integer>();
		intSet.add(1);
		strToIntSet.put("hallo", intSet);
		printSet(strToIntSet.get("hallo"));
		Set<Integer> newSet = strToIntSet.get("hallo");
		newSet.add(5);
		printSet(strToIntSet.get("hallo"));
		intSet.add(6);
		printSet(strToIntSet.get("hallo"));
	}
	
	public static void printSet(Set<Integer> intSet) {
		System.out.println("Set: ");
		for (Integer i : intSet) {
			System.out.println(i);
		}
	}
}
