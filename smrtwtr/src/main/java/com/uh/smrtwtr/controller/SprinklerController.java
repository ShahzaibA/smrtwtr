package com.uh.smrtwtr.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pi4j.io.gpio.*;
import com.uh.smrtwtr.resource.SchedulerResource;
import com.uh.smrtwtr.resource.WeatherResource;
import com.uh.smrtwtr.utils.DarkSkyApi;
import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.SchedulingPattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tk.plogitech.darksky.forecast.ForecastException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@RestController
public class SprinklerController {

    private GpioPinDigitalOutput pin;
    private Scheduler s;

    // list to store all gpio pins
    List<GpioPinDigitalOutput> pins = new ArrayList<>();

    private String scheduleId;
    private int sprinkleDuration;

    private void initalizeGPIOPins() {
        final GpioController gpio = GpioFactory.getInstance();
        // initialize all gpio pins
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_08, "GPIO_08", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_09, "GPIO_09", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_07, "GPIO_07", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "GPIO_00", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, "GPIO_02", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, "GPIO_03", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_12, "GPIO_12", PinState.HIGH));
        pins.add(gpio.provisionDigitalOutputPin(RaspiPin.GPIO_13, "GPIO_13", PinState.HIGH));
    }

    @PostMapping("/on/{stationId}")
    public void openValve(@PathVariable int stationId) {
        // initialize gpio pins if not initialized
        if(pins.isEmpty()) {
            initalizeGPIOPins();
        }
        // turn on gpio pin
        pins.get(stationId-1).low();
        log.info("GPIO ON");
    }

    @PostMapping("/off/{stationId}")
    public void closeValve(@PathVariable int stationId) {
        // initialize gpio pins if not initialized
        if(pins.isEmpty()) {
            initalizeGPIOPins();
        }
        // turn off gpio pin
        pins.get(stationId-1).high();
        log.info("GPIO OFF");
    }

    @GetMapping("/weather")
    public ResponseEntity getWeather() throws ForecastException {
        DarkSkyApi darkSkyApi = new DarkSkyApi();
        JsonObject forecastJsonObject = darkSkyApi.getForecast();
        JsonObject currently = forecastJsonObject.get("currently").getAsJsonObject();
        WeatherResource weatherResource = new Gson().fromJson(currently, WeatherResource.class);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(weatherResource);
    }

    public void runSprinklers() throws InterruptedException, ForecastException {
        log.info("Running Sprinklers");
        DarkSkyApi darkSkyApi = new DarkSkyApi();
        JsonObject forecastJsonObject = darkSkyApi.getForecast();
        JsonObject currently = forecastJsonObject.get("currently").getAsJsonObject();
        int precipProbability = currently.get("precipProbability").getAsInt();

        if(precipProbability < 30){
            pin.low();
            log.info("GPIO ON");
            Thread.sleep(this.sprinkleDuration*1000);
            pin.high();
            log.info("GPIO OFF");
        }
    }

    @PostMapping("/schedule")
    public ResponseEntity schedule(@RequestBody SchedulerResource schedulerResource) {
        this.sprinkleDuration = schedulerResource.getDuration();

        // initialize gpio pin if not initialized
        if(pin == null) {
            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, "MyLED", PinState.HIGH);
        }

        // parse user input in to cron format
        String[] time = schedulerResource.getStartTime().split(":");
        String minutes = time[1];
        String hours = time[0];
        String days = schedulerResource.getDays().stream().collect(Collectors.joining(","));

        String cronTask = minutes + " " + hours + " * * " + days;
        // creates the scheduler instance
        s = new Scheduler();
        // schedule a based on user input
        scheduleId = s.schedule(cronTask, new Runnable() {
            public void run() {
                try {
                    runSprinklers();
                } catch (InterruptedException | ForecastException e) {
                    e.printStackTrace();
                }
            }
        });
        // starts the scheduler
        s.start();
        return new ResponseEntity(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/schedule/cancel")
    public void cancelSchedule() {
        s.deschedule(scheduleId);
        s.stop();
    }

    @GetMapping("/schedule")
    public ResponseEntity getSchedule() {
        SchedulingPattern schedulingPattern = s.getSchedulingPattern(scheduleId);
        String[] cronTask = schedulingPattern.toString().split(" ");
        String startTime =  cronTask[1] + ":" + cronTask[0];
        int duration = this.sprinkleDuration;
        List<String> days = Arrays.stream(cronTask[4].split(",")).collect(Collectors.toList());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new SchedulerResource(startTime, duration, days));
    }
}
