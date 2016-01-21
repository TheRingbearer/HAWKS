package org.apache.ode.renderer;

import org.apache.ode.bpel.o.OAssign;
import org.apache.ode.bpel.o.OBase;
import org.apache.ode.bpel.o.OEmpty;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OForEach;
import org.apache.ode.bpel.o.OFragmentEntry;
import org.apache.ode.bpel.o.OFragmentExit;
import org.apache.ode.bpel.o.OFragmentFlow;
import org.apache.ode.bpel.o.OFragmentRegion;
import org.apache.ode.bpel.o.OFragmentScope;
import org.apache.ode.bpel.o.OFragmentSequence;
import org.apache.ode.bpel.o.OInvoke;
import org.apache.ode.bpel.o.OPickReceive;
import org.apache.ode.bpel.o.ORepeatUntil;
import org.apache.ode.bpel.o.OReply;
import org.apache.ode.bpel.o.OScope;
import org.apache.ode.bpel.o.OSequence;
import org.apache.ode.bpel.o.OSwitch;
import org.apache.ode.bpel.o.OTerminate;
import org.apache.ode.bpel.o.OThrow;
import org.apache.ode.bpel.o.OWait;
import org.apache.ode.bpel.o.OWhile;

public class ActivityRendererFactory {

	public static Renderer getRenderer(OBase activity, ActivityToRendererMapper mapper){
		Renderer result = null;
		if (activity instanceof OScope){
			result = new ScopeRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("scope.png"));
		} else if (activity instanceof OFragmentScope){
			result = new ScopeRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("scope.png"));
		} else if (activity instanceof OFragmentSequence){
			result = new SequenceRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("sequence.png"));
		} else if (activity instanceof OFragmentFlow){
			result = new FlowRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("flow.png"));
		} else if (activity instanceof OFragmentRegion){
			result = new FragmentRegionRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("fragmentRegionNotMapped.png"));
		} else if (activity instanceof OFlow){
			result = new FlowRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("flow.png"));
		} else if (activity instanceof OSequence){
			result = new SequenceRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("sequence.png"));
		} else if (activity instanceof OEmpty){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("empty.png"));
		} else if (activity instanceof OFragmentExit){
			result = new FragmentConnectorRenderer(activity, mapper);
		} else if (activity instanceof OFragmentEntry){
			result = new FragmentConnectorRenderer(activity, mapper);
		} else if (activity instanceof OPickReceive){
			OPickReceive act = (OPickReceive) activity;
			if (act.isReceiveActivity){
				result = new SimpleActivityRenderer(activity, mapper);
				result.setIcon(PictureLoader.load("receive.png"));
			} else {
				result = new PickRenderer(activity, mapper);
				result.setIcon(PictureLoader.load("pick.png"));
			}
		} else if (activity instanceof OReply){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("reply.png"));
		} else if (activity instanceof OInvoke){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("invoke.png"));
		} else if (activity instanceof OAssign){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("assign.png"));
		} else if (activity instanceof OTerminate){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("exit.png"));
		} else if (activity instanceof OWait){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("wait.png"));
		} else if (activity instanceof OSwitch){
			result = new IfRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("if.png"));
		} else if (activity instanceof OWhile){
			result = new WhileRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("while.png"));
		} else if (activity instanceof ORepeatUntil){
			result = new WhileRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("while.png"));
		} else if (activity instanceof OForEach){
			result = new WhileRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("forEach.png"));
		} else if (activity instanceof OThrow){
			result = new SimpleActivityRenderer(activity, mapper);
			result.setIcon(PictureLoader.load("throw.png"));
		} else {
			result = new SimpleActivityRenderer(activity, mapper);
		}
		if (result != null) {
			mapper.put(activity, result);
		}
		return result;
	}

	
}
