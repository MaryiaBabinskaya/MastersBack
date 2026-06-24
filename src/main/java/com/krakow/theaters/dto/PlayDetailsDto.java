package com.krakow.theaters.dto;

import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class PlayDetailsDto {
    private String title;
    private String source;
    private String imageUrl;
    private String scene;
    private String durationMinutesText;
    private String description;
    private String additionalInfo;
    private String category;
    private String youtubeUrl;
    private String premiereDate;

    private List<ContributorDto> contributors = new ArrayList<>();
    private List<CastMemberDto> cast = new ArrayList<>();
    private List<String> galleryImages = new ArrayList<>();
    private List<UpcomingTermDto> upcomingTerms = new ArrayList<>();
}
