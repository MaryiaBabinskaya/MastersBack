package com.krakow.theaters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krakow.theaters.dto.VarieteRepertuar;
import com.krakow.theaters.dto.VarieteSpektakl;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class VarieteParser {

    private static final String API_URL = "https://wordpress.teatrvariete.pl/wp-json/wp/v2/repertoire";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VarieteRepertuar parseRepertuar() {
        int year = LocalDate.now().getYear();
        return parseRepertuar(
                LocalDateTime.of(year, 5, 1, 0, 0),
                LocalDateTime.of(year, 8, 31, 23, 59)
        );
    }

    public VarieteRepertuar parseRepertuar(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Rozpoczynam parsowanie repertuaru Variete z API ({} - {})...", startDate, endDate);
        VarieteRepertuar repertuar = new VarieteRepertuar();
        List<VarieteSpektakl> spektakle = new ArrayList<>();

        int page = 1;
        int perPage = 100;
        boolean hasMore = true;

        while (hasMore) {
            String url = String.format("%s?per_page=%d&page=%d&orderby=date&order=asc", API_URL, perPage, page);
            log.info("Pobieranie danych ze strony {} API: {}", page, url);
            try {
                String jsonResponse = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(0)
                        .ignoreContentType(true)
                        .execute()
                        .body();

                JsonNode shows = objectMapper.readTree(jsonResponse);

                if (!shows.isArray() || shows.isEmpty()) {
                    break;
                }

                log.info("Znaleziono {} spektakli na stronie {}", shows.size(), page);

                for (JsonNode show : shows) {
                    List<VarieteSpektakl> showSpektakle = parseSpektakleFromApi(show, startDate, endDate);
                    for (VarieteSpektakl spektakl : showSpektakle) {
                        if (spektakl != null && spektakl.getNazwa() != null) {
                            spektakle.add(spektakl);
                            log.debug("Dodano spektakl: {} - {}", spektakl.getNazwa(), spektakl.getKiedy());
                        }
                    }
                }

                if (shows.size() < perPage) {
                    hasMore = false;
                } else {
                    page++;
                }

            } catch (Exception e) {
                log.error("Błąd podczas pobierania danych z API: {}", e.getMessage());
                hasMore = false;
            }
        }

        repertuar.setSpektakle(spektakle);
        log.info("Znaleziono łącznie {} spektakli", spektakle.size());

        return repertuar;
    }

    private List<VarieteSpektakl> parseSpektakleFromApi(JsonNode show, LocalDateTime startDate, LocalDateTime endDate) {
        List<VarieteSpektakl> result = new ArrayList<>();
        try {
            JsonNode acf = show.get("acf");
            if (acf == null || acf.isNull()) {
                return result;
            }

            String showTitle = null;
            JsonNode titleNode = show.get("title");
            if (titleNode != null && titleNode.has("rendered")) {
                showTitle = titleNode.get("rendered").asText();
                showTitle = showTitle.replaceAll("<[^>]*>", "").trim();
                showTitle = clean(showTitle);
            }

            if (isBlank(showTitle)) {
                return result;
            }

            String showLink = null;
            JsonNode linkNode = show.get("link");
            if (linkNode != null && !linkNode.isNull()) {
                showLink = linkNode.asText();
            }

            String showTyp = null;
            JsonNode directionNode = acf.get("direction");
            if (directionNode != null && !directionNode.isNull()) {
                showTyp = clean(directionNode.asText());
            }

            String showCena = null;
            if (acf.has("info") && !acf.get("info").isNull()) {
                JsonNode info = acf.get("info");
                if (info.has("tickets") && !info.get("tickets").isNull()) {
                    JsonNode tickets = info.get("tickets");
                    if (tickets.has("items") && tickets.get("items").isArray()) {
                        JsonNode items = tickets.get("items");
                        StringBuilder prices = new StringBuilder();
                        for (JsonNode item : items) {
                            if (item.has("label") && !item.get("label").isNull()) {
                                if (!prices.isEmpty()) {
                                    prices.append(" | ");
                                }
                                prices.append(item.get("label").asText());
                            }
                        }
                        if (!prices.isEmpty()) {
                            showCena = prices.toString();
                        }
                    }
                }

                if (info.has("upcoming") && !info.get("upcoming").isNull()) {
                    JsonNode upcoming = info.get("upcoming");
                    if (upcoming.has("items") && upcoming.get("items").isArray()) {
                        JsonNode items = upcoming.get("items");
                        for (JsonNode item : items) {
                            if (!item.has("date") || item.get("date").isNull()) {
                                continue;
                            }

                            String itemDateString = item.get("date").asText();
                            if (isBlank(itemDateString)) {
                                continue;
                            }

                            LocalDateTime itemDateTime;
                            try {
                                itemDateTime = LocalDateTime.parse(itemDateString, API_DATE_FORMAT);
                            } catch (DateTimeParseException e) {
                                log.warn("Nie można sparsować daty: {}", itemDateString);
                                continue;
                            }

                            if (itemDateTime.isBefore(startDate) || itemDateTime.isAfter(endDate)) {
                                continue;
                            }

                            VarieteSpektakl spektakl = new VarieteSpektakl();
                            spektakl.setNazwa(showTitle);
                            spektakl.setKiedy(itemDateTime.format(OUTPUT_DATE_FORMAT));
                            spektakl.setOpis(showLink);
                            spektakl.setTyp(showTyp);
                            spektakl.setCena(showCena);

                            if (item.has("link") && !item.get("link").isNull()) {
                                JsonNode link = item.get("link");
                                if (link.has("url") && !link.get("url").isNull()) {
                                    String ticketUrl = link.get("url").asText().trim();
                                    if (!ticketUrl.isEmpty()) {
                                        spektakl.setTicketUrl(ticketUrl);
                                    }
                                }
                            }

                            spektakl.setId("VR-" + generateId(showTitle));
                            result.add(spektakl);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Błąd podczas parsowania spektaklu z API: {}", e.getMessage(), e);
        }
        return result;
    }

    private String generateId(String name) {
        if (isBlank(name)) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        String cleanName = name.replaceAll("[^a-zA-Z0-9]", "");
        return cleanName.substring(0, Math.min(cleanName.length(), 10));
    }

    private String clean(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}