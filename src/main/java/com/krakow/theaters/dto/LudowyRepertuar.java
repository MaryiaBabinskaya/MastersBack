package com.krakow.theaters.dto;

import lombok.Data;
import java.util.List;

@Data
public class LudowyRepertuar {
    private String miesiac;
    private List<LudowyEvent> wydarzenia;
}
