package com.krakow.theaters.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KtoEvent {
    private String tytul;
    private String data;
    private String godzina;
    private String kategoria;
    private String lokalizacja;
    private String opis;

    @JsonProperty("link_spektaklu")
    private String linkSpektaklu;

    @JsonProperty("link_bilety")
    private String linkBilety;
}
