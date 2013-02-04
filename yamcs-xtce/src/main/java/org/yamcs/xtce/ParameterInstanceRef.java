package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A reference to an instance of a Parameter.
 * Used when the value of a parameter is required for a calculation or as an index value.  
 * A positive value for instance is forward in time, a negative value for count is backward in time, 
 * a 0 value for count means use the current value of the parameter or the first value in a container.
 * @author nm
 *
 */
public class ParameterInstanceRef implements Serializable {
	private static final long serialVersionUID = 200906191236L;
	private Parameter parameter;
	
	private boolean useCalibratedValue=true;
	//int instance=0; TODO
	
	public ParameterInstanceRef(Parameter para) {
		this.parameter=para;
	}
	
	public ParameterInstanceRef(Parameter para, boolean useCalibratedValue) {
		this.parameter=para;
		this.useCalibratedValue=useCalibratedValue;
	}

    public void setParameter(Parameter para) {
        this.parameter=para;
    }
    
	public ParameterInstanceRef(boolean useCalibratedValue) {
        this.useCalibratedValue=useCalibratedValue;
    }
	
	public Parameter getParameter() {
		return parameter;
	}
	
	public boolean useCalibratedValue() {
		return useCalibratedValue;
	}

}