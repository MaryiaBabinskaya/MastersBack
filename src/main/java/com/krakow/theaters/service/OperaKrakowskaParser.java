package com.krakow.theaters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OperaKrakowskaParser {

    private static final String AJAX_URL = "https://opera.krakow.pl/ajax/repertuar";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record EventInfo(String title, String composer, String type, String place,
                            String date, String time, String ticketUrl, String slug) {}

    public List<EventInfo> parseRepertuarEvents() throws IOException {
        int year = LocalDate.now().getYear();
        List<EventInfo> allEvents = new ArrayList<>();
        allEvents.addAll(parseMonthEvents(5, year, "maj"));
        allEvents.addAll(parseMonthEvents(6, year, "czerwiec"));
        allEvents.addAll(parseMonthEvents(7, year, "lipiec"));
        allEvents.addAll(parseMonthEvents(8, year, "sierpień"));
        return allEvents;
    }

    public List<EventInfo> parseRepertuarEventsForMonth(int month, int year) throws IOException {
        String monthName = switch (month) {
            case 1 -> "styczeń"; case 2 -> "luty"; case 3 -> "marzec";
            case 4 -> "kwiecień"; case 5 -> "maj"; case 6 -> "czerwiec";
            case 7 -> "lipiec"; case 8 -> "sierpień"; case 9 -> "wrzesień";
            case 10 -> "październik"; case 11 -> "listopad"; case 12 -> "grudzień";
            default -> String.valueOf(month);
        };
        return parseMonthEvents(month, year, monthName);
    }

    private List<EventInfo> parseMonthEvents(int month, int year, String monthName) throws IOException {
        String url = String.format("%s?month=%02d&year=%d", AJAX_URL, month, year);
        log.info("Parsowanie repertuaru Opera Krakowska: {} ({})", url, monthName);

        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://opera.krakow.pl/")
                .ignoreContentType(true)
                .timeout(TIMEOUT_MS)
                .execute();

        String json = response.body();

        if (json.startsWith("<script>")) {
            int scriptEnd = json.indexOf("</script>");
            if (scriptEnd > 0) {
                json = json.substring(scriptEnd + 9);
            }
        }

        List<EventInfo> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode performancesNode = root.get("performances");

            if (performancesNode != null && performancesNode.isArray()) {
                log.info("Znaleziono {} wydarzeń dla {}", performancesNode.size(), monthName);

                for (JsonNode performance : performancesNode) {
                    try {
                        JsonNode dataNode = performance.get("o");
                        if (dataNode == null) dataNode = performance.get("0");
                        if (dataNode == null) {
                            continue;
                        }

                        String dateStr = null;
                        JsonNode dateNode = dataNode.get("date");
                        if (dateNode != null && dateNode.has("date")) {
                            String fullDate = dateNode.get("date").asText();
                            if (fullDate.length() >= 10) {
                                dateStr = fullDate.substring(0, 10);
                            }
                        }

                        String timeStr = null;
                        JsonNode timeNode = dataNode.get("time");
                        if (timeNode != null && timeNode.has("date")) {
                            String fullTime = timeNode.get("date").asText();
                            if (fullTime.length() >= 16) {
                                timeStr = fullTime.substring(11, 16);
                            }
                        }

                        String ticketUrl = null;
                        if (dataNode.has("ticketUrl")) {
                            ticketUrl = dataNode.get("ticketUrl").asText();
                        }

                        String title = performance.has("title") ? performance.get("title").asText() : null;
                        String composer = performance.has("composer") ? performance.get("composer").asText() : null;
                        String type = performance.has("type") ? performance.get("type").asText() : null;
                        String place = performance.has("place") ? performance.get("place").asText() : null;
                        String slug = performance.has("slug") ? performance.get("slug").asText() : null;

                        if (title == null || dateStr == null || timeStr == null) {
                            log.warn("Pomijam wydarzenie z brakującymi danymi");
                            continue;
                        }

                        EventInfo event = new EventInfo(title, composer, type, place,
                                dateStr, timeStr, ticketUrl, slug);
                        events.add(event);

                        log.debug("Dodano wydarzenie: {} - {} {} - {}", title, dateStr, timeStr, place);

                    } catch (Exception e) {
                        log.error("Błąd parsowania wydarzenia: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("Brak węzła 'performances' w odpowiedzi JSON");
            }

        } catch (Exception e) {
            log.error("Błąd parsowania JSON dla {}: {}", monthName, e.getMessage());
        }

        log.info("Wyekstrahowano {} wydarzeń dla {}", events.size(), monthName);
        return events;
    }
}