package org.apache.ode.mediator;

public class MediationException extends Exception{

	public MediationException(){
		super();
	}
	public MediationException(String message){
		super(message);
	}
	public MediationException(Exception e){
		super(e);
	}
}
