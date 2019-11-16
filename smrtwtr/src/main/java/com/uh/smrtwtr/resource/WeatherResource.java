package com.uh.smrtwtr.resource;

import lombok.Data;

@Data
public class WeatherResource {

    private long time;
    private String summary;
    private String icon;
    private int nearestStormDistance;
    private int nearestStormBearing;
    private int precipIntensity;
    private int precipProbability;
    private double temperature;
    private double apparentTemperature;
    private double dewPoint;
    private double humidity;
    private double pressure;
    private double windSpeed;
    private double windGust;
    private int windBearing;
    private int cloudCover;
    private int uvIndex;
    private float visibility;
    private double ozone;
}
