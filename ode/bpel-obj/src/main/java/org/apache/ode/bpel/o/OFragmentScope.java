package org.apache.ode.bpel.o;

/**
 * 
 * @author Alex Hummel
 * 
 */
public class OFragmentScope extends OScope {

	public OFragmentScope(OProcess owner, OActivity parent) {
		super(owner, parent);
	}

	public String toString() {
		return "{OFragmentScope '" + name + "' id=" + getId() + "}";
	}

	// Hack to prevent collisions in hashCode() when it will be glued into flow
	// regin or sequence
	public OFragmentScope clone(OProcess owner, OActivity parent) {
		OFragmentScope scope = new OFragmentScope(owner, parent);
		scope.activity = this.activity;
		scope.atomicScope = this.atomicScope;
		scope.compensatable.addAll(this.compensatable);
		scope.compensationHandler = this.compensationHandler;
		scope.correlationSets.putAll(this.correlationSets);
		scope.debugInfo = this.debugInfo;
		scope.eventHandler = this.eventHandler;
		scope.failureHandling = this.failureHandling;
		scope.faultHandler = this.faultHandler;
		scope.implicitScope = this.implicitScope;
		scope.incomingLinks.addAll(this.incomingLinks);
		scope.joinCondition = this.joinCondition;
		scope.name = this.name;
		scope.nested.addAll(this.nested);
		scope.outgoingLinks.addAll(this.outgoingLinks);
		scope.partnerLinks.putAll(this.partnerLinks);
		scope.processScope = this.processScope;
		scope.sourceLinks.addAll(this.sourceLinks);
		scope.suppressJoinFailure = this.suppressJoinFailure;
		scope.targetLinks.addAll(this.targetLinks);
		scope.terminationHandler = this.terminationHandler;
		scope.variableRd.addAll(this.variableRd);
		scope.variables.putAll(this.variables);
		scope.variableWr.addAll(this.variableWr);
		scope.activity.setParent(scope);
		scope.setXpath(this.getXpath());
		this.getOwner().procesScope.activity = scope;
		return scope;
	}
}
