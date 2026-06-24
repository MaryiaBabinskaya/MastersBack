package com.krakow.theaters.dto;

import lombok.Data;

@Data
public class LudowyEvent {
    private String data;
    private String godzina;
    private String tytul;
    private String scena;
    private String linkBilety;
    private String plakat;
    private String linkSpektaklu;
}
