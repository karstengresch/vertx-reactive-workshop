package com.redhat.consulting.vertx;

import java.util.List;

import com.redhat.consulting.vertx.data.AmbianceEvent;
import com.redhat.consulting.vertx.data.DeviceStatus;
import com.redhat.consulting.vertx.data.HomePlan;
import com.redhat.consulting.vertx.data.Sensor;
import com.redhat.consulting.vertx.data.SensorLocation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

public class MainVerticle extends AbstractVerticle {

	// Addresses
	private static final String HOMEPLANS_EVENTS_ADDRESS = "homeplans";

	public static final String DEVICE_DATA_EVENTS_ADDRESS = "device-data";

	public static final String AMBIANCE_DATA_EVENTS_ADDRESS = "ambiance-data";

	// logger

	private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

	// Web client

	private WebClient client;

	@Override
	public void start() {
		initWebClient();
		startAmbianceDataTimer();
	}

	private void initWebClient() {
		client = WebClient.create(vertx);
	}

	private void startAmbianceDataTimer() {
		vertx.setPeriodic(10000, id -> {
			Future<List<String>> futureGetIds = getHomePlanIds();
			futureGetIds.compose(s1 -> {
				if (s1 != null && !s1.isEmpty()) {
					logger.info("Getting homeplan details for {} homeplans", s1.size());
					for (String homeplan : s1) {
						Future<HomePlan> futureHomeplan = getHomePlan(homeplan);
						futureHomeplan.compose(s2 -> {
							logger.debug("Getting homeplan devices status");
							if (s2.getSensorLocations() != null && !s2.getSensorLocations().isEmpty()) {
								for (SensorLocation sl : s2.getSensorLocations()) {
									Future<DeviceStatus> futureDeviceStatus = getDeviceStatus(s2.getId(), sl.getId());
									futureDeviceStatus.compose(s3 -> {
										sendAmbianceData(s3, s2);
									}, Future.future().setHandler(handler -> {
										// Something went wrong!
										logger.error("Error getting devices status", handler.cause());
									}));
								}
							}
						}, Future.future().setHandler(handler -> {
							// Something went wrong!
							logger.error("Error getting Homeplan details", handler.cause());
						}));
					}
				} else {
					logger.info("No homeplans available");
				}

			}, Future.future().setHandler(handler -> {
				// Something went wrong!
				logger.error("Error getting Homeplans", handler.cause());
			}));
		});

	}

	private void sendAmbianceData(DeviceStatus deviceStatus, HomePlan homePlan) {
		// identify sensor location id in homeplan matching device id
		SensorLocation sl = null;
		if (homePlan.getSensorLocations() != null && !homePlan.getSensorLocations().isEmpty()) {
			for (SensorLocation slinHomeplan : homePlan.getSensorLocations()) {
				if (slinHomeplan.getId().equals(deviceStatus.getId())) {
					int temperature = simulateTemperatureBehavior(deviceStatus, slinHomeplan.getTemperature());
					sl = new SensorLocation(slinHomeplan.getId(), slinHomeplan.getType(), temperature);
					break;
				}
			}

		}
		if (sl != null) {
			// publish ambiance data
			AmbianceEvent ae = new AmbianceEvent(homePlan.getId(), sl);
			logger.info("Publishing in address {} event {}", AMBIANCE_DATA_EVENTS_ADDRESS, Json.encodePrettily(ae));
			vertx.eventBus().publish(AMBIANCE_DATA_EVENTS_ADDRESS, Json.encode(ae));
		} else {
			logger.warn("No matching sensor with id {} in homeplan {}", deviceStatus.getId(), homePlan.getId());
		}

	}

	// TODO implement it!!!
	private int simulateTemperatureBehavior(DeviceStatus deviceStatus, int homeplanTemperature) {
		return homeplanTemperature;
	}

	private Future<DeviceStatus> getDeviceStatus(String homeplanId, String sensorId) {
		Future<DeviceStatus> future = Future.future();
		logger.debug("Sending event to address {} to get device status for id {}-{}", DEVICE_DATA_EVENTS_ADDRESS,
				homeplanId, sensorId);
		vertx.eventBus().send(DEVICE_DATA_EVENTS_ADDRESS, Json.encode(new Sensor(homeplanId, sensorId)), reply -> {
			if (reply.succeeded()) {
				final DeviceStatus deviceStatus = Json.decodeValue(reply.result().body().toString(),
						DeviceStatus.class);
				future.complete(deviceStatus);
			} else {
				reply.cause().printStackTrace();
				future.fail("No reply from device management service");
			}
		});
		return future;
	}

	private Future<HomePlan> getHomePlan(String homeplanId) {
		Future<HomePlan> future = Future.future();
		logger.debug("Sending event to address {} to get homeplan details for id {}", HOMEPLANS_EVENTS_ADDRESS,
				homeplanId);
		vertx.eventBus().send(HOMEPLANS_EVENTS_ADDRESS, homeplanId, reply -> {
			if (reply.succeeded()) {
				final HomePlan homePlan = Json.decodeValue(reply.result().body().toString(), HomePlan.class);
				future.complete(homePlan);
				logger.debug("Homeplan returned");
			} else {
				logger.error("No reply from Homeplan service", reply.cause());
				future.fail("No reply from Homeplan service");
			}
		});
		return future;
	}

	private Future<List<String>> getHomePlanIds() {
		Future<List<String>> future = Future.future();
		logger.debug("Getting all homeplans ids");
		HttpRequest<JsonObject> request = client.get(8080, "localhost", "/homeplan").as(BodyCodec.jsonObject());
		request.send(ar -> {
			if (ar.succeeded()) {
				future.complete(ar.result().body().getJsonArray("ids").getList());
				logger.debug("Homeplan ids returned");
			} else {
				logger.error("Could not get Homeplan ids", ar.cause());
				future.fail("Could not get Homeplan ids");
			}
		});
		return future;
	}

	// private Future<HomePlan> getHomePlan(String homeplanId) {
	// Future<HomePlan> future = Future.future();
	// HttpRequest<JsonObject> request = client.get(8080, "localhost",
	// "/homeplan/" + homeplanId).as(BodyCodec.jsonObject());
	// request.send(ar -> {
	// if (ar.succeeded()) {
	// future.complete(ar.result().bodyAsJson(HomePlan.class));
	// } else {
	// ar.cause().printStackTrace();
	// future.fail("Could not get Homeplan " + homeplanId);
	// }
	// });
	// return future;
	// }

}
