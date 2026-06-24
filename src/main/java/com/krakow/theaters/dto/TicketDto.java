package com.krakow.theaters.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {
    private Long id;
    private Long userId;
    private String userNickname;
    private String playId;
    private String playTitle;
    private String playShowtime;
    private String playImageUrl;
    private String theatreName;
    private LocalDateTime purchaseDate;
    private Double purchasePrice;
    private String status;
    private String seatInfo;
}