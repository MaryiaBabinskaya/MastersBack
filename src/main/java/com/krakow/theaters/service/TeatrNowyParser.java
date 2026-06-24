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
public class TeatrNowyParser {

    private static final String REPERTUAR_URL = "https://teatrnowy.com.pl/repertuar/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public record EventInfo(String url, String title, String date, String time, String scene, String ticketUrl) {}

    public List<EventInfo> parseRepertuarEventsForMonths(int year, int... months) throws IOException {
        List<EventInfo> all = parseRepertuarEvents();
        return all.stream()
                .filter(e -> e.date() != null && matchesYearMonths(e.date(), year, months))
                .toList();
    }

    private boolean matchesYearMonths(String date, int year, int[] months) {
        String yearStr = String.valueOf(year);
        if (!date.startsWith(yearStr + "-")) return false;
        for (int m : months) {
            String monthStr = String.format("%02d", m);
            if (date.startsWith(yearStr + "-" + monthStr)) return true;
        }
        return false;
    }

    public List<EventInfo> parseRepertuarEvents() throws IOException {
        log.info("Parsowanie repertuaru Teatru Nowego z: {}", REPERTUAR_URL);
        Document doc = Jsoup.connect(REPERTUAR_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        List<EventInfo> events = new ArrayList<>();
        Elements eventRows = doc.select(".rep-list-scena-backlight");
        log.info("Znaleziono {} wydarzeń w repertuarze", eventRows.size());

        for (Element row : eventRows) {
            try {
                Element dateDiv = row.selectFirst(".rep-list-date");
                String dateText = dateDiv != null ? normalize(dateDiv.text()) : null;
                String date = parseDateToISO(dateText);

                Element titleLink = row.selectFirst(".rep-list-title a.repertoire-lists-a");
                if (titleLink == null) {
                    continue;
                }

                String title = normalize(titleLink.text());
                String url = titleLink.absUrl("href");

                if (title.isEmpty() || url.isEmpty()) {
                    continue;
                }

                Elements scenaElements = row.select(".rep-list-scena .repertoire-lists-scena");
                String scene = null;
                String time = null;

                if (!scenaElements.isEmpty()) {
                    scene = normalize(scenaElements.get(0).text());
                }
                if (scenaElements.size() >= 2) {
                    time = normalize(scenaElements.get(1).text());
                }

                Element ticketLink = row.selectFirst(".rep-list-button a.button.repertoire-button-rezerwuj");
                String ticketUrl = null;
                if (ticketLink != null) {
                    ticketUrl = ticketLink.absUrl("href");
                    if (ticketUrl.isEmpty()) {
                        ticketUrl = null;
                    }
                }

                EventInfo eventInfo = new EventInfo(url, title, date, time, scene, ticketUrl);
                events.add(eventInfo);

                log.debug("Dodano wydarzenie: {} - {} {} - scena: {} - bilety: {}",
                        title, date, time, scene, ticketUrl != null ? "TAK" : "NIE");
            } catch (Exception e) {
                log.error("Błąd parsowania wydarzenia: {}", e.getMessage());
            }
        }

        log.info("Wyekstrahowano {} wydarzeń", events.size());
        return events;
    }

    private String parseDateToISO(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> monthMap = getStringStringMap();

            String[] parts = dateStr.trim().split("\\s+");
            if (parts.length != 3) {
                log.warn("Nieprawidłowy format daty: {}", dateStr);
                return null;
            }

            String day = parts[0];
            String monthName = parts[1].toLowerCase();
            String year = parts[2];
            String month = monthMap.get(monthName);

            if (month == null) {
                log.warn("Nieznana nazwa miesiąca: {}", monthName);
                return null;
            }

            if (day.length() == 1) {
                day = "0" + day;
            }

            return year + "-" + month + "-" + day;
        } catch (Exception e) {
            log.error("Błąd konwersji daty: {}", dateStr, e);
            return null;
        }
    }

    private static Map<String, String> getStringStringMap() {
        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("stycznia", "01");
        monthMap.put("lutego", "02");
        monthMap.put("marca", "03");
        monthMap.put("kwietnia", "04");
        monthMap.put("maja", "05");
        monthMap.put("czerwca", "06");
        monthMap.put("lipca", "07");
        monthMap.put("sierpnia", "08");
        monthMap.put("września", "09");
        monthMap.put("października", "10");
        monthMap.put("listopada", "11");
        monthMap.put("grudnia", "12");
        return monthMap;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}