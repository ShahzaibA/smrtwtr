package com.uh.smrtwtr.controller;

import com.pi4j.io.gpio.*;
import com.uh.smrtwtr.resource.SchedulerResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
public class SprinklerController {

    private boolean cancelSchedule = false;
    private GpioPinDigitalOutput pin;


    @PostMapping("/on")
    public void openValve() {
        if(pin == null) {
            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "MyLED", PinState.LOW);
        }
        // turn on gpio pin #01
        pin.high();
        log.info("GPIO ON");
    }

    @PostMapping("/off")
    public void closeValve() {
        if(pin == null) {
            // create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "MyLED", PinState.LOW);
        }
        // turn off gpio pin #01
        pin.low();
        log.info("GPIO OFF");
    }

    @PostMapping("/schedule/cancel")
    public void cancelSchedule() {
        cancelSchedule = true;
    }


    @PostMapping("/schedule")
    public void runSchedule(@RequestBody SchedulerResource schedulerResource) throws InterruptedException {
        cancelSchedule = false;
        log.info("Schedule Started");
        while(!cancelSchedule) {
            Thread.sleep(5000);
            String dayOfTheWeek = LocalDate.now().getDayOfWeek().name();
            LocalTime localTime = LocalTime.now();
            String t = localTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            if(schedulerResource.getStartTime().equals(t)) {
                log.info("GPIO ON");
                Thread.sleep(schedulerResource.getDuration()*1000);
                log.info("GPIO OFF");
            }
        }
        log.info("Schedule Stopped");
    }
}
