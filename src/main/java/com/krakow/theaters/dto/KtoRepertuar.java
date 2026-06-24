package com.krakow.theaters.dto;

import lombok.Data;
import java.util.List;

@Data
public class KtoRepertuar {
    private String miesiac;
    private List<KtoEvent> wydarzenia;
}
