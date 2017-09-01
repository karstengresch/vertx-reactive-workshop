package com.redhat.consulting.vertx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import com.redhat.consulting.vertx.Constants.AppErrorCode;
import com.redhat.consulting.vertx.Constants.DeviceAction;
import com.redhat.consulting.vertx.Constants.DeviceState;
import com.redhat.consulting.vertx.Constants.DeviceType;
import com.redhat.consulting.vertx.Constants.DevicesPurpose;
import com.redhat.consulting.vertx.Constants.ErrorCode;
import com.redhat.consulting.vertx.data.Device;
import com.redhat.consulting.vertx.dto.AmbianceDTO;
import com.redhat.consulting.vertx.dto.DeviceDTO;
import com.redhat.consulting.vertx.dto.DeviceDataDTO;
import com.redhat.consulting.vertx.dto.HomePlanRegulationDTO;
import com.redhat.consulting.vertx.utils.TimeUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
//import io.vertx.rxjava.core.shareddata.Counter;
//import io.vertx.rxjava.ext.web.RoutingContext;


/**
 * 
 * @author stkousso
 * 
 * Handles
 * 
 * Device Registration
 * Receives message from {@code MainVerticle.DEVICE_REGISTRATION_EVENTS_ADDRESS} 
 * Message expected format
 * {
    id : KoussourisHouseHold, 
    "devices" : [
         { "type" : air-con, id : "kitchen-1},
         { "type" : shutters, id : "kitchen-1"},
         { "type" : air-con, id : "bedroom-1"},
         { "type" : shutters, id : "bedroom-1"},
         { "type" : air-con, id : "bedroom-2",}
         { "type" : shutters, id : "bedroom-2"},
         { "type" : air-con, id : "living-room-1"},
         { "type" : shutters, id : "living-room-1"}
     ]
}


 * Device ACTION
 * Receives message from {@code MainVerticle.DEVICE_ACTION_EVENTS_ADDRESS} 
 * Utilizes Header {@code MainVerticle.DEVICE_ACTION_HEADER} with possible values {@code MainVerticle.DEVICE_MANAGEMENT_ACTION} 
 * 		
 * Message expected format
 * {
     "housePlanId" : kousourisHousehold,
     "id" : "kitchen-1",
     "type" : "AIRCON",
     "action" : INCREASING,
     "state" : ON,
     "fromNumber" : 17,
     "toNumber" : 22,
     "timeStart" : null
   }
   
   and for deactivate the header "TURNOFF_DEVICE"
   {
     "housePlanId" : kousourisHousehold,
     "id" : "kitchen-1",
     "type" : "AIRCON",
     "action" : NONE,
     "state" : OFF,
     "fromNumber" : 0,
     "toNumber" : 0,
     "timeStart" : null
   }
   
   
    * Device DATA
 * Receives message from {@code MainVerticle.DEVICE_DATA_EVENTS_ADDRESS} 
* 		
 * Message expected format
 * { id : "koussourisHousehold", sensor : "bedroom-1"} 
  

 *
 */
public class MainVerticle extends AbstractVerticle {

	private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	
	@Override
	public void start() {
		logger.info("\n----------------------------------------------------------------------------\n STARTING DEVICE Management - MainVerticle \n----------------------------------------------------------------------------");
	
		registerDevices(Constants.DEVICE_REGISTRATION_EVENTS_ADDRESS);
		deviceAction(Constants.DEVICE_ACTION_EVENTS_ADDRESS);
		readDevice(Constants.DEVICE_DATA_EVENTS_ADDRESS);
		updateDevice(Constants.AMBIANCE_DATA_EVENTS_ADDRESS);
		
	}
	
	// **********************************************************************************
	// HANDLE DEVICE REGISTRATIONS 
	// Receive message from the address 'DEVICE_REGISTRATION_EVENTS_ADDRESS'
	// *********************************************************************************
	private void registerDevices(String deviceDataEventsAddress) {
		
		vertx.eventBus().<String>consumer(deviceDataEventsAddress, message -> {

			System.out.println("\n\n CONSUMING message from #"+deviceDataEventsAddress+ "(HANDLED BY VertX.EventLoop" + this.toString());

			// Check whether we have received a payload in the incoming message
			if (message.body().isEmpty()) {
				// SEND/REPLY example
				// message.reply(json.put("message", "hello"));
			} else {
								
				// We will receive it as JSON string, transform it to its class equivalent
				DeviceDTO devicesToRegister = Json.decodeValue(message.body(), DeviceDTO.class);
						
				// TODO - NEED TO CHANGE THIS TO PROCESS THE MESSAGE AS A STREAM
				String housePlanId = devicesToRegister.getId();
					List<Device> devices = devicesToRegister.getDevices();
					for (Device device : devices) {
						registerDevice(generatedDeviceKey(devicesToRegister.getId(), device.getId()), 
								new Device(housePlanId, device.getId(), device.getType(), device.getAction(), 
										device.getState(), generateRandomLocationSensorTemperature(), device.getLastUpdate()));
					}
					
					// ONLY RE-ACTIVATE FOR TESTING - HACKING
//					getDevice(generatedDeviceKey("kousourisHousehold","kitchen-1"), message);
//					getDevice(generatedDeviceKey("kousourisHousehold", "bedroom-1"), message);
			}
		});		
		
        //ONLY RE-ACTIVATE FOR TESTING - HACKING
//		System.out.println("\n\n SENDING MESSAGE to #" + MainVerticle.DEVICE_REGISTRATION_EVENTS_ADDRESS);   
//		vertx.eventBus().send(MainVerticle.DEVICE_REGISTRATION_EVENTS_ADDRESS, createRegistrationPayload());
	}
	



