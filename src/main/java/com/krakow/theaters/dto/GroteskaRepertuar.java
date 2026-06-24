package com.krakow.theaters.dto;

import lombok.Data;
import java.util.List;

@Data
public class GroteskaRepertuar {
    private String miesiac;
    private List<GroteskaEvent> wydarzenia;
}
