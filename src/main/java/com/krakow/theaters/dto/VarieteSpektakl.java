package com.krakow.theaters.dto;

import lombok.Data;

@Data
public class VarieteSpektakl {
    private String id;
    private String nazwa;
    private String kiedy;
    private String cena;
    private String typ;
    private String opis;
    private String ticketUrl;
}
