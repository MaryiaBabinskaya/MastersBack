package com.krakow.theaters.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ASTParser {

    private static final String REPERTUAR_URL = "https://krakow.ast.krakow.pl/teatr-ast/repertuar/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public record EventInfo(String url, String title, String date, String time, String scene, String ticketUrl, boolean isCancelled) {}

    public List<EventInfo> parseRepertuarEvents() throws IOException {
        log.info("Parsowanie repertuaru AST z: {}", REPERTUAR_URL);
        Document doc = Jsoup.connect(REPERTUAR_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
        List<EventInfo> events = new ArrayList<>();

        Elements rows = doc.select("tr");
        log.info("Znaleziono {} wierszy tabeli", rows.size());
        for (Element row : rows) {
            try {
                Element eventLink = row.selectFirst("a[href*='post_type=spektakle']");
                if (eventLink == null) {
                    continue;
                }

                String url = eventLink.absUrl("href");
                String fullTitle = eventLink.text().trim();

                if (fullTitle.isEmpty()) {
                    continue;
                }

                String title = extractTitleBeforePipe(fullTitle);

                Element dateDiv = row.selectFirst("td.col-date div.ast-rep-date");
                String date = null;
                if (dateDiv != null) {
                    String dateText = normalize(dateDiv.text());
                    if (dateText.matches("\\d{2}\\.\\d{2}")) {
                        String[] parts = dateText.split("\\.");
                        int year = LocalDate.now().getYear();
                        date = year + "-" + parts[1] + "-" + parts[0];
                    }
                }

                Element hourDiv = row.selectFirst("div.ast-rep-hour span");
                String time = null;
                if (hourDiv != null && hourDiv.text().contains("Godzina:")) {
                    time = hourDiv.text().replace("Godzina:", "").trim();
                }

                Elements hourSpans = row.select("div.ast-rep-hour span");
                String scene = null;
                if (hourSpans.size() > 1) {
                    String sceneText = normalize(hourSpans.get(1).text());
                    if (sceneText.startsWith("|")) {
                        sceneText = sceneText.substring(1).trim();
                    }
                    scene = sceneText;
                }

                Element ticketLink = row.selectFirst("td.col-action a[href*='bilety.ast.krakow.pl']");
                String ticketUrl = null;
                if (ticketLink != null) {
                    ticketUrl = ticketLink.absUrl("href");
                }

                boolean isCancelled = !row.select("div.ast-canceled").isEmpty();

                EventInfo eventInfo = new EventInfo(url, title, date, time, scene, ticketUrl, isCancelled);
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

    private String extractTitleBeforePipe(String fullTitle) {
        if (fullTitle.contains("|")) {
            return fullTitle.substring(0, fullTitle.indexOf("|")).trim();
        }
        return fullTitle;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}