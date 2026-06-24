package com.krakow.theaters.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LazniaNowaParser {

    private static final String KALENDARIUM_URL = "https://www.laznianowa.pl/kalendarium";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public record EventInfo(String url, String title, String date, String time, String scene,
                            String ticketUrl, String type, String status) {}

    public List<EventInfo> parseKalendariumEvents() throws IOException {
        log.info("Parsowanie kalendarza Łaźni Nowej z: {}", KALENDARIUM_URL);
        Document doc = Jsoup.connect(KALENDARIUM_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        List<EventInfo> events = new ArrayList<>();

        Elements days = doc.select(".calendarium-day");
        log.info("Znaleziono {} dni w kalendarzu", days.size());

        for (Element day : days) {
            try {
                String dateAttr = day.attr("data-date");
                if (dateAttr.isEmpty()) {
                    continue;
                }

                String date = convertDateFormat(dateAttr);

                Elements eventItems = day.select(".calendarium-item");
                for (Element item : eventItems) {
                    try {
                        Element titleLink = item.selectFirst("a.calendarium-item-title");
                        if (titleLink == null) {
                            continue;
                        }

                        String title = normalize(titleLink.text());
                        String url = titleLink.absUrl("href");

                        if (title.isEmpty() || url.isEmpty()) {
                            continue;
                        }

                        Element timeDiv = item.selectFirst(".calendarium-item-time");
                        String time = timeDiv != null ? normalize(timeDiv.text()) : null;

                        Element typeDiv = item.selectFirst(".calendarium-item-type");
                        String type = typeDiv != null ? normalize(typeDiv.text()) : null;

                        Element stageDiv = item.selectFirst(".calendarium-item-stage");
                        String scene = stageDiv != null ? normalize(stageDiv.text()) : null;

                        String ticketUrl = null;
                        Element paymentDiv = item.selectFirst(".calendarium-item-payment");
                        if (paymentDiv != null) {
                            Element ticketLink = paymentDiv.selectFirst("a[href]");
                            if (ticketLink != null) {
                                ticketUrl = ticketLink.absUrl("href");
                            }
                        }

                        Element statusDiv = item.selectFirst(".calendarium-item-status");
                        String status = statusDiv != null ? normalize(statusDiv.text()) : null;

                        EventInfo eventInfo = new EventInfo(url, title, date, time, scene, ticketUrl, type, status);
                        events.add(eventInfo);

                        log.debug("Dodano wydarzenie: {} - {} {} - scena: {} - bilety: {}",
                                title, date, time, scene, ticketUrl != null ? "TAK" : "NIE");
                    } catch (Exception e) {
                        log.error("Błąd parsowania wydarzenia: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Błąd parsowania dnia: {}", e.getMessage());
            }
        }

        log.info("Wyekstrahowano {} wydarzeń", events.size());
        return events;
    }

    private String convertDateFormat(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            String[] parts = dateStr.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
            String month = parts[1];
            String year = parts[2];

            return year + "-" + month + "-" + day;
        } catch (Exception e) {
            log.error("Błąd konwersji daty: {}", dateStr);
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}