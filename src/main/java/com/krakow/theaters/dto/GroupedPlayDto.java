package com.krakow.theaters.dto;

import lombok.Data;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

@Data
public class GroupedPlayDto {
    private String title;
    private Double price;
    private String source;
    private String category;
    private String eventInfo;
    private Boolean isSpectacle;
    private Boolean isRepertoire;
    private String imageUrl;
    private String scene;
    private String duration;
    private String description;
    private String additionalInfo;
    private String detailsJson;
    private String youtubeUrl;
    private TheatreDto theatre;
    private List<ShowtimeDto> showtimes = new ArrayList<>();

    @Data
    public static class ShowtimeDto {
        private LocalDateTime showtimeAsDateTime;
        private String id;
        private String showtime;
        private String ticketUrl;
    }

    @Data
    public static class TheatreDto {
        private String id;
        private String name;
        private String url;
        private String imageUrl;
    }
}
