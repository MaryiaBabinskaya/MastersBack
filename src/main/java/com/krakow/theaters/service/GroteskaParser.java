package com.krakow.theaters.service;

import com.krakow.theaters.dto.GroteskaEvent;
import com.krakow.theaters.dto.GroteskaRepertuar;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GroteskaParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    private static final String TICKET_API_URL_TEMPLATE =
            "https://kup-bilet.groteska.pl/msi/mvc/pl/details/%d?sort=Flow&date=%d-%02d&dateStart=0";
    private static final String TICKET_BASE_URL_TEMPLATE =
            "https://kup-bilet.groteska.pl/msi/mvc/pl/details/%d";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final int[] SPECTACLE_IDS = {
            369, 358, 333, 348, 344, 345, 339, 289, 168, 340, 16, 286, 17, 318
    };

    private static final Pattern EVENT_JS_PATTERN = Pattern.compile(
            "'Id':\\s*(\\d+)[^}]+?'Name':\\s*'([^']+)'[^}]+?'Date':\\s*'([^']+)'[^}]+?'Hour':\\s*'([^']+)'[^}]+?'PosterId':\\s*(\\d+)",
            Pattern.DOTALL
    );

    public GroteskaRepertuar parseRepertuar(int month, int year) {
        log.info("Parsowanie repertuaru Groteska dla {}-{}", month, year);

        GroteskaRepertuar repertuar = new GroteskaRepertuar();
        repertuar.setMiesiac(String.format("%02d-%d", month, year));

        YearMonth targetMonth = YearMonth.of(year, month);

        List<GroteskaEvent> wydarzenia = Arrays.stream(SPECTACLE_IDS)
                .mapToObj(spectacleId -> parseSpectacleEventsSafely(spectacleId, targetMonth))
                .flatMap(List::stream)
                .toList();

        repertuar.setWydarzenia(wydarzenia);
        log.info("Sparsowano łącznie {} wydarzeń dla miesiąca {}-{}", wydarzenia.size(), month, year);

        return repertuar;
    }

    private List<GroteskaEvent> parseSpectacleEventsSafely(int spectacleId, YearMonth targetMonth) {
        try {
            List<GroteskaEvent> events = parseSpectacleEvents(spectacleId, targetMonth);
            log.debug("Dodano {} wydarzeń dla spektaklu ID {}", events.size(), spectacleId);
            return events;
        } catch (Exception e) {
            log.warn("Błąd podczas parsowania spektaklu ID {}: {}", spectacleId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<GroteskaEvent> parseSpectacleEvents(int spectacleId, YearMonth targetMonth) throws IOException {
        String url = String.format(TICKET_API_URL_TEMPLATE, spectacleId, targetMonth.getYear(), targetMonth.getMonthValue());
        log.debug("Fetching: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        return extractEventsFromHtml(doc.html(), spectacleId, targetMonth);
    }

    private List<GroteskaEvent> extractEventsFromHtml(String html, int spectacleId, YearMonth targetMonth) {
        List<GroteskaEvent> events = new ArrayList<>();
        Matcher matcher = EVENT_JS_PATTERN.matcher(html);

        while (matcher.find()) {
            String eventId = matcher.group(1);
            String name = matcher.group(2);
            String date = matcher.group(3);
            String hour = matcher.group(4);
            String posterId = matcher.group(5);

            if (isDateInMonth(date, targetMonth)) {
                GroteskaEvent event = buildEvent(eventId, name, date, hour, posterId, spectacleId);
                events.add(event);
                log.debug("Dodano wydarzenie: {} - {} {}", name, date, hour);
            }
        }
        return events;
    }

    private GroteskaEvent buildEvent(String eventId, String name, String date, String hour, String posterId, int spectacleId) {
        GroteskaEvent event = new GroteskaEvent();
        event.setEventId(eventId);
        event.setTytul(normalize(name));
        event.setData(date);
        event.setGodzina(hour);
        event.setPosterId(posterId);
        event.setLinkBilety(String.format(TICKET_BASE_URL_TEMPLATE, spectacleId));
        return event;
    }

    private boolean isDateInMonth(String dateStr, YearMonth targetMonth) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            return YearMonth.from(date).equals(targetMonth);
        } catch (DateTimeParseException e) {
            log.warn("Nie można sparsować daty do walidacji miesiąca: {}", dateStr);
            return false;
        }
    }

    public GroteskaRepertuar parseRepertuarFromWebsite(int month, int year) throws IOException {
        String url = String.format("https://www.groteska.pl/repertuar/%d/%d", month, year);
        log.info("Parsowanie repertuaru Groteska ze strony: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        GroteskaRepertuar repertuar = new GroteskaRepertuar();
        repertuar.setMiesiac(String.format("%02d-%d", month, year));
        List<GroteskaEvent> events = new ArrayList<>();

        Elements rows = doc.select(".m-repertoire .row.align-items-center");
        log.info("Znaleziono {} wierszy repertuaru", rows.size());

        for (Element row : rows) {
            try {
                Elements colAutos = row.select(".col-auto");
                if (colAutos.size() < 2) continue;

                String dayText = normalize(colAutos.get(0).text()).split("\\s+")[0];

                Element timeEl = colAutos.get(1);
                String hourText = normalize(timeEl.ownText());
                String supText = timeEl.select("sup").text();
                String time = String.format("%s:%s", hourText.isEmpty() ? "00" : hourText,
                        supText.isEmpty() ? "00" : supText);

                Element titleLink = row.selectFirst("a.text-uppercase.col");
                if (titleLink == null) continue;
                String title = normalize(titleLink.text());
                String spectacleUrl = "https://www.groteska.pl" + titleLink.attr("href");

                Elements cols = row.select("div.col:not(.col-auto):not(.col-12)");
                String scene = cols.isEmpty() ? null : normalize(cols.get(0).text());

                Element ticketLink = row.selectFirst("a[href*=kup-bilet.groteska.pl]");
                String ticketUrl = ticketLink != null ? ticketLink.attr("href") : spectacleUrl;

                String dateStr = String.format("%02d.%02d.%d", Integer.parseInt(dayText), month, year);

                GroteskaEvent event = new GroteskaEvent();
                event.setTytul(title);
                event.setData(dateStr);
                event.setGodzina(time);
                event.setScena(scene);
                event.setLinkBilety(ticketUrl);
                events.add(event);
                log.debug("Dodano: {} {} {} - {}", title, dateStr, time, scene);
            } catch (Exception e) {
                log.warn("Błąd parsowania wiersza: {}", e.getMessage());
            }
        }

        repertuar.setWydarzenia(events);
        log.info("Wyekstrahowano {} wydarzeń z {}/{}", events.size(), month, year);
        return repertuar;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}