	// **********************************************************************************
	// HANDLE DEVICE ACTIONS 
	// Receive message from the Vert.X Event Bus address #device-action
	// ACTIONS INCREASING/DECREASING/TURNOFF
	// separate ACTIONS based on the 'header' of the message
	// *********************************************************************************
	private void deviceAction(String deviceActionEventsAddress) {

		vertx.eventBus().<String>consumer(deviceActionEventsAddress, message -> {
			System.out.println("\n\n CONSUMING message from #"+Constants.DEVICE_ACTION_EVENTS_ADDRESS+" (HANDLED BY VertX.EventLoop" + this.toString()+")");

			// Check whether we have received a payload in the incoming message
			if (message.body().isEmpty()) {
				logger.error(appErrorPrefix(AppErrorCode.MESSAGE_IS_EMPTY)+" Received Message on address #"+Constants.DEVICE_ACTION_EVENTS_ADDRESS+" is empty");
				// SEND/REPLY example
				// message.reply(json.put("message", "hello"));
			} else {
				//DEVICE_MANAGEMENT_ACTION action = DEVICE_MANAGEMENT_ACTION.valueOf(message.headers().get(DEVICE_ACTION_HEADER));
				DeviceAction action = DeviceAction.valueOf(message.headers().get(Constants.DEVICE_ACTION_HEADER));
				HomePlanRegulationDTO deviceActionable = Json.decodeValue(message.body(), HomePlanRegulationDTO.class);
				
				logger.info("<---------------------------------------------------->");
				logger.info(Json.encodePrettily(deviceActionable));
				logger.info("<---------------------------------------------------->");

				Device device;

				switch (action) {
				case INCREASING:
				case DECREASING:
					logger.info("DEVICE: "+ generatedDeviceKey(deviceActionable)+ " is TURNED ON for ACTION: "+ action +" Applied");

					device = new Device(deviceActionable.getHousePlanId(), deviceActionable.getSensor(), DeviceType.AIRCON, action, DeviceState.ON, 0, TimeUtils.timeInMillisNow());

					//updateDevice(device);
					updateDevice(device, new Function<Map<String, Device>, Device>() {
						/*
						 * CASE 2: Function to copy devices content between actioned device and registered during an UPDATE
						 * as a result of homeplan-regulator #device-action event
						 * Affects state, action, lastupdate
						 */
						public Device apply(Map<String, Device> deviceMap) { // The devices to copy from/to
							 Device actionable = deviceMap.get(DevicesPurpose.ACTIONABLE);   
							 Device registered = deviceMap.get(DevicesPurpose.REGISTERED);   

							 logger.info("Applying DEVICE Copying Function for deviceAction.INCREASING/DECREASING");
							 registered.setState(actionable.getState());
							 registered.setAction(actionable.getAction());
							 registered.setLastUpdate(actionable.getLastUpdate());
							 
							// The updated device to be saved in the m
							 return registered;
						 }
					});


					break;
				case TURNOFF:
					logger.info("DEVICE: "+ generatedDeviceKey(deviceActionable)+ " is TURNED OFF");

					device = new Device(deviceActionable.getHousePlanId(), deviceActionable.getSensor(), DeviceType.AIRCON, action, DeviceState.OFF, generateRandomLocationSensorTemperature(), 0);

					//updateDevice(device);
					updateDevice(device, new Function<Map<String, Device>, Device>() {
						/*
						 * CASE 3: Function to copy devices content between actioned device and registered during an UPDATE
						 * as a result of homeplan-regulator #device-action event
						 * Affects all as resetting
						 * @return
						 */
						public Device apply(Map<String, Device> deviceMap) { // The devices to copy from/to
							 Device actionable = deviceMap.get(DevicesPurpose.ACTIONABLE);   
							 Device registered = deviceMap.get(DevicesPurpose.REGISTERED);   

							 logger.info("Applying DEVICE Copying Function for deviceAction.TURNOFF");

							 registered = actionable;
							 
							// The updated device to be saved in the m
							 return registered;
						 }
					});
					break;
				default:
					message.fail(ErrorCode.DEVICE_MANAGEMENT_ACTIONS_BAD_ACTION.getErrorCode(), "Bad action: " + action);
				}

			}
		});
	}	

