package com.krakow.theaters.service;

import com.krakow.theaters.dto.BarakahRepertuar;
import com.krakow.theaters.dto.BarakahSpektakl;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BarakahParser {

    private static final String REPERTUAR_URL = "https://teatrbarakah.com/repertuar/";
    private static final String DEFAULT_TICKET_URL = "https://bilety.teatrbarakah.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 30000;

    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("(\\d{1,2})\\s+(\\w+)(?:\\s+(\\d{4}))?\\s+(\\d{1,2}:\\d{2})(?::\\d{2})?");

    private static final Set<String> KNOWN_CATEGORIES = Set.of(
            "spektakle", "koncerty", "wystawy", "warsztaty", "rozważania",
            "spotkania", "projekcje", "inne", "gościnnie", "goscinnie"
    );

    private static final Set<String> NON_EVENT_PATHS = Set.of(
            "/category/", "/tag/", "/author/", "/feed/", "/wp-", "/page/",
            "/kontakt", "/o-teatrze", "/historia", "/zespol", "/bilety",
            "/repertuar/", "facebook.com", "instagram.com", "youtube.com", "twitter.com"
    );

    private static final Map<String, String> POLISH_MONTHS = Map.ofEntries(
            Map.entry("stycznia", "01"), Map.entry("styczeń", "01"),
            Map.entry("lutego", "02"), Map.entry("luty", "02"),
            Map.entry("marca", "03"), Map.entry("marzec", "03"),
            Map.entry("kwietnia", "04"), Map.entry("kwiecień", "04"),
            Map.entry("maja", "05"), Map.entry("maj", "05"),
            Map.entry("czerwca", "06"), Map.entry("czerwiec", "06"),
            Map.entry("lipca", "07"), Map.entry("lipiec", "07"),
            Map.entry("sierpnia", "08"), Map.entry("sierpień", "08"),
            Map.entry("września", "09"), Map.entry("wrzesień", "09"),
            Map.entry("października", "10"), Map.entry("październik", "10"),
            Map.entry("listopada", "11"), Map.entry("listopad", "11"),
            Map.entry("grudnia", "12"), Map.entry("grudzień", "12")
    );

    private record ParsedDateTime(String date, String time) {}

    public BarakahRepertuar parseRepertuar() throws IOException {
        log.info("Rozpoczynam parsowanie repertuaru Barakah ze strony: {}", REPERTUAR_URL);

        Document doc = Jsoup.connect(REPERTUAR_URL)
                .userAgent(USER_AGENT)
                .referrer("https://teatrbarakah.com/")
                .timeout(TIMEOUT_MS)
                .get();

        Element container = findContainer(doc);
        Elements eventLinks = findEventLinks(container);
        log.info("Znaleziono {} potencjalnych linków do wydarzeń", eventLinks.size());

        List<BarakahSpektakl> spektakle = new ArrayList<>();
        for (Element link : eventLinks) {
            try {
                BarakahSpektakl spektakl = parseEventCard(link);
                if (spektakl != null && spektakl.getTytul() != null && !spektakl.getTytul().isBlank()) {
                    spektakle.add(spektakl);
                    log.debug("Dodano spektakl: {} - {} {}", spektakl.getTytul(), spektakl.getData(), spektakl.getGodzina());
                }
            } catch (Exception e) {
                log.warn("Błąd podczas parsowania karty wydarzenia: {}", e.getMessage());
            }
        }

        log.info("Sparsowano łącznie {} spektakli z repertuaru Barakah", spektakle.size());
        BarakahRepertuar repertuar = new BarakahRepertuar();
        repertuar.setSpektakle(spektakle);
        return repertuar;
    }

    private Element findContainer(Document doc) {
        Element container = doc.selectFirst(".barakah-spektakle");
        if (container != null) return container;

        log.warn("Nie znaleziono kontenera .barakah-spektakle, szukam alternatyw...");
        container = doc.selectFirst(".entry-content, main, #main, .site-main");

        return container != null ? container : doc.body();
    }

    private Elements findEventLinks(Element container) {
        Elements links = container.select("a[href*='/spektakle/'], a[href*='/wydarzenie/'], a[href*='/event/']");
        if (links.isEmpty()) {
            log.warn("Brak linków po selektorze /spektakle/, rozszerzam wyszukiwanie...");
            return container.select("a[href]");
        }
        return links;
    }

    private BarakahSpektakl parseEventCard(Element link) {
        String href = link.absUrl("href");
        if (href.isBlank() || isMenuOrNavLink(href)) {
            return null;
        }

        BarakahSpektakl spektakl = new BarakahSpektakl();
        spektakl.setTytul(extractTitle(link));
        spektakl.setKategoria(extractKategoria(link));
        spektakl.setLinkPoster(extractPosterUrl(link));
        spektakl.setLinkBilety(extractTicketUrl(link));

        ParsedDateTime dateTime = extractDateTime(link);
        if (dateTime != null) {
            spektakl.setData(dateTime.date());
            spektakl.setGodzina(dateTime.time());
        }

        return spektakl;
    }

    private String extractTitle(Element link) {
        Element titleEl = link.selectFirst(".title-barakah-spektakle");
        if (titleEl != null && !titleEl.text().isBlank()) {
            return normalize(titleEl.text());
        }

        String fallbackTitle = link.childNodes().stream()
                .filter(this::isValidTextNodeForTitle)
                .map(node -> node instanceof TextNode ? ((TextNode) node).text() : ((Element) node).text())
                .map(this::normalize)
                .filter(text -> !text.isBlank() && !looksLikeDateOrTime(text) && isNotPriceOrCategory(text))
                .collect(Collectors.joining(" "));

        if (!fallbackTitle.isBlank()) return normalize(fallbackTitle);

        return normalize(link.text());
    }

    private boolean isValidTextNodeForTitle(Node node) {
        if (node instanceof TextNode) return true;
        if (node instanceof Element el) {
            String tag = el.tagName().toLowerCase();
            return !tag.matches("img|br|hr");
        }
        return false;
    }

    private ParsedDateTime extractDateTime(Element link) {
        String rawText = extractRawDateTimeText(link);
        if (rawText == null || rawText.isBlank()) return null;

        Matcher m = DATE_TIME_PATTERN.matcher(rawText);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            String monthStr = m.group(2).toLowerCase();
            String yearStr = m.group(3);
            String time = m.group(4);

            String monthNum = resolveMonthNumber(monthStr);
            String date;

            if (monthNum != null) {
                String year = yearStr != null ? yearStr : String.valueOf(Year.now().getValue());
                date = String.format("%s-%s-%02d", year, monthNum, day);
            } else {
                date = day + " " + m.group(2);
                log.warn("Nie udało się rozpoznać miesiąca: '{}'", m.group(2));
            }
            return new ParsedDateTime(date, time);
        }
        return null;
    }

    private String extractRawDateTimeText(Element link) {
        for (Node node : link.childNodes()) {
            String text = node instanceof TextNode ? ((TextNode) node).text() :
                    node instanceof Element && !((Element) node).tagName().equalsIgnoreCase("img") ? ((Element) node).text() : null;

            text = normalize(text);
            if (!text.isBlank() && looksLikeDateOrTime(text)) {
                return text;
            }
        }

        Matcher m = DATE_TIME_PATTERN.matcher(normalize(link.text()));
        return m.find() ? m.group(0) : null;
    }

    private String resolveMonthNumber(String monthName) {
        return POLISH_MONTHS.entrySet().stream()
                .filter(entry -> monthName.startsWith(entry.getKey()) || entry.getKey().startsWith(monthName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractKategoria(Element link) {
        String fullText = normalize(link.text()).toLowerCase();

        return KNOWN_CATEGORIES.stream()
                .filter(fullText::contains)
                .map(cat -> cat.substring(0, 1).toUpperCase() + cat.substring(1))
                .findFirst()
                .orElseGet(() -> extractFallbackKategoria(link));
    }

    private String extractFallbackKategoria(Element link) {
        return link.childNodes().stream()
                .map(node -> node instanceof TextNode ? ((TextNode) node).text() :
                        node instanceof Element && !((Element) node).tagName().equalsIgnoreCase("img") ? ((Element) node).text() : "")
                .map(this::normalize)
                .filter(text -> !text.isBlank() && text.length() < 30)
                .filter(text -> !looksLikeDateOrTime(text) && isNotPriceOrCategory(text) && !text.contains(" "))
                .findFirst()
                .orElse(null);
    }

    private String extractPosterUrl(Element link) {
        Element explicitImg = link.selectFirst("img.image-barakah-post, img[class*='barakah'], img[class*='poster'], img[class*='spektakl']");
        if (explicitImg != null && !explicitImg.absUrl("src").isBlank()) {
            return explicitImg.absUrl("src");
        }

        return link.select("img[src]").stream()
                .map(img -> img.absUrl("src"))
                .filter(src -> !src.isBlank() && !src.matches(".*(placeholder|spacer|blank|\\.svg).*"))
                .findFirst()
                .orElse(null);
    }

    private String extractTicketUrl(Element link) {
        String ticketUrl = link.select("a[href*='bilety.teatrbarakah.com'], a[href*='bilety']").stream()
                .map(a -> a.absUrl("href"))
                .filter(href -> !href.isBlank())
                .findFirst()
                .orElse(null);

        if (ticketUrl == null && link.parent() != null) {
            ticketUrl = link.parent().select("a[href*='bilety.teatrbarakah.com']").stream()
                    .map(a -> a.absUrl("href"))
                    .filter(href -> !href.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        return ticketUrl != null ? ticketUrl : DEFAULT_TICKET_URL;
    }

    private boolean looksLikeDateOrTime(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.matches(".*\\d{1,2}:\\d{2}.*")) return true;

        if (text.matches("\\d{1,2}\\s+\\w+.*")) {
            return POLISH_MONTHS.keySet().stream().anyMatch(text.toLowerCase()::contains);
        }
        return false;
    }

    private boolean isNotPriceOrCategory(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase();

        if (lower.matches(".*\\d+\\s*(pln|zł|zl).*") || lower.startsWith("cena:") || lower.matches(".*wstę?p wolny.*")) {
            return false;
        }
        return !KNOWN_CATEGORIES.contains(lower);
    }

    private boolean isMenuOrNavLink(String href) {
        if (href == null || href.isBlank()) return true;
        String lower = href.toLowerCase();

        if (lower.equals("#") || lower.startsWith("javascript:") || lower.startsWith("mailto:")) return true;
        return NON_EVENT_PATHS.stream().anyMatch(lower::contains);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}