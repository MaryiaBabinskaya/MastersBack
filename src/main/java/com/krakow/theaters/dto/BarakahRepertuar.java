package com.krakow.theaters.dto;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

@Data
public class BarakahRepertuar {
    private List<BarakahSpektakl> spektakle = new ArrayList<>();
}
