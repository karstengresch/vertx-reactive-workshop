package com.redhat.consulting.vertx.data;

import java.io.Serializable;
import java.util.List;

/**
 * Devices message used for registration process data object
 *  
 * @author dsancho
 *
 */
public class Devices implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String id; 
	
	private List<Device> devices;
	
	public Devices(String id, List<Device> devices) {
		super();
		this.id = id;
		this.devices = devices;
	}
	
	public Devices() {
		super();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<Device> getDevices() {
		return devices;
	}

	public void setDevices(List<Device> devices) {
		this.devices = devices;
	}
}