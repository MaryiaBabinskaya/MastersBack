package com.krakow.theaters.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StaryParser {

    private static final String REPERTUAR_URL = "https://bilety.stary.pl/repertuar/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public record EventInfo(String url, String title) {}

    public List<EventInfo> parseRepertuarEvents() throws IOException {
        log.info("Parsowanie repertuaru Stary Teatr z: {}", REPERTUAR_URL);
        Document doc = Jsoup.connect(REPERTUAR_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        Map<String, String> eventMap = new HashMap<>();
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.absUrl("href");
            if (href.contains("/wydarzenie/") && href.contains("?id=")) {
                String title = extractTitle(link);
                if (title != null && !title.isEmpty()) {
                    eventMap.put(href, title);
                    log.debug("Dodano wydarzenie: {} - {}", title, href);
                }
            }
        }

        List<EventInfo> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : eventMap.entrySet()) {
            result.add(new EventInfo(entry.getKey(), entry.getValue()));
        }

        log.info("Wyekstrahowano {} unikalnych wydarzeń", result.size());
        return result;
    }

    public List<String> parseRepertuarEventUrls() throws IOException {
        List<EventInfo> events = parseRepertuarEvents();
        List<String> urls = new ArrayList<>();
        for (EventInfo event : events) {
            urls.add(event.url());
        }
        return urls;
    }

    private String extractTitle(Element link) {
        String titleAttr = link.attr("title");
        if (!titleAttr.isEmpty()) {
            String cleaned = titleAttr.replaceFirst("^Spektakl:\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                return normalize(cleaned);
            }
        }

        String text = link.text();
        if (!text.isEmpty()) {
            return normalize(text);
        }

        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}