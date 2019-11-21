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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@RestController
public class SprinklerController {

    private Scheduler s = new Scheduler();

    // list to store all gpio pins
    List<GpioPinDigitalOutput> pins = new ArrayList<>();
    List<String> scheduleIds = new ArrayList<>();

    private String scheduleId;
    private int sprinkleDuration;
    private int stations;

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

    public void runSprinklers(int station) throws InterruptedException, ForecastException {
        log.info("Running Sprinklers");
        DarkSkyApi darkSkyApi = new DarkSkyApi();
        JsonObject forecastJsonObject = darkSkyApi.getForecast();
        JsonObject currently = forecastJsonObject.get("currently").getAsJsonObject();
        int precipProbability = currently.get("precipProbability").getAsInt();

        if(precipProbability < 30){
            pins.get(station).low();
            log.info("GPIO ON");
            Thread.sleep(this.sprinkleDuration*60000);
            pins.get(station).high();
            log.info("GPIO OFF");
        }
    }

    @PostMapping("/schedule")
    public ResponseEntity schedule(@RequestBody SchedulerResource schedulerResource) {
        // initialize gpio pins if not initialized
        if(pins.isEmpty()) {
            initalizeGPIOPins();
        }
        this.sprinkleDuration = schedulerResource.getDuration();
        this.stations = schedulerResource.getStations();

        DateTimeFormatter df = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime lt = LocalTime.parse(schedulerResource.getStartTime());
        for(int i = 0; i < stations; i++) {
            if(i != 0) {
                lt = lt.plusMinutes(this.sprinkleDuration);
            }
            int stationId = i;
            String[] time = df.format(lt).split(":");
            String minutes = time[1];
            String hours = time[0];
            String days = schedulerResource.getDays().stream().collect(Collectors.joining(","));

            // creates cronTask
            String cronTask = minutes + " " + hours + " * * " + days;

            // creates a scheduler instance which runs on a thread
            scheduleId = s.schedule(cronTask, new Runnable() {
                public void run() {
                    try {
                        runSprinklers(stationId);
                    } catch (InterruptedException | ForecastException e) {
                        e.printStackTrace();
                    }
                }
            });
            scheduleIds.add(scheduleId);
        }
        s.start();
        return new ResponseEntity(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/schedule/cancel")
    public void cancelSchedule() {
        Iterator<String> i = scheduleIds.iterator();
        while (i.hasNext()) {
            String id = i.next(); // must be called before you can call i.remove()
            // deschedule task
            s.deschedule(id);

            i.remove();
        }
        
        s.stop();
    }

    @GetMapping("/schedule")
    public ResponseEntity getSchedule() {
        SchedulingPattern schedulingPattern = s.getSchedulingPattern(scheduleIds.get(0));
        String[] cronTask = schedulingPattern.toString().split(" ");
        String startTime =  cronTask[1] + ":" + cronTask[0];
        int duration = this.sprinkleDuration;
        int stations = this.getStations();
        List<String> days = Arrays.stream(cronTask[4].split(",")).collect(Collectors.toList());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new SchedulerResource(startTime, duration, days, stations));
    }
}