	private void readDevice(String deviceDataEventsAddress) {
		vertx.eventBus().<String>consumer(deviceDataEventsAddress, message -> {

			logger.info("\n\n CONSUMING message from #"+deviceDataEventsAddress+" (HANDLED BY VerX.EventLoop" + this.toString());

			// Check whether we have received a payload in the incoming message
			if (message.body().isEmpty()) {
				// SEND/REPLY example
				// message.reply(json.put("message", "hello"));
			} else {
								
				// We will receive it as JSON string, transform it to its class equivalent
				DeviceDataDTO deviceDataRequested = Json.decodeValue(message.body(), DeviceDataDTO.class);
				
				logger.info(deviceDataRequested);
							
				getDevice(generatedDeviceKey(deviceDataRequested.getHousePlanId(), deviceDataRequested.getSensor()), message);			}
		});		
		
        //ONLY RE-ACTIVATE FOR TESTING - HACKING
//		System.out.println("\n\n SENDING MESSAGE to #" + MainVerticle.DEVICE_DATA_EVENTS_ADDRESS);
//		
//		vertx.eventBus().send(MainVerticle.DEVICE_REGISTRATION_EVENTS_ADDRESS, createRegistrationPayload());
//		
//		
//		DeliveryOptions options = new DeliveryOptions();
//
//	    
//	    /* *********  Test withaction header - for increase */
//		options.addHeader(DEVICE_ACTION_HEADER, DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE.toString());
//	    vertx.eventBus().send(MainVerticle.DEVICE_ACTION_EVENTS_ADDRESS, 
//	    		              createDeviceDummyActionPayload(DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE, DEVICE_ACTION.INCREASING, 17, 23),
//	    		              options);
//		
//		DeviceDataDTO deviceDetails = new DeviceDataDTO("kousourisHousehold", "bedroom-1");
//		
//		vertx.eventBus().send(MainVerticle.DEVICE_DATA_EVENTS_ADDRESS, Json.encodePrettily(deviceDetails));
	}
	
	//	private void updateDevice(Device deviceActionable) {
	//	SharedData sd = vertx.sharedData();
	//	sd.<String, Device>getClusterWideMap(DEVICES_MAP, res -> {
	//		if (res.succeeded()) {
	//			// SUCCEEDED to find the DEVICES SHARED MAP
	//		
	//			// GETTING THE DEVICE
	//			res.result().get(generatedDeviceKey(deviceActionable), ar -> {
	//				if (ar.succeeded()) {
	//					
	//					System.out.println("DEVICE RETRIEVED FROM SHARED MAP --> "+ar.result());
	//					
	//					// HERE IT SHOULD HAVE BEEN RETRIEVED based on the key
	//					System.out.println("RETRIEVED DEVICE FROM SHARED MAP: "+ (res.result() != null ? generatedDeviceKey(ar.result()) : "NONE FOUND") +" AS : "+Json.encodePrettily(ar.result()));
	//					
	//					// Check result matches one updating
	//					if (ar.result() != null && ar.result().equals(deviceActionable)){
	//						
	//						// updating
	//						res.result().put(generatedDeviceKey(deviceActionable), deviceActionable, ar2 -> {
	//							
	//							if (ar2.succeeded()) {
	//								// HERE IT SHOULD HAVE BEEN UPDADED on the MAP
	//								System.out.println("UPDATED DEVICE : "+deviceActionable.getId() +" to : "+Json.encodePrettily(deviceActionable));
	//							} else {
	//
	//								// HERE IT SHOULD HAVE failed to BE Updated on the MAP
	//								// TODO - Consider Vert.X error
	//							}
	//						});
	//					} else if (ar.result().getActionSequence() > deviceActionable.getActionSequence()) {
	//						// WARNING: Messages out of Sync
	//						System.out.println(appErrorPrefix(APP_ERROR_CODES.MESSAGE_OUT_OF_SYNC)+" UPDATE on DEVICE : "+generatedDeviceKey(deviceActionable) +" with Action Sequence "+deviceActionable.getActionSequence()+ "will not be applied as Shared Map already contains action sequence" + ar.result().getActionSequence());
	//					} else {
	//						// Trying to update a device for which there is no key registered in the Shared Map
	//						System.out.println(appErrorPrefix(APP_ERROR_CODES.NON_REGISTERED_DEVICE)+" CANNOT MATCH DEVICE with ID "+generatedDeviceKey(deviceActionable)+" with a registered Device on Shared Map data");
	//					}
	//				} else {
	//					// HERE IT SHOULD HAVE failed to BE RETREIVED from the MAP
	//					// TODO - Consider Vert.X error
	//				}
	//			});
	//		} else {
	//			// FAILED to find the MAP
	//			// TODO - Consider Vert.X error
	//		}		
	//	});
	//}

