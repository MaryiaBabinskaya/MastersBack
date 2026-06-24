package com.krakow.theaters.service;

import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.PlayDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class LazniaNowaDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final String BASE_URL = "https://www.laznianowa.pl";

    private static final Set<String> CAST_ROLES = Set.of("występują", "występuje");
    private static final Set<String> IGNORED_CONTRIBUTOR_ROLES = Set.of(
            "występują", "występuje", "produkcja", "premiera"
    );

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu Laznia Nowa: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        // Ustawiamy URI bazowe na wypadek, gdyby dokument go nie miał (dla poprawnego absUrl)
        doc.setBaseUri(BASE_URL);

        return parse(doc, url);
    }

    public PlayDetailsDto parse(Document doc, String url) {
        PlayDetailsDto dto = new PlayDetailsDto();
        dto.setSource(url);
        dto.setTitle(extractTitle(doc));
        dto.setImageUrl(extractImageUrl(doc));
        dto.setDescription(extractDescription(doc));
        dto.setAdditionalInfo(extractAdditionalInfo(doc));
        dto.setDurationMinutesText(extractDuration(doc));
        dto.setPremiereDate(extractPremiereDate(doc));
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setGalleryImages(extractGalleryImages(doc));

        log.info("Sparsowano spektakl: {}", dto.getTitle());
        return dto;
    }

    private String extractTitle(Document doc) {
        return Stream.of(
                        Optional.ofNullable(doc.selectFirst("h1.block-show-title")).map(Element::text),
                        Optional.ofNullable(doc.selectFirst("meta[property=og:title]")).map(el -> el.attr("content"))
                )
                .flatMap(Optional::stream)
                .map(this::normalize)
                .filter(title -> !title.isBlank() && title.length() > 2)
                .map(this::cleanTitleSuffix)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Nie znaleziono tytułu dla spektaklu");
                    return null;
                });
    }

    private String cleanTitleSuffix(String title) {
        return title.contains("|") ? title.substring(0, title.indexOf("|")).trim() : title;
    }

    private String extractImageUrl(Document doc) {
        return Stream.of(
                        Optional.ofNullable(doc.selectFirst(".parallax-mirror img.parallax-slider"))
                                .map(el -> el.absUrl("src")),
                        Optional.ofNullable(doc.selectFirst(".block-show-bg-parallax-window"))
                                .map(el -> {
                                    String src = el.attr("data-image-src");
                                    return src.startsWith("/") ? BASE_URL + src : src;
                                }),
                        Optional.ofNullable(doc.selectFirst("meta[property=og:image]"))
                                .map(el -> el.attr("content"))
                )
                .flatMap(Optional::stream)
                .map(String::trim)
                .filter(url -> !url.isBlank() && !url.contains(".svg"))
                .findFirst()
                .orElse(null);
    }

    private String extractDescription(Document doc) {
        String description = doc.select(".field--name-field-body-text .field__item p").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> text.length() >= 20 && !text.toLowerCase().startsWith("fot."))
                .collect(Collectors.joining("\n\n"))
                .trim();

        return description.isEmpty() ? null : description;
    }

    private String extractDuration(Document doc) {
        Element durationDiv = doc.selectFirst(".block-show-duration");
        if (durationDiv != null) {
            String text = normalize(durationDiv.text());
            if (text.contains(">")) {
                String[] parts = text.split(">", 2);
                return normalize(parts[1]);
            }
            return text;
        }
        return null;
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> contributors = extractCastItemPairs(doc)
                .filter(pair -> !IGNORED_CONTRIBUTOR_ROLES.contains(pair.role.toLowerCase()))
                .map(pair -> {
                    ContributorDto dto = new ContributorDto();
                    dto.setRole(pair.role);
                    dto.setName(pair.value);
                    return dto;
                })
                .collect(Collectors.toMap(
                        c -> c.getRole() + "|" + c.getName(),
                        c -> c,
                        (existing, replacement) -> existing
                ))
                .values().stream()
                .peek(c -> log.debug("Znaleziono twórcę: {} - {}", c.getRole(), c.getName()))
                .toList();

        return contributors.isEmpty() ? null : contributors;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> cast = extractCastItemPairs(doc)
                .filter(pair -> CAST_ROLES.contains(pair.role.toLowerCase()))
                .flatMap(pair -> Arrays.stream(pair.value.split(",")))
                .map(this::normalize)
                .filter(name -> !name.isBlank() && name.length() > 2)
                .distinct()
                .map(name -> {
                    CastMemberDto member = new CastMemberDto();
                    member.setName(name);
                    member.setRole("");
                    return member;
                })
                .peek(m -> log.debug("Znaleziono aktora: {}", m.getName()))
                .toList();

        return cast.isEmpty() ? null : cast;
    }

    private String extractPremiereDate(Document doc) {
        return extractCastItemPairs(doc)
                .filter(pair -> pair.role.equalsIgnoreCase("premiera"))
                .map(pair -> pair.value)
                .peek(date -> log.debug("Znaleziono datę premiery: {}", date))
                .findFirst()
                .orElse(null);
    }

    private Stream<CastItem> extractCastItemPairs(Document doc) {
        return doc.select(".cast--promoted .cast-item, .block-show-cast.cast .cast-item").stream()
                .map(item -> {
                    Element roleDiv = item.selectFirst(".cast-item-role");
                    Element textDiv = item.selectFirst(".cast-item-text");
                    if (roleDiv != null && textDiv != null) {
                        return new CastItem(normalize(roleDiv.text()), normalize(textDiv.text()));
                    }
                    return null;
                })
                .filter(item -> item != null && !item.role.isBlank() && !item.value.isBlank());
    }

    private record CastItem(String role, String value) {}

    private List<String> extractGalleryImages(Document doc) {
        List<String> images = doc.select(".gallery-item [href]").stream()
                .map(link -> link.absUrl("href"))
                .filter(url -> !url.isBlank() && (url.toLowerCase().contains(".jpg") || url.toLowerCase().contains(".jpeg")))
                .distinct()
                .toList();

        return images.isEmpty() ? null : images;
    }

    private String extractAdditionalInfo(Document doc) {
        String additionalInfo = doc.select(".block-show-cast.cast .cast-snippet").stream()
                .map(this::extractTextFromSnippet)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n"))
                .trim();

        return additionalInfo.isEmpty() ? null : additionalInfo;
    }

    private String extractTextFromSnippet(Element castSnippet) {
        Element fieldItem = castSnippet.selectFirst(".field__item");
        if (fieldItem != null) {
            String paras = fieldItem.select("p").stream()
                    .map(Element::text)
                    .map(this::normalize)
                    .filter(text -> !text.isBlank() && text.length() > 10)
                    .collect(Collectors.joining("\n\n"));
            if (!paras.isEmpty()) return paras;
        }

        String text = normalize(castSnippet.text());
        return text.length() > 10 ? text : "";
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}