package com.krakow.theaters.dto;

import lombok.Data;

@Data
public class GroteskaEvent {
    private String data;
    private String godzina;
    private String tytul;
    private String scena;
    private String linkBilety;
    private String eventId;
    private String posterId;
}
