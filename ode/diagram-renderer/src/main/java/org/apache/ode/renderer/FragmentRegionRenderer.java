package org.apache.ode.renderer;

import java.awt.Graphics;

import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OFragmentRegion;

public class FragmentRegionRenderer extends ScopeRenderer{
	private OFragmentRegion region;
	public FragmentRegionRenderer(OBase element, ActivityToRendererMapper mapper) {
		super(element, mapper);
		defaultName = "fragmentRegion";
		region = (OFragmentRegion)element;
		if (!region.danglingExit && region.danglingEntry){
			this.setIcon(PictureLoader.load("fragmentRegionMappedIn.png"));
		} else if (!region.danglingExit && !region.danglingEntry){
			this.setIcon(PictureLoader.load("fragmentRegionMappedOut.png"));
		}
		child = region.child;
	}
	public void prepare(Graphics g){
		if (region.child != null && region.danglingExit && region.danglingEntry){
			this.setIcon(PictureLoader.load("fragmentRegionGlued.png"));
		} else if (!region.danglingExit && region.danglingEntry){
			this.setIcon(PictureLoader.load("fragmentRegionMappedIn.png"));
		} else if (!region.danglingExit && !region.danglingEntry){
			this.setIcon(PictureLoader.load("fragmentRegionMappedOut.png"));
		}
		super.prepare(g);
	}

}
