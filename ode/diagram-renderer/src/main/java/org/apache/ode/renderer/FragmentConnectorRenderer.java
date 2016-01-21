package org.apache.ode.renderer;

import java.awt.Graphics;

import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;

public class FragmentConnectorRenderer extends SimpleActivityRenderer{

	public FragmentConnectorRenderer(OBase element,
			ActivityToRendererMapper mapper) {
		super(element, mapper);
	}
	public void prepare(Graphics g){
		
		if (element instanceof OFragmentExit){
			OFragmentExit exit = (OFragmentExit) element;
			if (exit.danglingExit){
				this.setIcon(PictureLoader.load("fragmentExit.png"));	
			} else {
				this.setIcon(PictureLoader.load("mappedExit.png"));
			}
			
		} else if (element instanceof OFragmentEntry){
			OFragmentEntry entry = (OFragmentEntry) element;
			if (entry.danglingEntry){
				this.setIcon(PictureLoader.load("fragmentEntry.png"));
			} else {
				this.setIcon(PictureLoader.load("mappedExit.png"));
			}
			
		}
		super.prepare(g);
	}

}
