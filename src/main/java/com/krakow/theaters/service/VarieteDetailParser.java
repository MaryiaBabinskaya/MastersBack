package com.krakow.theaters.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.PlayDetailsDto;
import com.krakow.theaters.dto.UpcomingTermDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VarieteDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu Variete: {}", url);

        String slug = extractSlugFromUrl(url);
        if (slug == null) {
            log.warn("Nie można wyekstrahować slug z URL: {}", url);
            return null;
        }

        String apiUrl = "https://wordpress.teatrvariete.pl/wp-json/wp/v2/repertoire?slug=" + slug;
        String jsonResponse = Jsoup.connect(apiUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(0)
                .ignoreContentType(true)
                .execute()
                .body();

        JsonNode shows = objectMapper.readTree(jsonResponse);
        if (shows.isArray() && shows.isEmpty()) {
            log.warn("Nie znaleziono spektaklu dla slug: {}", slug);
            return null;
        }

        JsonNode show = shows.get(0);
        return parse(show, url);
    }

    public PlayDetailsDto parse(JsonNode show, String url) {
        PlayDetailsDto dto = new PlayDetailsDto();
        dto.setSource(url);
        dto.setTitle(extractTitle(show));
        dto.setImageUrl(extractImageUrl(show));
        dto.setDescription(extractDescription(show));
        dto.setCategory(extractCategory(show));
        dto.setContributors(extractContributors(show));
        dto.setCast(extractCast(show));
        dto.setUpcomingTerms(extractUpcomingTerms(show));

        log.info("Sparsowano spektakl: {}", dto.getTitle());
        return dto;
    }

    private String extractSlugFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        url = url.replaceAll("/+$", "");

        String[] parts = url.split("/");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.contains("?")) {
                lastPart = lastPart.substring(0, lastPart.indexOf("?"));
            }
            return lastPart;
        }
        return null;
    }

    private String extractTitle(JsonNode show) {
        JsonNode titleNode = show.get("title");
        if (titleNode != null && titleNode.has("rendered")) {
            String title = titleNode.get("rendered").asText();
            title = title.replaceAll("<[^>]*>", "").trim();
            return clean(title);
        }
        return null;
    }

    private String extractImageUrl(JsonNode show) {
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("intro") && !acf.get("intro").isNull()) {
            JsonNode intro = acf.get("intro");
            if (intro.has("image") && !intro.get("image").isNull()) {
                JsonNode image = intro.get("image");
                if (image.has("url") && !image.get("url").isNull()) {
                    return image.get("url").asText();
                }
            }
        }

        if (show.has("featured_media") && !show.get("featured_media").isNull()) {
            int mediaId = show.get("featured_media").asInt();
            if (mediaId > 0) {
                try {
                    String mediaUrl = "https://wordpress.teatrvariete.pl/wp-json/wp/v2/media/" + mediaId;
                    String jsonResponse = Jsoup.connect(mediaUrl)
                            .userAgent(USER_AGENT)
                            .timeout(TIMEOUT_MS)
                            .ignoreContentType(true)
                            .execute()
                            .body();

                    JsonNode media = objectMapper.readTree(jsonResponse);
                    if (media.has("source_url") && !media.get("source_url").isNull()) {
                        return media.get("source_url").asText();
                    }
                } catch (Exception e) {
                    log.warn("Nie można pobrać featured media: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    private String extractDescription(JsonNode show) {
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("intro") && !acf.get("intro").isNull()) {
            JsonNode intro = acf.get("intro");
            if (intro.has("content") && !intro.get("content").isNull()) {
                String content = intro.get("content").asText();
                content = content.replaceAll("</p>\\s*<p[^>]*>", "\n\n");
                content = content.replaceAll("<[^>]*>", "");
                content = content.replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#038;", "&");
                content = clean(content);
                if (content.length() > 50) {
                    return content;
                }
            }
        }

        if (show.has("excerpt") && !show.get("excerpt").isNull()) {
            JsonNode excerpt = show.get("excerpt");
            if (excerpt.has("rendered") && !excerpt.get("rendered").isNull()) {
                String content = excerpt.get("rendered").asText();
                content = content.replaceAll("<[^>]*>", "");
                content = clean(content);
                if (content.length() > 50) {
                    return content;
                }
            }
        }
        return null;
    }

    private String extractCategory(JsonNode show) {
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("direction") && !acf.get("direction").isNull()) {
            String direction = acf.get("direction").asText();
            direction = clean(direction);
            if (direction != null && !direction.isBlank()) {
                return direction;
            }
        }
        return "Musical";
    }

    private List<ContributorDto> extractContributors(JsonNode show) {
        List<ContributorDto> result = new ArrayList<>();
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("info") && !acf.get("info").isNull()) {
            JsonNode info = acf.get("info");
            if (info.has("creators") && !info.get("creators").isNull()) {
                JsonNode creators = info.get("creators");
                if (creators.has("items") && creators.get("items").isArray()) {
                    JsonNode items = creators.get("items");
                    for (JsonNode item : items) {
                        if (item.has("role") && item.has("name")) {
                            String role = clean(item.get("role").asText());
                            String name = clean(item.get("name").asText());
                            if (role != null && !role.isBlank() && name != null && !name.isBlank()) {
                                ContributorDto dto = new ContributorDto();
                                dto.setRole(role);
                                dto.setName(name);
                                dto.setProfileUrl(null);
                                result.add(dto);
                                log.debug("Dodano twórcę: {} - {}", role, name);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<CastMemberDto> extractCast(JsonNode show) {
        List<CastMemberDto> result = new ArrayList<>();
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("info") && !acf.get("info").isNull()) {
            JsonNode info = acf.get("info");
            if (info.has("cast") && !info.get("cast").isNull()) {
                JsonNode cast = info.get("cast");
                if (cast.has("items") && cast.get("items").isArray()) {
                    JsonNode items = cast.get("items");
                    for (JsonNode item : items) {
                        if (item.has("name")) {
                            String name = clean(item.get("name").asText());
                            if (name != null && !name.isBlank()) {
                                CastMemberDto dto = new CastMemberDto();
                                    if (item.has("role") && !item.get("role").isNull()) {
                                    String role = clean(item.get("role").asText());
                                    if (role != null && !role.isBlank()) {
                                        dto.setName(name + " - " + role);
                                    } else {
                                        dto.setName(name);
                                    }
                                } else {
                                    dto.setName(name);
                                }
                                dto.setProfileUrl(null);
                                dto.setImageUrl(null);
                                result.add(dto);
                                log.debug("Dodano aktora: {}", dto.getName());
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<UpcomingTermDto> extractUpcomingTerms(JsonNode show) {
        List<UpcomingTermDto> result = new ArrayList<>();
        JsonNode acf = show.get("acf");
        if (acf != null && acf.has("info") && !acf.get("info").isNull()) {
            JsonNode info = acf.get("info");
            if (info.has("upcoming") && !info.get("upcoming").isNull()) {
                JsonNode upcoming = info.get("upcoming");
                if (upcoming.has("items") && upcoming.get("items").isArray()) {
                    JsonNode items = upcoming.get("items");
                    for (JsonNode item : items) {
                        if (item.has("date") && !item.get("date").isNull()) {
                            String dateStr = item.get("date").asText();
                            UpcomingTermDto dto = new UpcomingTermDto();
                            dto.setDayLabel(dateStr);

                            if (item.has("link") && !item.get("link").isNull()) {
                                JsonNode link = item.get("link");
                                if (link.has("url") && !link.get("url").isNull()) {
                                    String ticketUrl = link.get("url").asText().trim();
                                    if (!ticketUrl.isEmpty()) {
                                        dto.setTicketUrl(ticketUrl);
                                        dto.setStatus("AVAILABLE");
                                    } else {
                                        dto.setStatus("INFO");
                                    }
                                } else {
                                    dto.setStatus("INFO");
                                }
                            } else {
                                dto.setStatus("INFO");
                            }
                            result.add(dto);
                        }
                    }
                }
            }
        }
        return result;
    }

    private String clean(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}