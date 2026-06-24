package com.krakow.theaters.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BarakahSpektakl {
    private String data;
    private String godzina;
    private String tytul;
    private String kategoria;

    @JsonProperty("link_poster")
    private String linkPoster;

    @JsonProperty("link_bilety")
    private String linkBilety;
}