	private void updateDevice(String ambianceDataEventsAddress) {
		vertx.eventBus().<String>consumer(ambianceDataEventsAddress, message -> {

			System.out.println("\n\n CONSUMING message from #"+ambianceDataEventsAddress+ "(HANDLED BY VertX.EventLoop" + this.toString());
			AmbianceDTO ambianceData = Json.decodeValue(message.body(), AmbianceDTO.class);

			logger.info("Device Updated on Ambiance Data PUBLISH : "+ambianceData);

			Device device = new Device(ambianceData.getHousePlanId(), ambianceData.getSensorLocation().getId(), DeviceType.AIRCON, DeviceAction.NONE, DeviceState.OFF, 0, 0);

			updateDevice(device, new Function<Map<String, Device>, Device>() {
				/*
				 * CASE 1: Function to copy devices content between actioned device and registered during an UPDATE
				 * as a result of sensor-generator #ambiance-data publish
				 * Affects lastUpdate, Temperatu
				 * @return
				 */
				public Device apply(Map<String, Device> deviceMap) { // The devices to copy from/to
					Device actionable = deviceMap.get(DevicesPurpose.ACTIONABLE);   
					Device registered = deviceMap.get(DevicesPurpose.REGISTERED);   

					 logger.info("Applying DEVICE Copying Function for updateDevice");					
					
					registered.setState(actionable.getState());
					registered.setAction(actionable.getAction());
					registered.setLastUpdate(actionable.getLastUpdate());

					// The updated device to be saved in the m
					return registered;
				}
			});

		});
	}

	private Future<AsyncMap<String, Device>> retrieveSharedMap(String mapName){
		SharedData sd = vertx.sharedData();

		Future<AsyncMap<String, Device>> futureDevicesMap = Future.future();

		sd.<String, Device>getClusterWideMap(Constants.DEVICES_MAP, res -> {
			if (res.succeeded()) {
				futureDevicesMap.complete(res.result());
			} else {
				res.cause().printStackTrace();
				futureDevicesMap.fail(vertxErrorPrefix(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICES_MAP_ERROR)+" Failed to retrieved the shared MAP with name ["+mapName+"]");
			}
		});
		return futureDevicesMap;
	}
	
	// If I want to do something with the final result/oucome I need to pass a handler eg. SEND/REPLY otherwise no handler needed
//	private void registerDevice(String key, Device deviceRegister) {
	private Future<Device> registerDevice(String key, Device deviceRegister) {
	// NEW VERSION with Futures (Can we do it with RXJava API?
		
		Future<Device> futureRegDevice = Future.future();
		Future<AsyncMap<String, Device>> futureDevicesSharedMap = retrieveSharedMap(Constants.DEVICES_MAP);
		
		futureDevicesSharedMap.compose(sharedMap -> {

			futureDevicesSharedMap.result().putIfAbsent(key, deviceRegister, ar -> {
				if (ar.succeeded()) {
					Device device = ar.result();
					logger.info("ADDED DEVICE : "+deviceRegister.getId() +" with KEY : "+key);
					futureRegDevice.complete(device);
				} else {
					futureRegDevice.fail(appErrorPrefix(AppErrorCode.DEVICE_ALREADY_REGISTERED)+" for key "+generatedDeviceKey(deviceRegister));
				}
			});
		}, Future.future().setHandler(handler -> {
			logger.error(vertxErrorPrefix(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICE_ERROR)+" Unexpected Failure Occured during Device registration", handler.cause());
		}));
		
		return futureRegDevice;
		
		// OLD VERSION with handlers
//		SharedData sd = vertx.sharedData();
//		sd.<String, Device>getClusterWideMap(DEVICES_MAP, res -> {
//			if (res.succeeded()) {
//				// SUCCEEDED to find the DEVICES SHARED MAP
//				
//				// ADDING THE DEVICE
//				res.result().putIfAbsent(key, deviceRegister, ar -> {
//					if (ar.succeeded()) {
//						// HERE IT SHOULD HAVE BEEN ADDED
//						logger.info("ADDED DEVICE : "+deviceRegister.getId() +" with KEY : "+key);
//					} else {
//						// HERE IT SHOULD HAVE failed to BE ADDED on the MAP
//						logger.error("FAILED DEVICE : "+deviceRegister.getId() +" with KEY : "+key);						
//					}
//				});
//			} else {
//				logger.error("FAILED TO Retrieve DEVICES MAP");
//			}
//		});
	}
	
