package com.krakow.theaters.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

public class TeatrWKrakowieDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlayDto {
        private String name;
        private String type;
        private String category;
        private String eventInfo;
        private boolean spectacle;
        private boolean repertoireEvent;
        private String description;
        private List<ActorDto> actors;
        private String scene;
        private String stageDirector;
        private String trailerLink;
        private String imageLink;
        private String detailUrl;
        private String ticketLink;
        private List<PerformanceDto> performances;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActorDto {
        private String name;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PerformanceDto {
        private String date;
        private String time;
        private String dateTime;
        private String scene;
        private String instanceId;
    }
}
