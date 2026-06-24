package com.krakow.theaters.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "repertuar")
public class VarieteRepertuar {

    @JacksonXmlProperty(localName = "spektakl")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<VarieteSpektakl> spektakle;
}