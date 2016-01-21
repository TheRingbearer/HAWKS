package org.apache.ode.bpel.runtime;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.ode.bpel.engine.BpelProcess;
import org.apache.ode.bpel.o.*;
import org.apache.ode.bpel.o.OScope.Variable;
import org.apache.ode.bpel.o.OSwitch.OCase;

/**
 * Maps with an xpath-expression to an element in the org.apache.ode.bpel.o
 * 
 * @author Bo Ning
 * 
 */
public class XPathParser {

	static OBase actualElement;

	public OBase handleXPath(String xpath, BpelProcess process) {

		String[] values = xpath.split("/");
		String priorActivity = null;
		actualElement = process.getOProcess();
		for (String value : values) {
			int number = value.indexOf("[");
			String activity = value;
			String activitySuffix = null;
			if (number != -1) {
				activity = value.substring(0, number);
				activitySuffix = value
						.substring(number + 1, value.length() - 1);
			}
			if (activity.compareTo("") != 0) {
				if (activity.compareTo("process") != 0) {
					try {
						actualElement = getCorrectElement(actualElement,
								priorActivity, activity, activitySuffix);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				priorActivity = new String(activity);
			}
		}

		return actualElement;
	}

	public OBase getCorrectElement(OBase element, String priorActivity,
			String activity, String activitySuffix) {
		OBase result = element;

		int activityNumber = 1;
		if (activitySuffix != null && activitySuffix.compareTo("") != 0) {
			activityNumber = new Integer(activitySuffix);
		}
		int i = 0;
		// all elements with subsidiary activities
		if (priorActivity.compareTo("sequence") == 0) {

			for (OActivity childActivity : ((OSequence) element).sequence) {

				if (activity.compareTo("assign") == 0) {
					if (childActivity instanceof OAssign) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("receive") == 0) {
					if (childActivity instanceof OPickReceive) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("reply") == 0) {
					if (childActivity instanceof OReply) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("invoke") == 0) {
					// the activity invoke will be converted to OScope
					if (childActivity instanceof OScope) {
						OScope implicitScope = (OScope)childActivity;
						if (implicitScope.implicitScope && implicitScope.activity instanceof OInvoke) {

							// an invoke-activity is an implicit scope with an invoke-activity as child
							i++;
							if (i == activityNumber) {
								result = childActivity;
								break;
							}
						}
					}
				}

				else if (activity.compareTo("if") == 0) {
					if (childActivity instanceof OSwitch) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("scope") == 0) {
					if (childActivity instanceof OScope) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("wait") == 0) {
					if (childActivity instanceof OWait) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("empty") == 0) {
					if (childActivity instanceof OEmpty) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}

				else if (activity.compareTo("exit") == 0) {
					if (childActivity instanceof OTerminate) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("flow") == 0) {
					if (childActivity instanceof OFlow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("forEach") == 0) {
					if (childActivity instanceof OForEach) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("pick") == 0) {
					if (childActivity instanceof OPickReceive) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("repeatUntil") == 0) {
					if (childActivity instanceof ORepeatUntil) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("rethrow") == 0) {
					if (childActivity instanceof ORethrow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("sequence") == 0) {
					if (childActivity instanceof OSequence) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("throw") == 0) {
					if (childActivity instanceof OThrow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}
				// OValidate can not be found in the package of
				// org.apache.ode.bpel.o
				// because this activity is not implemented by ODE. Processes
				// containing such activities will cause a compilation failure.
				/*
				 * else if(activity.compareTo("validate")==0){ if (childActivity
				 * instanceof OValidate){ i++; if(i == activityNumber){ result =
				 * (Element)childActivity; break; } } }
				 */
				else if (activity.compareTo("while") == 0) {
					if (childActivity instanceof OWhile) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}
			}

		} else if (priorActivity.compareTo("flow") == 0) {
			for (OActivity childActivity : ((OFlow) element).parallelActivities) {
				if (activity.compareTo("assign") == 0) {
					if (childActivity instanceof OAssign) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("receive") == 0) {
					if (childActivity instanceof OPickReceive) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("reply") == 0) {
					if (childActivity instanceof OReply) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("invoke") == 0) {
					if (childActivity instanceof OInvoke) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}

				else if (activity.compareTo("if") == 0) {
					if (childActivity instanceof OSwitch) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("scope") == 0) {
					if (childActivity instanceof OScope) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("wait") == 0) {
					if (childActivity instanceof OWait) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("empty") == 0) {
					if (childActivity instanceof OEmpty) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}

				else if (activity.compareTo("exit") == 0) {
					if (childActivity instanceof OTerminate) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("flow") == 0) {
					if (childActivity instanceof OFlow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("forEach") == 0) {
					if (childActivity instanceof OForEach) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("pick") == 0) {
					if (childActivity instanceof OPickReceive) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("repeatUntil") == 0) {
					if (childActivity instanceof ORepeatUntil) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("rethrow") == 0) {
					if (childActivity instanceof ORethrow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("sequence") == 0) {
					if (childActivity instanceof OSequence) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				} else if (activity.compareTo("throw") == 0) {
					if (childActivity instanceof OThrow) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}
				// OValidate can not be found in the package of
				// org.apache.ode.bpel.o
				// because this activity is not implemented by ODE. Processes
				// containing such activities will cause a compilation failure.
				/*
				 * else if(activity.compareTo("validate")==0){ if (childActivity
				 * instanceof Validate){ i++; if(i == activityNumber){ result =
				 * childActivity; break; } } }
				 */
				else if (activity.compareTo("while") == 0) {
					if (childActivity instanceof OWhile) {
						i++;
						if (i == activityNumber) {
							result = childActivity;
							break;
						}
					}
				}
			}
		} else if (priorActivity.compareTo("process") == 0) {

			/*
			 * if (activity.compareTo("variables") == 0){
			 * 
			 * result = (OScope.Variable)((OProcess)element).procesScope; } else
			 */
			if (activity.compareTo("faultHandlers") == 0) {
				result = ((OProcess) element).procesScope.faultHandler;
			} else if (activity.compareTo("eventHandlers") == 0) {
				result = ((OProcess) element).procesScope.eventHandler;
			} else {
				result = ((OProcess) element).procesScope.activity;
			}
		}

		else if (priorActivity.compareTo("variables") == 0) {
			for (OScope.Variable variable : ((OScope) element).variables
					.values()) {
				i++;
				if (i == activityNumber) {
					result = variable;

					break;
				}
			}
		}

		else if (priorActivity.compareTo("faultHandlers") == 0) {
			if (activity.compareTo("catchAll") == 0) {
				result = ((OCatch) element).activity;
			} else if (activity.compareTo("catch") == 0) {
				for (OCatch variable : ((OFaultHandler) element).catchBlocks) {
					i++;
					if (i == activityNumber) {
						result = variable.activity;
						break;
					}
				}
			}

		} else if (priorActivity.compareTo("catchAll") == 0) {
			result = ((OCatch) element).activity;
		} else if (priorActivity.compareTo("catch") == 0) {
			result = ((OCatch) element).activity;
		}

		else if (priorActivity.compareTo("if") == 0) {
			if (activity.compareTo("elseif") == 0) {
				for (OCase variable : ((OSwitch) element).getCases()) {
					i++;
					if (i == activityNumber) {
						result = variable;
						break;
					}
				}

			} else if (activity.compareTo("else") == 0) {
				result = (OCase) ((OSwitch) element).getCases();
			} else {
				result = (OCase) ((OSwitch) element).getCases();
			}
		} else if (priorActivity.compareTo("scope") == 0) {
			if (activity.compareTo("variables") == 0) {

				result = (OScope.Variable) element;
			} else if (activity.compareTo("faultHandlers") == 0) {
				result = ((OScope) element).faultHandler;
			} else {
				result = ((OScope) element).activity;
			}
		}

		else if (priorActivity.compareTo("else") == 0) {
			result = (OSwitch) element;
		}

		return result;

	}

}
