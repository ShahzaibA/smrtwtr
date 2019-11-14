package com.uh.smrtwtr.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SchedulerResource {

    @JsonProperty("startTime")
    @NotNull(message = "A start time must be provided.")
    private String startTime;

    @JsonProperty("duration")
    @NotNull(message = "A sprinkler duration must be provided.")
    private int duration;

    @JsonProperty("days")
    @NotNull(message = "You must specify which days to turn the sprinkler on.")
    private List<String> days;

    public SchedulerResource() {}
}
