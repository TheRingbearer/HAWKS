package org.apache.ode.renderer;

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.ode.bpel.o.OActivity;

public class FlowLineContainer implements DiagramElement {
	private Vector<FlowLine> flowLines;
	private Set<OActivity> localActivities;
	private HashSet<OActivity> preparedActivities;
	private ActivityToRendererMapper mapper;
	private int insets = 10;

	public FlowLineContainer(Set<OActivity> initialActivities,
			Set<OActivity> localActivities,
			HashSet<OActivity> preparedActivities, ActivityToRendererMapper mapper) {
		this.mapper = mapper;
		this.localActivities = localActivities;
		this.preparedActivities = preparedActivities; 
		
		flowLines = new Vector<FlowLine>();
		for (OActivity activity : initialActivities) {
			FlowLine line = new FlowLine(activity, localActivities, preparedActivities, mapper);
			flowLines.add(line);
		}
	}
	
	public void addLine(FlowLine line){
		flowLines.add(line);
	}

	
	public void prepare(Graphics g) {
		for (FlowLine line : flowLines) {
			line.prepare(g);
		}
	}

	
	public Dimension getDimension() {
		int totalWidth = insets * flowLines.size();
		int maxHeight = 0;
		for (FlowLine line : flowLines) {
			Dimension lineDim = line.getDimension();
			totalWidth += lineDim.width;
			if (maxHeight < lineDim.height) {
				maxHeight = lineDim.height;
			}
		}
		return new Dimension(totalWidth, maxHeight);
	}

	
	public void setCoordinates(int leftTopX, int leftTopY) {
		int currentX = leftTopX + insets;
		for (FlowLine line : flowLines) {
			line.setCoordinates(currentX, leftTopY + insets);
			currentX = currentX + insets + line.getDimension().width;
		}
	}

	
	public void render(Graphics g) {
		for (FlowLine line : flowLines) {
			line.render(g);
		}
	}
}
