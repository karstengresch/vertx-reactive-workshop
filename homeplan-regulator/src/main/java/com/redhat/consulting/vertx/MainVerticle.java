package com.redhat.consulting.vertx;

import com.redhat.consulting.vertx.Constants.AppErrorCode;
import com.redhat.consulting.vertx.Constants.DeviceAction;
import com.redhat.consulting.vertx.data.HomePlan;
import com.redhat.consulting.vertx.data.SensorLocation;
import com.redhat.consulting.vertx.dto.AmbianceDTO;
import com.redhat.consulting.vertx.dto.DeviceActionDTO;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
	
	private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	
	public int actionCounter = 0;
	
	  @Override
	  public void start() {
		  
		  startHomeplanRegulatorEventBusProvider();

		  // OLD IMPLEMENTATION WORKING
//			System.out.println("\n\n STARTING HOMEPLAN REGULATOR - MainVerticle \n");
//
//		  
//			vertx.eventBus().<String>consumer(AMBIANBCE_DATA_ADDRESS, message -> {
//
//				System.out.println("\n\n CONSUMING message from #"+AMBIANBCE_DATA_ADDRESS);
//				System.out.println("HANDLED BY Verticle.EventLoop" + this.toString());
//
//				// Check whether we have received a payload in the incoming message
//				if (message.body().isEmpty()) {
//					// SEND/REPLY example
//					// message.reply(json.put("message", "hello"));
//				} else {
//									
//					// We will receive it as JSON string, transform it to its class equivalent
//					AmbianceDTO ambianceData = Json.decodeValue(message.body(), AmbianceDTO.class);
//
//					
//					System.out.println("\n CONSUMED AMBIANCE-DATA \n"+Json.encodePrettily(ambianceData)+"\n\n");
//								
//					Future<HomePlan> futureHomeplan = getHomePlan(ambianceData.getHousePlanId());
//					futureHomeplan.compose(s2 -> {
//						System.out.println("Get sensor location preferences status for home plan: " + ambianceData.getHousePlanId());
//						
//						if (s2.getSensorLocations()!= null && !s2.getSensorLocations().isEmpty()) {
//							for (SensorLocation sl : s2.getSensorLocations()) {
//								
//								System.out.println("Finding match between ambiance data ["+ambianceData.getHousePlanId()+"-"+ambianceData.getSensorLocation().getId()
//										+"] and sensor location ["+ambianceData.getHousePlanId()+"-"+sl.getId()+"]");
//								
//								if (ambianceData.getSensorLocation().getId()!= null && sl.getId() != null && ambianceData.getSensorLocation().getId().equals(sl.getId())){
//									
//									String msgPayload = null;
//									DEVICE_MANAGEMENT_ACTION headerAction = DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE;
//									
//									System.out.println("MATCH-FOUND: Homeplan Regulator in action on Device: "+ambianceData.getHousePlanId()+"-"+sl.getId());
//									
//									System.out.println("Action Before: "+actionCounter);
//									if (actionCounter == 3){
//										actionCounter = 1;
//									} else {
//										actionCounter =+ 1;
//									}
//									System.out.println("Action NOW: "+actionCounter);
//									
//
//									
//									switch(actionCounter) {
//									case 1:
//										System.out.println("Action NOW: "+DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE);
//										System.out.println("Action NOW: "+DEVICE_ACTION.INCREASING);
//										headerAction = DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE;
//										msgPayload = createDeviceManagementActionPayload(ambianceData.getHousePlanId(), sl.getId(), DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE, DEVICE_ACTION.INCREASING, DEVICE_TYPE.AIRCON, 16, 23, TimeUtils.timeInMillisNow(), 1L);
//										break;
//									case 2:
//										System.out.println("Action NOW: "+DEVICE_MANAGEMENT_ACTION.TURNOFF_DEVICE);
//										headerAction = DEVICE_MANAGEMENT_ACTION.TURNOFF_DEVICE;
//										msgPayload = createDeviceManagementActionPayload(ambianceData.getHousePlanId(), sl.getId(), DEVICE_MANAGEMENT_ACTION.TURNOFF_DEVICE, DEVICE_ACTION.NONE, DEVICE_TYPE.AIRCON, 0, 0, TimeUtils.timeInMillisNow(), 1L);
//
//										break;
//									case 3:
//										System.out.println("Action NOW: "+DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE);
//										System.out.println("Action NOW: "+DEVICE_ACTION.DECREASING);
//										headerAction = DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE;
//										msgPayload = createDeviceManagementActionPayload(ambianceData.getHousePlanId(), sl.getId(), DEVICE_MANAGEMENT_ACTION.ACTIVATE_DEVICE, DEVICE_ACTION.DECREASING, DEVICE_TYPE.AIRCON, 25, 21, TimeUtils.timeInMillisNow(), 1L);
//
//										break;
//
//									}
//											
//									sendDeviceAction(headerAction, msgPayload);
//
//								}
//
//							}
//						}
//					}, Future.future().setHandler(handler -> {
//						// Something went wrong!
//						handler.cause().printStackTrace();
//						System.out.println("Error getting Homeplans");
//					}));
//				}
//
//			});		
						
			
	  }
	
	private void startHomeplanRegulatorEventBusProvider() {
		vertx.eventBus().<String>consumer(Constants.AMBIANBCE_DATA_ADDRESS, message -> {
			applyHomePlanRegulation(message);
		});
		logger.info("\n----------------------------------------------------------------------------\n HOMEPLAN REGULATOR EVENT BUS ready (Vert.X EventLoop "+this.toString()+" \n----------------------------------------------------------------------------");
		
	}

	private void applyHomePlanRegulation(Message<String> message) {
		// We will receive it as JSON string, transform it to its class equivalent
		AmbianceDTO ambianceData = Json.decodeValue(message.body(), AmbianceDTO.class);
		
		Future<HomePlan> futureHomePlan = getHomePlan(ambianceData.getHousePlanId());
		futureHomePlan.compose(s1 -> {
			HomePlan homePlan = futureHomePlan.result();
			
			Future<String> futureRegMsg = sendRegulatoryMsg(homePlan, ambianceData);

			futureRegMsg.compose(s2 -> {

				if (s2 != null) {
					logger.info("Applied Successfully HomePlan temperature regulation for location "+ambianceData.getHousePlanId()+"-"+ambianceData.getSensorLocation().getId());
				} else {
					logger.info("Failed to apply HomePlan temperature regulation for location "+ambianceData.getHousePlanId()+"-"+ambianceData.getSensorLocation().getId());
				}

			}, Future.future().setHandler(handler -> {
				logger.error(appErrorPrefix(AppErrorCode.HOMEPLAN_REGULATOR_FAIL_APPLY_HOMEPLAN)+"Homeplan Regulation Error", handler.cause());
			}));
			
		}, Future.future().setHandler(handler -> {
			logger.error(appErrorPrefix(AppErrorCode.HOMEPLAN_REGULATOR_FAIL_RETRIEVE_HOMEPLAN)+"Homeplan Retrieval Error", handler.cause());
		}));
	}	
	
	private Future<HomePlan> getHomePlan(String homeplanId) {
		Future<HomePlan> future = Future.future();
		vertx.eventBus().send(Constants.HOMEPLANS_EVENTS_ADDRESS, homeplanId, reply -> {
			if (reply.succeeded()) {
				final HomePlan homePlan = Json.decodeValue(reply.result().body().toString(), HomePlan.class);
				future.complete(homePlan);
			} else {
				reply.cause().printStackTrace();
				future.fail("No reply from Homeplan service");
			}
		});
		return future;
	}
		
	private Future<String> sendRegulatoryMsg(HomePlan plan, AmbianceDTO ambianceData) {
		Future<String> futureHPReguMsg = Future.future();
		
		if (plan.getSensorLocations()!= null && !plan.getSensorLocations().isEmpty()) {
			for (SensorLocation sl : plan.getSensorLocations()) {

				logger.debug("Finding match between ambiance data ["+ambianceData.getHousePlanId()+"-"+ambianceData.getSensorLocation().getId()
						+"] and sensor location ["+ambianceData.getHousePlanId()+"-"+sl.getId()+"]");

				if (ambianceData.getSensorLocation().getId()!= null && sl.getId() != null && ambianceData.getSensorLocation().getId().equals(sl.getId())){
					logger.debug("MATCH-FOUND: Homeplan Regulator in action on Device: "+ambianceData.getHousePlanId()+"-"+sl.getId());

					DeviceAction headerAction = applyTemperatureHomePlan(sl.getTemperature(), ambianceData.getSensorLocation().getTemperature());
					String msgPayload = Json.encodePrettily((new DeviceActionDTO(ambianceData.getHousePlanId(), sl.getId())));


					sendDeviceAction(headerAction, msgPayload);

					futureHPReguMsg.complete("HomePlan Regulation applied");
				}
			}
		}
		return futureHPReguMsg;
	}
	
	  private Future<String> sendDeviceAction(DeviceAction headerAction, String msgPayload) {
		  Future<String> future = Future.future();
		  
		  logger.info("Sending Regulating action <"+headerAction+"> on Device: "+msgPayload);
					
		  DeliveryOptions options = new DeliveryOptions();
		  options.addHeader(Constants.DEVICE_ACTION_HEADER, headerAction.toString());
			
		  vertx.eventBus().send(Constants.DEVICE_ACTION_EVENTS_ADDRESS, msgPayload, options);
		  future.complete("HomePlan Regulation Sent");
		  return future;
	  }
	
	private DeviceAction applyTemperatureHomePlan(int planSensorLocationTemperature, int sensorTemperature) {
		if (planSensorLocationTemperature > sensorTemperature)
			return DeviceAction.INCREASING;
		else if (planSensorLocationTemperature < sensorTemperature)
			return DeviceAction.DECREASING;
		return DeviceAction.TURNOFF;
	}
	
	private String appErrorPrefix(AppErrorCode error){
		return error.getErrorCode()+": "+error;
	}
	
 
//  private String createDeviceManagementActionPayload(String homePlanId, String sensorLocationNAME) {
//		 return  Json.encodePrettily((new DeviceActionDTO(homePlanId, sensorLocationNAME)));
//  }




}
