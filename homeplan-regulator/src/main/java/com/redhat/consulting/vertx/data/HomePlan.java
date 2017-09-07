package com.redhat.consulting.vertx.data;

import java.io.Serializable;
import java.util.List;

/**
 * HomePlan data object
 *  
 * @author stkousso
 *
 */
public class HomePlan implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String id;
	
	private List<SensorLocation> sensorLocations;
	
	
	public HomePlan(String id, List<SensorLocation> sensorLocations) {
		this.id = id;
		this.sensorLocations = sensorLocations;
	}
	
	public HomePlan() {
		super();
	}

	public List<SensorLocation> getSensorLocations() {
		return sensorLocations;
	}

	public void setSensorLocations(List<SensorLocation> sensorLocations) {
		this.sensorLocations = sensorLocations;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}