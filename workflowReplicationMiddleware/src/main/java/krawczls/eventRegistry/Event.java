package krawczls.eventRegistry;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Event {
    private String modelID;
    private String details;
    private String elementState;
    @Id
    private String elementXPath;

    public void setModelID(String modelID) {
        this.modelID = modelID;
    }

    public String getModelID() {
        return this.modelID;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getDetails() {
        return this.details;
    }

    public void setElementState(String elementState) {
        this.elementState = elementState;
    }

    public String getElementState() {
        return this.elementState;
    }

    public void setElementXPath(String elementXPath) {
        this.elementXPath = elementXPath;
    }

    public String getElementXPath() {
        return this.elementXPath;
    }
}