	private void updateDevice(Device deviceActionable, Function<Map<String, Device>, Device> copyDeviceContentFunc) {

		// retrieve Device 
		Future<Device> futureRegisteredDevice = getDevice(generatedDeviceKey(deviceActionable));

		futureRegisteredDevice.compose(registeredDevice -> {
			
			HashMap copyFuncMap = new HashMap();
			copyFuncMap.put(DevicesPurpose.ACTIONABLE,  deviceActionable);
			copyFuncMap.put(DevicesPurpose.REGISTERED,  registeredDevice);
			
			Device tobeUpdated = copyDeviceContentFunc.apply(copyFuncMap);

			Future<String> futureApplydDeviceUpdate = applyDeviceUpdate(tobeUpdated);

//			futureApplydDeviceUpdate.compose(updateMsg -> {
//
//				updateMsg
//
//			}, Future.future().setHandler(handler -> {
//				logger.error(vertxErrorPrefix(ERROR_CODES.DEVICE_MANAGEMENT_ACTIONS_BAD_ACTION), handler.cause());
//			}));

		}, Future.future().setHandler(handler -> {
			logger.error(appErrorPrefix(AppErrorCode.NON_REGISTERED_DEVICE)+"Unable to Retrieve Device Error", handler.cause());
		}));
		
	}
	
	@Deprecated
	private void updateDevice(Device deviceActionable) {
//		SharedData sd = vertx.sharedData();
		
		// retrieve Device 
		Future<Device> futureRegisteredDevice = getDevice(generatedDeviceKey(deviceActionable));

		futureRegisteredDevice.compose(registeredDevice -> {

			// CASE 1: UPDATE during sensor-generator ambiance-data publish
			//          lastUpdate, Temperature
			
			// CASE 2: HPR action INCREASE/DECREASE
			//          state, action, lastupdate
			
			// CASE 3: HPR action TURNOFF
			//          state, action Temperature
			
			// TODO - below it is not handling it AS MANY CASES not all covered deprecating in favour of private void updateDevice(Device deviceActionable, Function<Map<String, Device>, Device> copyDeviceContentFunc) {
		
			// TODO - Check DO we nned to update anything else? LastUpdate needed here?
			deviceActionable.setState(registeredDevice.getState());
			deviceActionable.setAction(registeredDevice.getAction());
			deviceActionable.setLastUpdate(registeredDevice.getLastUpdate());

			Future<String> futureApplydDeviceUpdate = applyDeviceUpdate(deviceActionable);

//			futureApplydDeviceUpdate.compose(updateMsg -> {
//
//				updateMsg
//
//			}, Future.future().setHandler(handler -> {
//				logger.error(vertxErrorPrefix(ERROR_CODES.DEVICE_MANAGEMENT_ACTIONS_BAD_ACTION), handler.cause());
//			}));

		}, Future.future().setHandler(handler -> {
			logger.error(appErrorPrefix(AppErrorCode.NON_REGISTERED_DEVICE)+"Unable to Retrieve Device Error", handler.cause());
		}));
		
//		sd.<String, Device>getClusterWideMap(DEVICES_MAP, res -> {
//			if (res.succeeded()) {
//				// SUCCEEDED to find the DEVICES SHARED MAP
//			
//				// GETTING THE DEVICE
//				res.result().get(generatedDeviceKey(deviceActionable), ar -> {
//					if (ar.succeeded()) {
//						
//						System.out.println("DEVICE RETRIEVED FROM SHARED MAP --> "+ar.result());
//						
//						// HERE IT SHOULD HAVE BEEN RETRIEVED based on the key
//						System.out.println("RETRIEVED DEVICE FROM SHARED MAP: "+ (res.result() != null ? generatedDeviceKey(ar.result()) : "NONE FOUND") +" AS : "+Json.encodePrettily(ar.result()));
//						
//						// Check result matches one updating
//						if (ar.result() != null && ar.result().equals(deviceActionable)){
//							
//							// updating
//							res.result().put(generatedDeviceKey(deviceActionable), deviceActionable, ar2 -> {
//								
//								if (ar2.succeeded()) {
//									// HERE IT SHOULD HAVE BEEN UPDADED on the MAP
//									System.out.println("UPDATED DEVICE : "+deviceActionable.getId() +" to : "+Json.encodePrettily(deviceActionable));
//								} else {
//
//									// HERE IT SHOULD HAVE failed to BE Updated on the MAP
//									// TODO - Consider Vert.X error
//								}
//							});
//						} else if (ar.result().getActionSequence() > deviceActionable.getActionSequence()) {
//							// WARNING: Messages out of Sync
//							System.out.println(appErrorPrefix(APP_ERROR_CODES.MESSAGE_OUT_OF_SYNC)+" UPDATE on DEVICE : "+generatedDeviceKey(deviceActionable) +" with Action Sequence "+deviceActionable.getActionSequence()+ "will not be applied as Shared Map already contains action sequence" + ar.result().getActionSequence());
//						} else {
//							// Trying to update a device for which there is no key registered in the Shared Map
//							System.out.println(appErrorPrefix(APP_ERROR_CODES.NON_REGISTERED_DEVICE)+" CANNOT MATCH DEVICE with ID "+generatedDeviceKey(deviceActionable)+" with a registered Device on Shared Map data");
//						}
//					} else {
//						// HERE IT SHOULD HAVE failed to BE RETREIVED from the MAP
//						// TODO - Consider Vert.X error
//					}
//				});
//			} else {
//				// FAILED to find the MAP
//				// TODO - Consider Vert.X error
//			}		
//		});
	}



