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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScenaStuDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów Scena STU: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        PlayDetailsDto details = new PlayDetailsDto();
        details.setSource(url);

        details.setTitle(extractTitle(doc));
        details.setImageUrl(extractPosterUrl(doc));
        extractAboutSection(doc, details);

        details.setContributors(extractCreators(doc));
        details.setCast(extractCast(doc));

        log.info("Sparsowano spektakl: {} (Twórców: {}, Obsada: {})",
                details.getTitle(), details.getContributors().size(), details.getCast().size());

        return details;
    }

    private String extractTitle(Document doc) {
        return Optional.ofNullable(doc.selectFirst("h1.post-title"))
                .map(Element::text)
                .map(this::normalize)
                .orElse(null);
    }

    private String extractPosterUrl(Document doc) {
        return Optional.ofNullable(doc.selectFirst("div.container .thumbnail img.wp-post-image"))
                .map(el -> el.attr("src"))
                .filter(src -> !src.isEmpty())
                .orElse(null);
    }

    private void extractAboutSection(Document doc, PlayDetailsDto details) {
        Element aboutSection = doc.selectFirst("div.module.main-content-section");
        if (aboutSection == null) return;

        Element contentDiv = aboutSection.selectFirst("div.main-content-section-content");
        if (contentDiv != null) {
            String description = contentDiv.select("p").stream()
                    .map(Element::text)
                    .map(this::normalize)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("\n\n"));

            details.setDescription(description);

            contentDiv.select("p").stream()
                    .map(Element::text)
                    .filter(text -> text.toLowerCase().contains("premiera krakowska:"))
                    .findFirst()
                    .ifPresent(p -> details.setAdditionalInfo(normalize(p)));
        }
    }

    private List<ContributorDto> extractCreators(Document doc) {
        return doc.select("table.meta-info-table tbody tr.row.border").stream()
                .map(row -> {
                    String role = normalize(Objects.requireNonNull(row.selectFirst("th.label")).text());
                    String name = normalize(Objects.requireNonNull(row.selectFirst("td.content")).text());
                    if (role.isEmpty() || name.isEmpty() || role.equalsIgnoreCase("Premiera")) return null;

                    ContributorDto contributor = new ContributorDto();
                    contributor.setRole(role);
                    contributor.setName(name);
                    return contributor;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<CastMemberDto> extractCast(Document doc) {
        return doc.select("div.module.main-content-section").stream()
                .filter(section -> Optional.ofNullable(section.selectFirst("h2.main-content-section-title"))
                        .map(h -> h.text().toLowerCase().contains("obsada")).orElse(false))
                .flatMap(section -> section.select("div.person-card").stream())
                .map(this::mapToCastMember)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private CastMemberDto mapToCastMember(Element card) {
        Element nameDiv = card.selectFirst("div.name");
        Element roleDiv = card.selectFirst("div.role");

        if (nameDiv == null || roleDiv == null) return null;

        String name = normalize(nameDiv.text());
        String role = normalize(roleDiv.text()).replaceAll("(?i)^jako\\s+", "");

        if (name.isEmpty() || role.isEmpty()) return null;

        CastMemberDto member = new CastMemberDto();
        member.setName(name);
        member.setRole(role);

        Optional.ofNullable(card.selectFirst("img")).map(i -> i.attr("src")).ifPresent(member::setImageUrl);
        Optional.ofNullable(card.selectFirst("a[href]")).map(a -> a.absUrl("href")).ifPresent(member::setProfileUrl);

        return member;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}