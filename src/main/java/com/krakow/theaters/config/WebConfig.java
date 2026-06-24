package com.krakow.theaters.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String UPLOADS_HANDLER = "/uploads/**";
    private static final String UPLOADS_LOCATION = "file:uploads/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(UPLOADS_HANDLER)
                .addResourceLocations(UPLOADS_LOCATION);
    }
}
