package com.krakow.theaters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeatrWKrakowieAjaxClient {

    private static final String AJAX_URL = "https://teatrwkrakowie.pl/ajax/pl/repertoireList";
    private static final String BASE_URL = "https://teatrwkrakowie.pl";
    private static final String REFERER = "https://teatrwkrakowie.pl/repertuar";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int TIMEOUT_MS = 30000;

    private final ObjectMapper objectMapper;

    public Document fetchMonth(String startDate) throws IOException {
        log.info("Pobieranie repertuaru AJAX dla startDate={}", startDate);

        Connection.Response response = Jsoup.connect(AJAX_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", REFERER)
                .header("Origin", BASE_URL)
                .data(buildRequestParams(startDate))
                .ignoreContentType(true)
                .method(Connection.Method.POST)
                .execute();

        String body = response.body();
        if (body.isBlank()) {
            throw new IllegalStateException("Pusta odpowiedź z serwera dla startDate=" + startDate);
        }

        return parseTemplateFromResponse(body, startDate);
    }

    private Map<String, String> buildRequestParams(String startDate) {
        return Map.of(
                "filters[0][type]", "type",
                "filters[0][value]", "current",
                "filters[1][type]", "EventType",
                "filters[1][value]", "all",
                "filters[2][type]", "Event",
                "filters[2][value]", "all",
                "filters[3][type]", "search",
                "filters[3][value]", "",
                "startDate", startDate
        );
    }

    private Document parseTemplateFromResponse(String body, String startDate) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String template = root.path("template").asText();

        if (template.isBlank()) {
            throw new IllegalStateException("Brak pola 'template' w odpowiedzi dla startDate=" + startDate);
        }

        log.info("Pomyślnie przetworzono szablon dla {}", startDate);
        return Jsoup.parse(template, BASE_URL);
    }
}