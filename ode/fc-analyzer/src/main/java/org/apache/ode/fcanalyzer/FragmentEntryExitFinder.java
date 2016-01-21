package org.apache.ode.fcanalyzer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentFlow;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OFragmentSequence;
import org.apache.ode.bpel.o.OProcess;
/**
 * 
 * @author Alex Hummel
 *
 */
public class FragmentEntryExitFinder {

	
	public static List<Integer> findNeededContainers(OProcess process){
		HashSet<Integer> result = new HashSet<Integer>(findElementsToGlue(process));
		
		List<OBase> children = process.getChildren();
		for (OBase element: children){
			if (element instanceof OFragmentRegion){
				OFragmentRegion region = (OFragmentRegion)element;
				if (region.danglingEntry || region.danglingExit){
					result.add(region.getId());
				}
			}
		}
		
		
		return new ArrayList<Integer>(result);
	}
	
	public static List<Integer> findElementsToGlue(OProcess process) {
		HashSet<Integer> elementIds = new HashSet<Integer>();
		List<OBase> children = process.getChildren();
		for (OBase activity: children){
			if (activity instanceof OFragmentRegion){
				OFragmentRegion region = (OFragmentRegion)activity;
				if (region.child == null){
					elementIds.add(region.getId());
				}
			} else if (activity instanceof OFragmentExit){
				OFragmentExit exit = (OFragmentExit) activity;
				if (exit.danglingExit){
					OActivity current = exit.getParent();
					if (current instanceof OFragmentSequence){
						OFragmentSequence sequence = (OFragmentSequence)current;
						if (sequence.sequence.get(sequence.sequence.size() - 1).equals(exit)){
							elementIds.add(exit.getParent().getId());	
						}
					} 
					while (current != null) {
						if (current instanceof OFragmentFlow) {
							elementIds.add(current.getId());
						}
						current = current.getParent();
					}
				}
			}
		}
		return new ArrayList<Integer>(elementIds);
	}
	public static List<Integer> findDanglingExits(OProcess process) {
		ArrayList<Integer> exits = new ArrayList<Integer>();
		
		List<OBase> children = process.getChildren();
		for (OBase child: children){
			if (child instanceof OFragmentExit) {
				OFragmentExit act = (OFragmentExit) child;
				if (act.danglingExit){
					exits.add(act.getId());
				}
			}
		}
		return exits;
	}
	public static List<Integer> findDanglingEntries(OProcess process) {
		ArrayList<Integer> entries = new ArrayList<Integer>();
		
		List<OBase> children = process.getChildren();
		for (OBase child: children){
			if (child instanceof OFragmentEntry) {
				OFragmentEntry act = (OFragmentEntry) child;
				if (act.danglingEntry){
					entries.add(act.getId());
				}
			}
		}
		return entries;
	}
	
	
	public static int getFragmentExitsCount(OProcess process){
		int counter = 0;
		List<OBase> children = process.getChildren();
		for (OBase element: children){
			if (element instanceof OFragmentExit){
				counter++;
			}
		}
		return counter;
	}

	public static int getFragmentRegionsCount(OProcess process) {
		int counter = 0;
		List<OBase> children = process.getChildren();
		for (OBase element: children){
			if (element instanceof OFragmentRegion){
				counter++;
			}
		}
		return counter;
	}
	
	
	public static List<Integer> findRegionsToMapForward(OProcess process) {
		ArrayList<Integer> regions = new ArrayList<Integer>();
		List<OBase> children = process.getChildren(); 
		for (OBase child: children){
			if (child instanceof OFragmentRegion) {
				OFragmentRegion region = (OFragmentRegion) child;
				if (region.child != null && region.danglingExit){
					regions.add(region.getId());
				}
			}	
		}
		return regions;
	}
	
	public static List<Integer> findRegionsToMapBack(OProcess process) {
		ArrayList<Integer> regions = new ArrayList<Integer>();
		List<OBase> children = process.getChildren(); 
		for (OBase child: children){
			if (child instanceof OFragmentRegion) {
				OFragmentRegion region = (OFragmentRegion) child;
				if (region.child != null && region.danglingEntry){
					regions.add(region.getId());
				}
			}
		}
		return regions;
	}
	

}
