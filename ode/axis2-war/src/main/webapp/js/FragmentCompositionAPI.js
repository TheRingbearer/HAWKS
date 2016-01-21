var baseURL;
if (location.host.indexOf('/') == -1 && location.protocol.indexOf('/') == -1) {
    baseURL = location.protocol + "//" + location.host + "/";
}else if(location.host.indexOf('/') != -1 && location.protocol.indexOf('/') == -1){
    baseURL = location.protocol + "//" + location.host;
}
var baseDirectoryName = location.pathname.substring(0,location.pathname.indexOf('/',1));
if(baseDirectoryName.indexOf('/') == 0){
    baseDirectoryName = baseDirectoryName.substring(1);
}
var address = baseURL + baseDirectoryName + "/processes/FragmentComposition";

var FragmentCompositionService = new WebService("FragmentCompositionPort");
FragmentCompositionService.getPossibleParentElements =

function getPossibleParentElements(instanceId){

    this._options = new Array();
    isAsync = (this.getPossibleParentElements.callback != null && typeof(this.getPossibleParentElements.callback) == 'function');
    request =
    '<q0:getFragmentContainers xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
    '</q0:getFragmentContainers>';
    if (isAsync) {
        try {
            this._call(
                    "getFragmentContainers",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getPossibleParentElements.callback, this.getPossibleParentElements.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getPossibleParentElements.onError(error);
        }
    } else {
        try {
            response = this._call("getFragmentContainers", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.
}
FragmentCompositionService.glue =

function glue(instanceId, elementId, newFragmentName){

    this._options = new Array();
    isAsync = (this.glue.callback != null && typeof(this.glue.callback) == 'function');
    request =
    '<q0:glue xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:fragmentFlowId>' + this._encodeXML(elementId) + '</q0:fragmentFlowId>' + 
    '<q0:newFragmentName>' + this._encodeXML(newFragmentName) + '</q0:newFragmentName>' + 
    '</q0:glue>';
	
    if (isAsync) {
        try {
            this._call(
                    "glue",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.glue.callback, this.glue.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.glue.onError(error);
        }
    } else {
        try {
            response = this._call("glue", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getDanglingEntries =

function getDanglingEntries(instanceId){

    this._options = new Array();
    isAsync = (this.getDanglingEntries.callback != null && typeof(this.getDanglingEntries.callback) == 'function');
    request =
    '<q0:getDanglingEntries xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'</q0:getDanglingEntries>';

    if (isAsync) {
        try {
            this._call(
                    "getDanglingEntries",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getDanglingEntries.callback, this.getDanglingEntries.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getDanglingEntries.onError(error);
        }
    } else {
        try {
            response = this._call("getDanglingEntries", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.getDanglingExits =

function getDanglingExits(instanceId){

    this._options = new Array();
    isAsync = (this.getDanglingExits.callback != null && typeof(this.getDanglingExits.callback) == 'function');
    request =
    '<q0:getDanglingExits xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'</q0:getDanglingExits>';

    if (isAsync) {
        try {
            this._call(
                    "getDanglingExits",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getDanglingExits.callback, this.getDanglingExits.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getDanglingExits.onError(error);
        }
    } else {
        try {
            response = this._call("getDanglingExits", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getIgnorableExits =

function getIgnorableExits(instanceId){

    this._options = new Array();
    isAsync = (this.getIgnorableExits.callback != null && typeof(this.getIgnorableExits.callback) == 'function');
    request =
    '<q0:getIgnorableExits xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'</q0:getIgnorableExits>';

    if (isAsync) {
        try {
            this._call(
                    "getIgnorableExits",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getIgnorableExits.callback, this.getIgnorableExits.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getIgnorableExits.onError(error);
        }
    } else {
        try {
            response = this._call("getIgnorableExits", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getIgnorableEntries =

function getIgnorableEntries(instanceId){

    this._options = new Array();
    isAsync = (this.getIgnorableEntries.callback != null && typeof(this.getIgnorableEntries.callback) == 'function');
    request =
    '<q0:getIgnorableEntries xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'</q0:getIgnorableEntries>';

    if (isAsync) {
        try {
            this._call(
                    "getIgnorableEntries",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getIgnorableEntries.callback, this.getIgnorableEntries.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getIgnorableEntries.onError(error);
        }
    } else {
        try {
            response = this._call("getIgnorableEntries", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getAvailableVariables =

function getAvailableVariables(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getAvailableVariables.callback != null && typeof(this.getAvailableVariables.callback) == 'function');
    request =
    '<q0:getAvailableVariables xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:entryOrExitId>' + this._encodeXML(elementId) + '</q0:entryOrExitId> '
	'</q0:getAvailableVariables>';

	
    if (isAsync) {
        try {
            this._call(
                    "getAvailableVariables",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getAvailableVariables.callback, this.getAvailableVariables.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getAvailableVariables.onError(error);
        }
    } else {
        try {
            response = this._call("getAvailableVariables", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.getAvailablePartnerLinks =

function getAvailablePartnerLinks(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getAvailablePartnerLinks.callback != null && typeof(this.getAvailablePartnerLinks.callback) == 'function');
    request =
    '<q0:getAvailablePartnerLinks xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:elementId>' + this._encodeXML(elementId) + '</q0:elementId> '
	'</q0:getAvailablePartnerLinks>';

	
    if (isAsync) {
        try {
            this._call(
                    "getAvailablePartnerLinks",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getAvailablePartnerLinks.callback, this.getAvailablePartnerLinks.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getAvailablePartnerLinks.onError(error);
        }
    } else {
        try {
            response = this._call("getAvailablePartnerLinks", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getAvailableCorrelationSets =

function getAvailableCorrelationSets(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getAvailableCorrelationSets.callback != null && typeof(this.getAvailableCorrelationSets.callback) == 'function');
    request =
    '<q0:getAvailableCorrelationSets xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:elementId>' + this._encodeXML(elementId) + '</q0:elementId> '
	'</q0:getAvailableCorrelationSets>';

	
    if (isAsync) {
        try {
            this._call(
                    "getAvailableCorrelationSets",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getAvailableCorrelationSets.callback, this.getAvailableCorrelationSets.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getAvailableCorrelationSets.onError(error);
        }
    } else {
        try {
            response = this._call("getAvailableCorrelationSets", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}




FragmentCompositionService.getVariablesToMap =

function getVariablesToMap(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getVariablesToMap.callback != null && typeof(this.getVariablesToMap.callback) == 'function');
    request =
    '<q0:getVariablesToMap xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:elementId>' + this._encodeXML(elementId) + '</q0:elementId> ' +
	'</q0:getVariablesToMap>';

    if (isAsync) {
        try {
            this._call(
                    "getVariablesToMap",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getVariablesToMap.callback, this.getVariablesToMap.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getVariablesToMap.onError(error);
        }
    } else {
        try {
            response = this._call("getVariablesToMap", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.getPartnerLinksToMap =

function getPartnerLinksToMap(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getPartnerLinksToMap.callback != null && typeof(this.getPartnerLinksToMap.callback) == 'function');
    request =
    '<q0:getPartnerLinksToMap xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:elementId>' + this._encodeXML(elementId) + '</q0:elementId> ' +
	'</q0:getPartnerLinksToMap>';

    if (isAsync) {
        try {
            this._call(
                    "getPartnerLinksToMap",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getPartnerLinksToMap.callback, this.getPartnerLinksToMap.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getPartnerLinksToMap.onError(error);
        }
    } else {
        try {
            response = this._call("getPartnerLinksToMap", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}

FragmentCompositionService.getCorrelationSetsToMap =

function getCorrelationSetsToMap(instanceId, elementId){

    this._options = new Array();
    isAsync = (this.getCorrelationSetsToMap.callback != null && typeof(this.getCorrelationSetsToMap.callback) == 'function');
    request =
    '<q0:getCorrelationSetsToMap xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:elementId>' + this._encodeXML(elementId) + '</q0:elementId> ' +
	'</q0:getCorrelationSetsToMap>';

    if (isAsync) {
        try {
            this._call(
                    "getCorrelationSetsToMap",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getCorrelationSetsToMap.callback, this.getCorrelationSetsToMap.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getCorrelationSetsToMap.onError(error);
        }
    } else {
        try {
            response = this._call("getCorrelationSetsToMap", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}



FragmentCompositionService.wireAndMap =

function wireAndMap(instanceId, fragmentExitId, fragmentEntryId, mappings){

    this._options = new Array();
    isAsync = (this.wireAndMap.callback != null && typeof(this.wireAndMap.callback) == 'function');
    var request =
    '<q0:wireAndMap xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:fragmentExitId>' + this._encodeXML(fragmentExitId) + '</q0:fragmentExitId>' +
	'<q0:fragmentEntryId>' + this._encodeXML(fragmentEntryId) + '</q0:fragmentEntryId>' +
	'<q0:variableMapping><q0:mapping-list>';
	
	for (var i = 0; i < mappings.length; i++){
		var mapping = mappings[i];
		request = request + '<q0:mapping-info>' +
		'<q0:elementType>' + this._encodeXML(mapping.type) +'</q0:elementType>' +
		'<q0:fromElementName>' + this._encodeXML(mapping.fromElement) + '</q0:fromElementName>' + 
		'<q0:toElementName>' + this._encodeXML(mapping.toElement) + '</q0:toElementName>' + 
		'</q0:mapping-info>';

		}

	request = request + '<q0:mapping-list></q0:variableMapping></q0:wireAndMap>';
	
	
    if (isAsync) {
        try {
            this._call(
                    "wireAndMap",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.wireAndMap.callback, this.wireAndMap.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.wireAndMap.onError(error);
        }
    } else {
        try {
            response = this._call("wireAndMap", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.getProcessImage =

function getProcessImage(instanceId){

    this._options = new Array();
    isAsync = (this.getProcessImage.callback != null && typeof(this.getProcessImage.callback) == 'function');
    var request =
    '<q0:getProcessImage xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'</q0:getProcessImage>';
	
	
    if (isAsync) {
        try {
            this._call(
                    "getProcessImage",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.getProcessImage.callback, this.getProcessImage.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.getProcessImage.onError(error);
        }
    } else {
        try {
            response = this._call("getProcessImage", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.ignoreFragmentExit =

function ignoreFragmentExit(instanceId, fragmentExitId){

    this._options = new Array();
    isAsync = (this.ignoreFragmentExit.callback != null && typeof(this.ignoreFragmentExit.callback) == 'function');
    var request =
    '<q0:ignoreFragmentExit xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:fragmentExitId>' + this._encodeXML(fragmentExitId) + '</q0:fragmentExitId>' + 
	'</q0:ignoreFragmentExit>';
	
	
    if (isAsync) {
        try {
            this._call(
                    "ignoreFragmentExit",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.ignoreFragmentExit.callback, this.ignoreFragmentExit.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.ignoreFragmentExit.onError(error);
        }
    } else {
        try {
            response = this._call("ignoreFragmentExit", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}


FragmentCompositionService.ignoreFragmentEntry =

function ignoreFragmentEntry(instanceId, fragmentEntryId){

    this._options = new Array();
    isAsync = (this.ignoreFragmentEntry.callback != null && typeof(this.ignoreFragmentEntry.callback) == 'function');
    var request =
    '<q0:ignoreFragmentEntry xmlns:q0="http://www.apache.org/ode/fcapi/">' +
	'<q0:instanceId>' + this._encodeXML(instanceId) + '</q0:instanceId>' + 
	'<q0:fragmentEntryId>' + this._encodeXML(fragmentEntryId) + '</q0:fragmentEntryId>' + 
	'</q0:ignoreFragmentEntry>';
	
	
    if (isAsync) {
        try {
            this._call(
                    "ignoreFragmentEntry",
                    request,
                    function(thisRequest, callbacks) {
                        if (thisRequest.error != null) {
                            callbacks[1](thisRequest.error);
                        } else {
                            response = thisRequest.responseXML;
                            if (response == null) {
                                resultValue = null;
                            } else {
                                resultValue = response.documentElement;
                            }
                            callbacks[0](resultValue);
                        }
                    },
                    new Array(this.ignoreFragmentEntry.callback, this.ignoreFragmentEntry.onError)
                    );
        } catch (e) {
            var error;
            if (WebServiceError.prototype.isPrototypeOf(e)) {
                error = e;
            } else if (typeof(e) == "string") {
                error = new WebServiceError(e, "Internal Error");
            } else {
                error = new WebServiceError(e.description, e.number, e.number);
            }
            this.ignoreFragmentEntry.onError(error);
        }
    } else {
        try {
            response = this._call("ignoreFragmentEntry", request);
            resultValue = response.documentElement;
            return resultValue;
        } catch (e) {
            if (typeof(e) == "string") throw(e);
            if (e.message) throw(e.message);
            throw (e.reason + e.detail);
        }
    }
    return null; // Suppress warnings when there is no return.

}



// WebService object.
function WebService(endpointName)
{
    this.readyState = 0;
    this.onreadystatechange = null;

    //public accessors for manually intervening in setting the address (e.g. supporting tcpmon)
    this.getAddress = function (endpointName)
    {
        return this._endpointDetails[endpointName].address;
    }

    this.setAddress = function (endpointName, address)
    {
        this._endpointDetails[endpointName].address = address;
    }

    // private helper functions
    this._getWSRequest = function()
    {
        var wsrequest;
        try {
            wsrequest = new WSRequest();
        } catch(e) {
            try {
                wsrequest = new ActiveXObject("WSRequest");
            } catch(e) {
                try {
                    wsrequest = new SOAPHttpRequest();
                    netscape.security.PrivilegeManager.enablePrivilege("UniversalXPConnect");
                } catch (e) {
                    throw new WebServiceError("WSRequest object not defined.", "WebService._getWSRequest() cannot instantiate WSRequest object.");
                }
            }
        }
        return wsrequest;
    }

    this._endpointDetails =
    {
        "FragmentCompositionPort": {
            "type" : "SOAP11",
            "address" : address,
            "action" : {
				"getAvailablePartnerLinks" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getAvailablePartnerLinks",
				"getAvailableCorrelationSets" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getAvailableCorrelationSets",
                "getAvailableVariables" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getAvailableVariables",
                "getDanglingExits" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getDanglingExits",
                "wireAndMap" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/wireAndMap",
				"getFragmentContainers" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getFragmentContainers",
				"glue" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/glue",
				"getDanglingEntries" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getDanglingEntries",
				"getVariablesToMap" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getVariablesToMap",
				"getPartnerLinksToMap" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getPartnerLinksToMap",
				"getCorrelationSetsToMap" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getCorrelationSetsToMap",
				"getProcessImage" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getProcessImage",
				"ignoreFragmentExit" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/ignoreFragmentExit",
				"ignoreFragmentEntry" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/ignoreFragmentEntry",
				"getIgnorableExits" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getIgnorableExits",
				"getIgnorableEntries" : "http://www.apache.org/ode/fcapi/FragmentCompositionPortType/getIgnorableEntries"
				
            }
        }
    };
    this.endpoint = endpointName;

    this._encodeXML = function (value) {
        var re;
        var str = value.toString();
        re = /&/g;
        str = str.replace(re, "&amp;");
        re = /</g;
        str = str.replace(re, "&lt;");
        return(str);
    };

    this._call = function (opName, reqContent, callback, userdata)
    {
        var details = this._endpointDetails[this.endpoint];
        if (details.type == 'SOAP12') this._options.useSOAP = 1.2;
        else if (details.type == 'SOAP11') this._options.useSOAP = 1.1;
        else if (details.type == 'HTTP') this._options.useSOAP = false;

        if (details.action != null) {
            this._options.useWSA = true;
            this._options.action = details.action[opName];
        } else if (details.soapaction != null) {
            this._options.useWSA = false;
            this._options.action = details.soapaction[opName];
        } else {
            this._options.useWSA = false;
            this._options.action = undefined;
        }

        if (details["httpmethod"] != null) {
            this._options.HTTPMethod = details.httpmethod[opName];
        } else {
            this._options.HTTPMethod = null;
        }

        if (details["httpinputSerialization"] != null) {
            this._options.HTTPInputSerialization = details.httpinputSerialization[opName];
        } else {
            this._options.HTTPInputSerialization = null;
        }

        if (details["httplocation"] != null) {
            this._options.HTTPLocation = details.httplocation[opName];
        } else {
            this._options.HTTPLocation = null;
        }

        if (details["httpignoreUncited"] != null) {
            this._options.HTTPLocationIgnoreUncited = details.httpignoreUncited[opName];
        } else {
            this._options.HTTPLocationIgnoreUncited = null;
        }

        if (details["httpqueryParameterSeparator"] != null) {
            this._options.HTTPQueryParameterSeparator = details.httpqueryParameterSeparator[opName];
        } else {
            this._options.HTTPQueryParameterSeparator = null;
        }

        var isAsync = (typeof(callback) == 'function');

        var thisRequest = this._getWSRequest();
        if (isAsync) {
            thisRequest._userdata = userdata;
            thisRequest.onreadystatechange =
            function() {
                if (thisRequest.readyState == 4) {
                    callback(thisRequest, userdata);
                }
            }
        }
        thisRequest.open(this._options, details.address, isAsync);
        thisRequest.send(reqContent);
        if (isAsync) {
            return "";
        } else {
            try {
                var resultContent = thisRequest.responseText;
                if (resultContent == "") {
                    throw new WebServiceError("No response", "WebService._call() did not recieve a response to a synchronous request.");
                }
                var resultXML = thisRequest.responseXML;
            } catch (e) {
                throw new WebServiceError(e);
            }
            return resultXML;
        }
    }
}
WebService.visible = false;

// library function for dynamically converting an element with js:type annotation to a Javascript type.
convertJSType.visible = false;
function convertJSType(element, isWrapped) {
    if (element == null) return "";
    var extractedValue = WSRequest.util._stringValue(element);
    var resultValue, i;
    var type = element.getAttribute("js:type");
    if (type == null) {
        type = "xml";
    } else {
        type = type.toString();
    }
    switch (type) {
        case "string":
            return extractedValue;
            break;
        case "number":
            return parseFloat(extractedValue);
            break;
        case "boolean":
            return extractedValue == "true" || extractedValue == "1";
            break;
        case "date":
            return xs_dateTime_to_date(extractedValue);
            break;
        case "array":
            resultValue = new Array();
            for (i = 0; i < element.childNodes.length; i++) {
                resultValue = resultValue.concat(convertJSType(element.childNodes[i]));
            }
            return(resultValue);
            break;
        case "object":
            resultValue = new Object();
            for (i = 0; i < element.childNodes.length; i++) {
                resultValue[element.childNodes[i].tagName] = convertJSType(element.childNodes[i]);
            }
            return(resultValue);
            break;
        case "xmllist":
            return element.childNodes;
            break;
        case "xml":
        default:
            if (isWrapped == true)
                return element.firstChild;
            else return element;
            break;
    }
}

// library function for parsing xs:date, xs:time, and xs:dateTime types into Date objects.
function xs_dateTime_to_date(dateTime)
{
    var buffer = dateTime;
    var p = 0; // pointer to current parse location in buffer.

    var era, year, month, day, hour, minute, second, millisecond;

    // parse date, if there is one.
    if (buffer.substr(p, 1) == '-')
    {
        era = -1;
        p++;
    } else {
        era = 1;
    }

    if (buffer.charAt(p + 2) != ':')
    {
        year = era * buffer.substr(p, 4);
        p += 5;
        month = buffer.substr(p, 2);
        p += 3;
        day = buffer.substr(p, 2);
        p += 3;
    } else {
        year = 1970;
        month = 1;
        day = 1;
    }

    // parse time, if there is one
    if (buffer.charAt(p) != '+' && buffer.charAt(p) != '-')
    {
        hour = buffer.substr(p, 2);
        p += 3;
        minute = buffer.substr(p, 2);
        p += 3;
        second = buffer.substr(p, 2);
        p += 2;
        if (buffer.charAt(p) == '.')
        {
            millisecond = parseFloat(buffer.substr(p)) * 1000;
            // Note that JS fractional seconds are significant to 3 places - xs:time is significant to more -
            // though implementations are only required to carry 3 places.
            p++;
            while (buffer.charCodeAt(p) >= 48 && buffer.charCodeAt(p) <= 57) p++;
        } else {
            millisecond = 0;
        }
    } else {
        hour = 0;
        minute = 0;
        second = 0;
        millisecond = 0;
    }

    var tzhour = 0;
    var tzminute = 0;
    // parse time zone
    if (buffer.charAt(p) != 'Z' && buffer.charAt(p) != '') {
        var sign = (buffer.charAt(p) == '-' ? -1 : +1);
        p++;
        tzhour = sign * buffer.substr(p, 2);
        p += 3;
        tzminute = sign * buffer.substr(p, 2);
    }

    var thisDate = new Date();
    thisDate.setUTCFullYear(year);
    thisDate.setUTCMonth(month - 1);
    thisDate.setUTCDate(day);
    thisDate.setUTCHours(hour);
    thisDate.setUTCMinutes(minute);
    thisDate.setUTCSeconds(second);
    thisDate.setUTCMilliseconds(millisecond);
    thisDate.setUTCHours(thisDate.getUTCHours() - tzhour);
    thisDate.setUTCMinutes(thisDate.getUTCMinutes() - tzminute);
    return thisDate;
}
xs_dateTime_to_date.visible = false;

function scheme(url) {
    var s = url.substring(0, url.indexOf(':'));
    return s;
}
scheme.visible = false;

function domain(url) {
    var d = url.substring(url.indexOf('://') + 3, url.indexOf('/', url.indexOf('://') + 3));
    return d;
}
domain.visible = false;

function domainNoPort(url) {
    var d = domain(url);
    if (d.indexOf(":") >= 0)
        d = d.substring(0, d.indexOf(':'));
    return d;
}
domainNoPort.visible = false;

try {
    var secureEndpoint = "";
    var pageUrl = document.URL;
    var pageScheme = scheme(pageUrl);
    // only attempt fixup if we're from an http/https domain ('file:' works fine on IE without fixup)
    if (pageScheme == "http" || pageScheme == "https") {
        var pageDomain = domain(pageUrl);
        var pageDomainNoPort = domainNoPort(pageUrl);
        var endpoints = FragmentCompositionService._endpointDetails;
        // loop through each available endpoint
        for (var i in endpoints) {
            var address = endpoints[i].address;
            // if we're in a secure domain, set the endpoint to the first secure endpoint we come across
            if (secureEndpoint == "" && pageScheme == "https" && scheme(address) == "https") {
                secureEndpoint = i;
                FragmentCompositionService.endpoint = secureEndpoint;
            }
            // if we're in a known localhost domain, rewrite the endpoint domain so that we won't get
            //  a bogus xss violation
            if (pageDomainNoPort.indexOf('localhost') == 0 || pageDomainNoPort.indexOf('127.0.0.1') == 0) {
                endpoints[i].address = address.replace(domainNoPort(address), pageDomainNoPort);
            }
        }
    }
} catch (e) {
}

