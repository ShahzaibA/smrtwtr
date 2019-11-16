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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tk.plogitech.darksky.forecast.ForecastException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@RestController
public class SprinklerController {

    private GpioPinDigitalOutput pin;
    private Scheduler s;

    private String scheduleId;
    private int sprinkleDuration;

    @PostMapping("/on")
    public void openValve() {
        // initialize gpio pin if not initialized
        if(pin == null) {
            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, "MyLED", PinState.HIGH);
        }
        // turn on gpio pin #01
        pin.low();
        log.info("GPIO ON");
    }

    @PostMapping("/off")
    public void closeValve() {
        // initialize gpio pin if not initialized
        if(pin == null) {
            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, "MyLED", PinState.HIGH);
        }
        // turn off gpio pin #01
        pin.high();
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
