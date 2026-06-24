package com.krakow.theaters.service;

import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.PlayDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class OperaKrakowskaDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final String BASE_URL = "https://opera.krakow.pl";
    private static final int TIMEOUT_MS = 30000;

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów Opera Krakowska: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        PlayDetailsDto details = new PlayDetailsDto();
        details.setSource(url);
        details.setTitle(extractTitle(doc));
        details.setImageUrl(extractImageUrl(doc));
        details.setDescription(extractText(doc));
        details.setAdditionalInfo(extractAdditionalInfo(doc));

        parseContributorsAndCast(doc, details);

        log.info("Sparsowano spektakl: {}", details.getTitle());
        return details;
    }

    private String extractTitle(Document doc) {
        return Optional.ofNullable(doc.selectFirst("h1.header-spectacle-panel__title"))
                .map(Element::text)
                .map(this::normalize)
                .orElse(null);
    }

    private String extractImageUrl(Document doc) {
        Element posterImg = doc.selectFirst("#poster img[src*='/media/spectacle_image/']");
        if (posterImg != null) return toAbsolute(posterImg.attr("src"));

        return doc.select("img[src*='/media/spectacle_image/']").stream()
                .map(img -> toAbsolute(img.attr("src")))
                .findFirst()
                .orElse(null);
    }

    private String extractAdditionalInfo(Document doc) {
        StringBuilder sb = new StringBuilder();
        Elements infoRows = doc.select(".detail-text__title");

        for (Element titleElem : infoRows) {
            String title = titleElem.text().toLowerCase();
            if (title.contains("informacje") || title.contains("dodatkowe informacje")) {
                Optional.ofNullable(titleElem.nextElementSibling())
                        .map(div -> normalize(div.text()))
                        .filter(text -> !text.isEmpty())
                        .ifPresent(text -> sb.append(sb.isEmpty() ? "" : "\n").append(text));
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void parseContributorsAndCast(Document doc, PlayDetailsDto details) {
        Element section = doc.selectFirst("#producersAndCast");
        if (section == null) return;

        details.setContributors(parseSection(section, "realizatorzy", this::mapToContributor));
        details.setCast(parseSection(section, "obsada", this::mapToCastMember));
    }

    private <T> List<T> parseSection(Element container, String headerKeyword, SectionMapper<T> mapper) {
        return container.select(".detail-text").stream()
                .filter(s -> Optional.ofNullable(s.selectFirst(".detail-text__title"))
                        .map(t -> t.text().toLowerCase().contains(headerKeyword)).orElse(false))
                .flatMap(s -> Optional.ofNullable(s.selectFirst(".detail-text__content__text .description")).stream())
                .flatMap(desc -> Arrays.stream(desc.html().split("<br[^>]*>")))
                .map(line -> normalize(Jsoup.parse(line).text()))
                .filter(line -> line.contains("|"))
                .map(line -> line.split("\\|", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> mapper.map(parts[0].trim(), parts[1].trim()))
                .filter(Objects::nonNull)
                .toList();
    }

    private ContributorDto mapToContributor(String role, String name) {
        if (role.toLowerCase().contains("realizatorzy")) return null;
        ContributorDto dto = new ContributorDto();
        dto.setRole(role);
        dto.setName(name);
        return dto;
    }

    private CastMemberDto mapToCastMember(String role, String name) {
        String lower = role.toLowerCase();
        if (lower.contains("soliści") || lower.contains("balet") || lower.contains("orkiestra")) return null;
        CastMemberDto dto = new CastMemberDto();
        dto.setRole(role);
        dto.setName(name);
        return dto;
    }

    private String toAbsolute(String url) {
        return (url != null && url.startsWith("/")) ? BASE_URL + url : url;
    }

    private String extractText(Document doc) {
        return Optional.ofNullable(doc.selectFirst(".detail-text__content__text.description"))
                .map(Element::text)
                .map(this::normalize)
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    @FunctionalInterface
    private interface SectionMapper<T> {
        T map(String role, String name);
    }
}