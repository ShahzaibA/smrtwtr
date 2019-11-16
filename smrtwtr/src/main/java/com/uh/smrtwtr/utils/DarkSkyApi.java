package com.uh.smrtwtr.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Data;
import tk.plogitech.darksky.forecast.*;
import tk.plogitech.darksky.forecast.model.Latitude;
import tk.plogitech.darksky.forecast.model.Longitude;

@Data
public class DarkSkyApi {

    public JsonObject getForecast() throws ForecastException {
        ForecastRequest forecastRequest = new ForecastRequestBuilder()
                .key(new APIKey("8bc54314ca1abd9a3902d2f0e2342444"))
                .location(new GeoCoordinates(new Longitude(-95.369804), new Latitude(29.760427)))
                .units(ForecastRequestBuilder.Units.us)
                .build();

        DarkSkyClient client = new DarkSkyClient();
        JsonObject forecastJsonObject  = new Gson().fromJson(client.forecastJsonString(forecastRequest), JsonObject.class);
        return forecastJsonObject;
    }
}
