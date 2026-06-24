package com.krakow.theaters.dto;

import lombok.Data;

@Data
public class UpcomingTermDto {
    private String month;
    private String dayOfMonth;
    private String dayLabel;
    private String time;
    private String status;
    private String ticketUrl;
}