	private void turnoffDevice(Device deviceActionable) {		
		SharedData sd = vertx.sharedData();

		sd.<String, Device>getClusterWideMap(Constants.DEVICES_MAP, res -> {
			if (res.succeeded()) {
				// SUCCEEDED to find the DEVICES SHARED MAP

				// GETTING THE DEVICE
				res.result().get(generatedDeviceKey(deviceActionable), ar -> {
					if (ar.succeeded()) {
						// HERE IT SHOULD HAVE BEEN RETRIEVED based on the key
						System.out.println("RETRIEVED DEVICE FROM SHARED MAP: "+ (res.result() != null ? generatedDeviceKey(ar.result()) : "NONE FOUND") +" AS : "+Json.encodePrettily(ar.result()));

						// Check result matches one updating
						if (ar.result() != null && ar.result().equals(deviceActionable)){

							// updating
							res.result().put(generatedDeviceKey(deviceActionable), deviceActionable, ar2 -> {

								if (ar2.succeeded()) {
									// HERE IT SHOULD HAVE BEEN UPDADED on the MAP
									System.out.println("TURN-OFF DEVICE : "+deviceActionable.getId() +" to : "+Json.encodePrettily(deviceActionable));
								} else {

									// HERE IT SHOULD HAVE failed to BE Updated on the MAP
									// TODO - Consider Vert.X error
								}
							});
						} 
//						else if (ar.result().getActionSequence() > deviceActionable.getActionSequence()) {
//							// WARNING: Messages out of Sync
//							System.out.println(appErrorPrefix(APP_ERROR_CODES.MESSAGE_OUT_OF_SYNC)+" UPDATE on DEVICE : "+generatedDeviceKey(deviceActionable) +" with Action Sequence "+deviceActionable.getActionSequence()+ "will not be applied as Shared Map already contains action sequence" + ar.result().getActionSequence());
//						} 
					    else {
							// Trying to update a device for which there is no key registered in the Shared Map
							System.out.println(appErrorPrefix(AppErrorCode.NON_REGISTERED_DEVICE)+" CANNOT MATCH DEVICE with ID "+generatedDeviceKey(deviceActionable)+" with a registered Device on Shared Map data");
						}
					} else {
						// HERE IT SHOULD HAVE failed to BE RETREIVED from the MAP
						// TODO - Consider Vert.X error
					}
				});
			} else {
				// FAILED to find the MAP
				// TODO - Consider Vert.X error
			}		
		});
	}

	private String appErrorPrefix(AppErrorCode error){
		return error.getErrorCode()+": "+error;
	}
	
	private String vertxErrorPrefix(ErrorCode error){
		return error.getErrorCode()+": "+error;
	}
	
	private String generatedDeviceKey(Device device) {
		return generatedDeviceKey(device.getHousePlanId(), device.getId());
	}
	
	private String generatedDeviceKey(HomePlanRegulationDTO device) {
		return generatedDeviceKey(device.getHousePlanId(), device.getSensor());
	}
	
	private String generatedDeviceKey(String housePlanId, String deviceId) {
		return housePlanId+Constants.DEVICES_ID_SEPARATOR+deviceId;
	}

	/* Used in registration and in turn off activities on a Device object */
	private int generateRandomLocationSensorTemperature(){
		Random rn = new Random();
		return (rn.nextInt(45 - 13 + 1) + 13);
	}
	
	private Future<String> applyDeviceUpdate(Device device) {
		SharedData sd = vertx.sharedData();

		// update Device 
		Future<String> futureUpdatedDevice = Future.future();
		Future<AsyncMap<String, Device>> futureDevicesSharedMap = retrieveSharedMap(Constants.DEVICES_MAP);
		
		futureDevicesSharedMap.compose(sharedMap -> {

			futureDevicesSharedMap.result().put(generatedDeviceKey(device), device, ar -> {
				if (ar.succeeded()) {
					futureUpdatedDevice.complete("Device ["+generatedDeviceKey(device)+"] successfully updated");
				} else {
					// Something went wrong! NOT BASED ON THE KEY
					futureUpdatedDevice.fail(appErrorPrefix(AppErrorCode.DEVICE_UPDATE_NOT_POSSIBLE)+" Unexpected Failure Occured during update of a Device "+generatedDeviceKey(device));
				}
			});
		}, Future.future().setHandler(handler -> {
			logger.error(vertxErrorPrefix(ErrorCode.DEVICE_MANAGEMENT_ACTIONS_BAD_ACTION), handler.cause());
		}));
		
		return futureUpdatedDevice;
	}
	
