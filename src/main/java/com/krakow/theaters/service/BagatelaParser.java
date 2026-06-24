package com.krakow.theaters.service;

import com.krakow.theaters.dto.BagatelaRepertuar;
import com.krakow.theaters.dto.BagatelaSpektakl;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BagatelaParser {

    private static final String REPERTUAR_URL = "https://bagatela.pl/repertuar-teatru/repertuar";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final int MAX_MONTHS = 6;

    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final Pattern FALLBACK_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{4})\\s*(?:godz\\.|)\\s*(\\d{1,2}):(\\d{1,2})"
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy 'godz.' HH:mm"),
            DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", new Locale("pl", "PL"))
    );

    private static final DateTimeFormatter TARGET_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public BagatelaRepertuar parseRepertuar() throws IOException {
        BagatelaRepertuar repertuar = new BagatelaRepertuar();
        List<BagatelaSpektakl> spektakle = new ArrayList<>();
        Set<String> visitedUrls = new HashSet<>();

        String currentUrl = REPERTUAR_URL;
        int monthCount = 0;

        while (currentUrl != null && monthCount < MAX_MONTHS && visitedUrls.add(currentUrl)) {
            Document doc = fetchDocument(currentUrl);

            List<BagatelaSpektakl> monthlySpektakle = doc.select(".spectacle-tile.type-3").stream()
                    .map(eventEl -> parseSpektakl(eventEl, doc))
                    .filter(Objects::nonNull)
                    .filter(s -> s.getNazwa() != null)
                    .toList();

            spektakle.addAll(monthlySpektakle);

            currentUrl = getNextMonthUrl(doc);
            if (currentUrl != null) {
                monthCount++;
            }
        }

        repertuar.setSpektakle(spektakle);
        return repertuar;
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    private String getNextMonthUrl(Document doc) {
        Element nextButton = doc.selectFirst("a.months-switcher-button.next:not(.disabled)");
        if (nextButton != null && nextButton.hasAttr("href")) {
            String nextUrl = nextButton.absUrl("href");
            return !nextUrl.isBlank() ? nextUrl : null;
        }
        return null;
    }

    private BagatelaSpektakl parseSpektakl(Element eventEl, Document doc) {
        try {
            BagatelaSpektakl spektakl = new BagatelaSpektakl();

            spektakl.setNazwa(extractTitle(eventEl));
            spektakl.setOpis(extractDetailUrl(eventEl)); // Store URL temporarily in opis field
            spektakl.setKiedy(extractDateTime(eventEl, doc));
            spektakl.setTyp(extractStage(eventEl));
            spektakl.setTicketUrl(extractTicketUrl(eventEl));

            if (spektakl.getNazwa() != null) {
                spektakl.setId("BG-" + generateId(spektakl.getNazwa()));
            }

            return spektakl;
        } catch (Exception e) {
            log.warn("Błąd podczas parsowania spektaklu: {}", e.getMessage());
            return null;
        }
    }

    private String extractTitle(Element eventEl) {
        String title = eventEl.select(".title-container h6.title-part").stream()
                .map(Element::text)
                .map(this::clean)
                .collect(Collectors.joining(" "));
        return title.isBlank() ? null : title;
    }

    private String extractDetailUrl(Element eventEl) {
        Element link = eventEl.selectFirst(".title-container .spectacle-title-container");
        return link != null && !link.absUrl("href").isBlank() ? link.absUrl("href") : null;
    }

    private String extractDateTime(Element eventEl, Document doc) {
        Element dateContainer = eventEl.selectFirst(".date-container");
        if (dateContainer == null) return null;

        Element dateEl = dateContainer.selectFirst("span.date");
        Element hourEl = dateContainer.selectFirst("span.hour-value");

        if (dateEl != null && hourEl != null) {
            String date = clean(dateEl.text());
            String time = clean(hourEl.text());
            String yearStr = extractYear(doc);

            String fullDateTime = date + "." + yearStr + " " + time;
            return parseDateString(fullDateTime);
        }
        return null;
    }

    private String extractYear(Document doc) {
        Element monthEl = doc.selectFirst(".months-switcher__current");
        if (monthEl != null) {
            Matcher matcher = YEAR_PATTERN.matcher(monthEl.text());
            if (matcher.find()) return matcher.group(1);
        }
        return String.valueOf(LocalDate.now().getYear());
    }

    private String extractStage(Element eventEl) {
        Element stageEl = eventEl.selectFirst(".stage-container .value span");
        return stageEl != null ? clean(stageEl.text()) : null;
    }

    private String extractTicketUrl(Element eventEl) {
        Element ticketButton = eventEl.selectFirst("a[href*='bilety-bagatela.com.pl']");
        if (ticketButton != null && !ticketButton.absUrl("href").isBlank()) {
            return ticketButton.absUrl("href");
        }
        return null;
    }

    private String parseDateString(String dateText) {
        if (dateText == null || dateText.isBlank()) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(dateText.trim(), formatter);
                return dateTime.format(TARGET_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }

        Matcher matcher = FALLBACK_DATE_PATTERN.matcher(dateText);
        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));

                LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute);
                return dateTime.format(TARGET_FORMATTER);
            } catch (Exception e) {
                log.warn("Nie udało się sparsować daty fallbackiem: {}", dateText);
            }
        }
        return dateText;
    }

    private String generateId(String name) {
        if (name == null || name.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return name.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(name.length(), 10));
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}