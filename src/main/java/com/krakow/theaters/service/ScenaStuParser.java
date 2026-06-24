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
public class ScenaStuParser {

    private static final String CALENDAR_URL = "https://scenastu.pl/calendar/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public record EventInfo(String url, String title, String date, String time, String ticketUrl) {}

    public List<EventInfo> parseCalendarEvents() throws IOException {
        int year = LocalDate.now().getYear();
        List<EventInfo> allEvents = new ArrayList<>();
        allEvents.addAll(parseMonthEvents(String.format("%d/05", year), "maj"));
        allEvents.addAll(parseMonthEvents(String.format("%d/06", year), "czerwiec"));
        return allEvents;
    }

    public List<EventInfo> parseCalendarEventsForMonths(int year, int... months) throws IOException {
        List<EventInfo> allEvents = new ArrayList<>();
        String[] monthNames = {"", "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
                "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"};
        for (int m : months) {
            String path = String.format("%d/%02d", year, m);
            String name = m < monthNames.length ? monthNames[m] : String.valueOf(m);
            allEvents.addAll(parseMonthEvents(path, name));
        }
        return allEvents;
    }

    private List<EventInfo> parseMonthEvents(String monthPath, String monthName) throws IOException {
        String url = CALENDAR_URL + monthPath;
        log.info("Parsowanie kalendarza Scena STU z: {} ({})", url, monthName);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        List<EventInfo> events = new ArrayList<>();

        Elements rows = doc.select("table.stu-calendar tr.grid");
        log.info("Znaleziono {} wierszy w kalendarzu dla {}", rows.size(), monthName);

        for (Element row : rows) {
            try {
                Element dateTime = row.selectFirst("time.date[datetime]");
                if (dateTime == null) {
                    continue;
                }
                String dateStr = dateTime.attr("datetime");

                Element titleLink = row.selectFirst(".title a[href]");
                if (titleLink == null) {
                    continue;
                }

                titleLink.select("small.note").remove();
                String title = normalize(titleLink.text());
                String showUrl = titleLink.absUrl("href");

                if (title.isEmpty() || showUrl.isEmpty()) {
                    continue;
                }

                Elements showDivs = row.select(".show");
                for (Element showDiv : showDivs) {
                    try {
                        Element timeElement = showDiv.selectFirst("time.time[datetime]");
                        if (timeElement == null) {
                            continue;
                        }
                        String timeStr = timeElement.text();

                        Element ticketLink = showDiv.selectFirst("a.link[href]");
                        String ticketUrl = null;
                        if (ticketLink != null) {
                            ticketUrl = ticketLink.absUrl("href");
                        }

                        EventInfo eventInfo = new EventInfo(showUrl, title, dateStr, timeStr, ticketUrl);
                        events.add(eventInfo);

                        log.debug("Dodano wydarzenie: {} - {} {} - bilety: {}",
                                title, dateStr, timeStr, ticketUrl != null ? "TAK" : "NIE");
                    } catch (Exception e) {
                        log.error("Błąd parsowania showtime: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Błąd parsowania wydarzenia: {}", e.getMessage());
            }
        }

        log.info("Wyekstrahowano {} wydarzeń dla {}", events.size(), monthName);
        return events;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}