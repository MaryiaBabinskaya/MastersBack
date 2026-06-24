package com.krakow.theaters.service;

import com.krakow.theaters.dto.KtoEvent;
import com.krakow.theaters.dto.KtoRepertuar;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KtoParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    private static final String DEFAULT_LOCATION = "Teatr KTO / Zamoyskiego 50";
    private static final String CATEGORY_KIDS = "dla dzieci";
    private static final String CATEGORY_WORKSHOP = "warsztaty";

    private static final Pattern URL_MONTH_PATTERN = Pattern.compile("/(\\w+-\\d{4})/");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{1,2}\\s+\\p{L}{2,3}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}[:.\\s]\\d{2}$");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(Teatr KTO[^|\\n]*|Zamoyskiego[^|\\n]*)");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(".*(reż|scen|prowadzenie|warsztaty teatralne|chor|choreografia|scenografia).*");

    private static final Set<String> NAVIGATION_KEYWORDS = Set.of(
            "MAJ", "CZERWIEC", "KWIECIEŃ", "EN", "REPERTUAR", "BILETY"
    );

    private static class ParseContext {
        String currentDate = null;
        String currentTime = null;
    }

    public KtoRepertuar parseRepertuar(String url) throws IOException {
        log.info("Parsowanie repertuaru KTO z URL: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        KtoRepertuar repertuar = new KtoRepertuar();
        String month = extractMonthFromUrl(url);
        repertuar.setMiesiac(month);

        List<KtoEvent> wydarzenia = new ArrayList<>();
        Elements allHeadings = doc.select(".elementor-heading-title");
        ParseContext context = new ParseContext();

        for (int i = 0; i < allHeadings.size(); i++) {
            Element heading = allHeadings.get(i);
            String text = normalize(heading.text());

            if (text.isEmpty() || isNavigationElement(text) || isDescriptionElement(text)) {
                continue;
            }

            if (isDate(text)) {
                context.currentDate = text;
                context.currentTime = null;
                log.debug("Znaleziono datę: {}", context.currentDate);
                continue;
            }

            if (isTime(text)) {
                context.currentTime = normalizeTime(text);
                log.debug("Znaleziono godzinę: {}", context.currentTime);
                continue;
            }

            if (context.currentDate != null && context.currentTime != null) {
                KtoEvent event = buildEvent(heading, text, context.currentDate, context.currentTime, allHeadings, i);
                wydarzenia.add(event);

                log.debug("Dodano wydarzenie: {} - {} {}", event.getTytul(), event.getData(), event.getGodzina());
                context.currentTime = null;
            }
        }

        repertuar.setWydarzenia(wydarzenia);
        log.info("Sparsowano {} wydarzeń dla miesiąca {}", wydarzenia.size(), month);
        return repertuar;
    }

    private KtoEvent buildEvent(Element titleHeading, String titleText, String date, String time, Elements allHeadings, int currentIndex) {
        KtoEvent event = new KtoEvent();
        event.setData(date);
        event.setGodzina(time);
        event.setTytul(titleText);

        extractSpectacleLink(titleHeading, event);
        enrichEventWithSiblingDetails(titleHeading, event, allHeadings, currentIndex);
        determineCategory(titleHeading, titleText, event);
        extractAndSetLocation(titleHeading, event);

        return event;
    }

    private void extractSpectacleLink(Element heading, KtoEvent event) {
        Element link = heading.selectFirst("a");
        if (link != null) {
            String href = link.absUrl("href");
            if (href.contains("teatrkto.pl") && !URL_MONTH_PATTERN.matcher(href).find()) {
                event.setLinkSpektaklu(href);
            }
        }
    }

    private void enrichEventWithSiblingDetails(Element heading, KtoEvent event, Elements allHeadings, int currentIndex) {
        Element parent = heading.parent();
        if (parent == null) return;

        StringBuilder descriptionBuilder = new StringBuilder();

        int limit = Math.min(currentIndex + 10, allHeadings.size());
        for (int j = currentIndex + 1; j < limit; j++) {
            Element nextHeading = allHeadings.get(j);
            String nextText = normalize(nextHeading.text());

            if (isDate(nextText) || isTime(nextText)) {
                break;
            }

            Element nextLink = nextHeading.selectFirst("a");
            if (nextLink != null && nextLink.text().toLowerCase().contains("bilet")) {
                event.setLinkBilety(nextLink.absUrl("href"));
            }

            if (isDescriptionElement(nextText)) {
                if (!descriptionBuilder.isEmpty()) descriptionBuilder.append("\n");
                descriptionBuilder.append(nextText);
            }
        }

        if (!descriptionBuilder.isEmpty()) {
            event.setOpis(descriptionBuilder.toString());
        }
    }

    private void determineCategory(Element heading, String titleText, KtoEvent event) {
        String titleLower = titleText.toLowerCase();
        if (titleLower.contains("kto?senior") || titleLower.contains("kto senior")) {
            event.setKategoria(CATEGORY_WORKSHOP);
            return;
        }

        Element parent = heading.parent();
        if (parent != null && parent.parent() != null) {
            Element container = parent.parent();

            boolean isForKids = container.select("img").stream()
                    .map(img -> img.attr("src"))
                    .anyMatch(src -> src.contains("header-dla-dzieci"));

            if (isForKids) {
                event.setKategoria(CATEGORY_KIDS);
                return;
            }

            if (container.text().toLowerCase().contains(CATEGORY_WORKSHOP)) {
                event.setKategoria(CATEGORY_WORKSHOP);
            }
        }
    }

    private void extractAndSetLocation(Element heading, KtoEvent event) {
        Element parent = heading.parent();
        if (parent != null && parent.parent() != null) {
            String fullText = parent.parent().text();
            Matcher matcher = LOCATION_PATTERN.matcher(fullText);
            if (matcher.find()) {
                event.setLokalizacja(matcher.group(1).trim());
                return;
            }
        }
        event.setLokalizacja(DEFAULT_LOCATION);
    }

    private String extractMonthFromUrl(String url) {
        Matcher matcher = URL_MONTH_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isDate(String text) {
        return DATE_PATTERN.matcher(text.trim()).matches();
    }

    private boolean isTime(String text) {
        return TIME_PATTERN.matcher(text.trim()).matches();
    }

    private boolean isNavigationElement(String text) {
        return NAVIGATION_KEYWORDS.contains(text.toUpperCase());
    }

    private boolean isDescriptionElement(String text) {
        return DESCRIPTION_PATTERN.matcher(text.toLowerCase()).matches();
    }

    private String normalizeTime(String time) {
        if (time == null) return "";

        String normalized = time.replaceAll("[.\\s]", ":");
        String[] parts = normalized.split(":");

        if (parts.length == 2 && parts[0].length() == 1) {
            normalized = "0" + normalized;
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}