	private Future<Device> getDevice(String key) {

		Future<Device> futureRegDevice = Future.future();
		Future<AsyncMap<String, Device>> futureDevicesSharedMap = retrieveSharedMap(Constants.DEVICES_MAP);
		
		futureDevicesSharedMap.compose(sharedMap -> {

			futureDevicesSharedMap.result().get(key, ar -> {
				if (ar.succeeded()) {
					Device device = ar.result();
					if (device != null) {
						futureRegDevice.complete(device);
					} else {
						futureRegDevice.fail(appErrorPrefix(AppErrorCode.NON_REGISTERED_DEVICE)+" for key "+generatedDeviceKey(device));
					}
				} else {
					// Something went wrong! NOT BASED ON THE KEY
					futureRegDevice.fail(vertxErrorPrefix(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICE_ERROR)+" Unexpected Failure Occured during during retrieval of a Device ");
				}
			});
		}, Future.future().setHandler(handler -> {
			logger.error(vertxErrorPrefix(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICES_MAP_ERROR), handler.cause());
		}));
		
		return futureRegDevice;
		
//		SharedData sd = vertx.sharedData();
//		sd.<String, Device>getClusterWideMap(DEVICES_MAP, res -> {
//			if (res.succeeded()) {
//				
//				// ADDING THE DEVICE
//				//res.result().putIfAbsent(arg0, arg1, arg2);
//				
//				res.result().get(key, ar -> {
//					if (ar.succeeded()) {
//						Device device = ar.result();
//						if (device != null) {
//							// HERE I need to return the existing device						
//							
//							System.out.println("\n\n REPLYING to message FOUND DEVICE \n ------------------------------------------------------------------- \n "+Json.encodePrettily(device)+" \n -------------------------------------------------------------------");
//							message.reply(Json.encodePrettily(device));
//
//						} else {
//							// HERE I need to return c
//							// TODO - Handle ERROR MESSAGE
//							message.reply(null);
//
//						}
//					} else {
//						// Something went wrong! NOT BASED ON THE KEY
//						message.fail(ERROR_CODES.DEVICE_MANAGEMENT_GET_DEVICE_ERROR.getErrorCode(), "[device-management.get.device.error] Unexpected Failure Occured during during retrieval of key "+key);
//					}
//				});
//				;
//			} else {
//				// Something went wrong!
//				message.fail(ERROR_CODES.DEVICE_MANAGEMENT_GET_DEVICES_MAP_ERROR.getErrorCode(), "[device-management.get.devices.map.error] Unexpected Failure Occured during retrieval of dvices map ["+DEVICES_MAP+"] from shared data");
//			}
//		});
	}
	
	// If I want to do something with the final result/oucome I need to pass a handler eg. SEND/REPLY otherwise no handler needed
	private void getDevice(String key, Message<String> message) {
//	private void getDevice(String key) {

		SharedData sd = vertx.sharedData();
		sd.<String, Device>getClusterWideMap(Constants.DEVICES_MAP, res -> {
			if (res.succeeded()) {
				
				// ADDING THE DEVICE
				//res.result().putIfAbsent(arg0, arg1, arg2);
				
				res.result().get(key, ar -> {
					if (ar.succeeded()) {
						Device device = ar.result();
						if (device != null) {
							// HERE I need to return the existing device						
							
							logger.info("\n\n REPLYING to message FOUND DEVICE \n ------------------------------------------------------------------- \n "+Json.encodePrettily(device)+" \n -------------------------------------------------------------------");
							message.reply(Json.encodePrettily(device));

						} else {
							// HERE I need to return c
							// TODO - Handle ERROR MESSAGE
							message.reply(null);

						}
					} else {
						// Something went wrong! NOT BASED ON THE KEY
						message.fail(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICE_ERROR.getErrorCode(), "[device-management.get.device.error] Unexpected Failure Occured during during retrieval of key "+key);
					}
				});
				;
			} else {
				// Something went wrong!
				message.fail(ErrorCode.DEVICE_MANAGEMENT_GET_DEVICES_MAP_ERROR.getErrorCode(), "[device-management.get.devices.map.error] Unexpected Failure Occured during retrieval of dvices map ["+Constants.DEVICES_MAP+"] from shared data");
			}
		});
	}
	
	// If I want to do something with the final result/oucome I need to pass a handler eg. SEND/REPLY otherwise no handler needed
	private void getDevice2(String key) {

		SharedData sd = vertx.sharedData();
		sd.<String, Device>getClusterWideMap(Constants.DEVICES_MAP, res -> {
			if (res.succeeded()) {
				
				// ADDING THE DEVICE
				//res.result().putIfAbsent(arg0, arg1, arg2);
				
				res.result().get(key, ar -> {
					if (ar.succeeded()) {
						Device device = ar.result();
						if (device != null) {
							// HERE I need to return the existing device						
							
							System.out.println("\n\n FOUND DEVICE \n ------------------------------------------------------------------- \n "+device.toString()+" \n -------------------------------------------------------------------");
							
						} else {
							System.out.println("\n FAILED TO FIND In shared map Device " + key);
						}
					} else {
						// Something went wrong! NOT BASED ON THE KEY
						// TODO - VertX error message
					}
				});
				;
			} else {
				// Something went wrong!
				// TODO - VertX error message
			}
		});
	}


	
	
//    private void incrementAndGetDeviceData(RoutingContext rc) {
//    	Future<Object> future = Future.future();
//		vertx.sharedData().getClusterWideMap("device-shared-data", future);
//    	
//    	/* RX JAVA API equivalent 
//        vertx.sharedData().rxGetCounter("device-shared-data")
//            .flatMap(Counter::rxIncrementAndGet)
//            .map(count -> new JsonObject().put("value", count).put("appId", getNodeId()))
//            .subscribe(
//                json -> rc.response().end(json.encode()),
//                rc::fail
//            );
//            */
//    }

//    private void getDeviceData(RoutingContext rc) {
//        vertx.sharedData().rxGetCounter("my-counter")
//            .flatMap(Counter::rxGet)
//            .map(count -> new JsonObject().put("value", count).put("appId", getNodeId()))
//            .subscribe(
//                json -> rc.response().end(json.encode()),
//                rc::fail
//            );
//    }
	
	
	// LATER FOR DEVICE ACTIONS FROM: http://vertx.io/docs/guide-for-java-devs/#_the_database_verticle
	/*
	 *
	public void onMessage(Message<JsonObject> message) {

  if (!message.headers().contains("action")) {
    LOGGER.error("No action header specified for message with headers {} and body {}",
      message.headers(), message.body().encodePrettily());
    message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
    return;
  }
  String action = message.headers().get("action");

  switch (action) {
    case "all-pages":
      fetchAllPages(message);
      break;
    case "get-page":
      fetchPage(message);
      break;
    case "create-page":
      createPage(message);
      break;
    case "save-page":
      savePage(message);
      break;
    case "delete-page":
      deletePage(message);
      break;
    default:
      message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
  }
}
	 */
	


	
	  /* TODO - Remove as only tester helper methods to generate messages on the bus */
//	  private String createRegistrationPayload() {
//			Device regDev1 = new Device(null, "kitchen-1", DEVICE_TYPE.AIRCON, DEVICE_ACTION.NONE, DEVICE_STATE.OFF, 0, 0, 0L, 0L);
//			Device regDev2 = new Device(null, "bedroom-1", DEVICE_TYPE.AIRCON, DEVICE_ACTION.NONE, DEVICE_STATE.OFF, 0, 0, 0L, 0L);
//			
//			HashMap<String, List<Device>> payload = new HashMap<String, List<Device>>();
//			DeviceDTO dtoMsg = new DeviceDTO("kousourisHousehold", Arrays.asList(regDev1, regDev2));
//			//payload.put("kousourisHousehold", Arrays.asList(regDev1, regDev2));
//			
//			System.out.println("\n-----------------PAYLOAD ---------------------------\n"+Json.encodePrettily(dtoMsg)+"\n------------------------------------------------------------------");
//			
//			return Json.encodePrettily(dtoMsg);	
//	  }
//	  
//	  private String createDeviceDummyActionPayload(DEVICE_MANAGEMENT_ACTION devicemntaction,  DEVICE_ACTION action, int fromNo, int toNo) {
//		  Device updateDevice;
//
//		if (devicemntaction.equals(DEVICE_MANAGEMENT_ACTION.TURNOFF_DEVICE.toString())) {
//			updateDevice = new Device("kousourisHousehold", "bedroom-1", DEVICE_TYPE.AIRCON, DEVICE_ACTION.INCREASING, DEVICE_STATE.OFF, 0, 0, 0L, 1L);
//		} else {
//			updateDevice = new Device("kousourisHousehold", "bedroom-1", DEVICE_TYPE.AIRCON, action, DEVICE_STATE.ON, fromNo, toNo, TimeUtils.timeInMillisNow(), 1L);
//		}
//		System.out.println("\n-----------------PAYLOAD ---------------------------\n"+Json.encodePrettily(updateDevice)+"\n------------------------------------------------------------------");
//
//		return Json.encodePrettily(updateDevice);	
//
//	  }
}