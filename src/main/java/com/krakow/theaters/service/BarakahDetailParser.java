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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class BarakahDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    private static final List<String> ROLE_PATTERNS = List.of(
            "reżyseria i scenariusz:", "reżyseria:", "scenariusz i dramaturgia:",
            "scenariusz:", "muzyka:", "choreografia:", "scenografia:", "kostiumy:",
            "scenografia i kostiumy:", "light design:", "reżyseria światła:",
            "instalacje performatywne:", "adaptacja:", "tłumaczenie:",
            "dramaturgia:", "video:", "multimedia:", "kierownik budowy scenografii:",
            "głos konferansjera:"
    );

    private static final List<String> ROLE_KEYWORDS = List.of(
            "scenariusz", "dramaturgia", "muzyka", "choreografia", "scenografia",
            "kostiumy", "video", "reżyseria", "tłumaczenie", "adaptacja",
            "light design", "multimedia", "kierownik", "charakteryzacja", "głos"
    );

    private enum Section {
        DESCRIPTION, CONTRIBUTORS, CAST, ADDITIONAL_INFO
    }

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów Barakah dla: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
        return parse(doc, url);
    }

    public PlayDetailsDto parse(Document doc, String url) {
        PlayDetailsDto dto = new PlayDetailsDto();
        dto.setSource(url);
        dto.setTitle(extractTitle(doc));
        dto.setImageUrl(extractImageUrl(doc));

        Element contentDiv = doc.selectFirst(".single-spektakle__text");
        if (contentDiv != null) {
            parseContentSection(contentDiv, dto);
        } else {
            log.warn("Nie znaleziono sekcji .single-spektakle__text dla URL: {}", url);
        }

        return dto;
    }

    private void parseContentSection(Element contentDiv, PlayDetailsDto dto) {
        StringBuilder description = new StringBuilder();
        List<ContributorDto> contributors = new ArrayList<>();
        List<CastMemberDto> cast = new ArrayList<>();
        StringBuilder additionalInfo = new StringBuilder();

        Section currentSection = Section.DESCRIPTION;

        for (Element p : contentDiv.select("p")) {
            String htmlText = p.html();
            String plainText = normalize(p.text());

            if (plainText.isBlank()) continue;

            if (isCombinedContributorAndCast(plainText)) {
                handleCombinedParagraph(htmlText, plainText, contributors, cast);
                continue;
            }

            currentSection = determineSection(plainText, currentSection);

            switch (currentSection) {
                case DESCRIPTION -> appendWithNewlines(description, plainText);
                case CONTRIBUTORS -> parseContributors(plainText, contributors);
                case CAST -> parseCast(htmlText, plainText, cast);
                case ADDITIONAL_INFO -> appendWithNewlines(additionalInfo, plainText);
            }
        }

        applyParsedContent(dto, description, contributors, cast, additionalInfo);
        log.info("Sparsowano: opis={}, twórcy={}, obsada={}, info={}",
                !description.isEmpty(), contributors.size(), cast.size(), !additionalInfo.isEmpty());
    }

    private boolean isCombinedContributorAndCast(String plainText) {
        String lowerText = plainText.toLowerCase();
        return (lowerText.contains("reżyseria i scenariusz") || lowerText.contains("reżyseria:"))
                && lowerText.contains("obsada:");
    }

    private void handleCombinedParagraph(String htmlText, String plainText,
                                         List<ContributorDto> contributors, List<CastMemberDto> cast) {
        int obsadaIndex = plainText.toLowerCase().indexOf("obsada:");
        int obsadaHtmlIndex = htmlText.toLowerCase().indexOf("obsada:");

        parseContributors(plainText.substring(0, obsadaIndex), contributors);
        parseCast(htmlText.substring(obsadaHtmlIndex), plainText.substring(obsadaIndex), cast);
    }

    private Section determineSection(String plainText, Section currentSection) {
        String lowerText = plainText.toLowerCase();
        if (lowerText.contains("reżyseria i scenariusz") || lowerText.contains("reżyseria:")) {
            return Section.CONTRIBUTORS;
        } else if (lowerText.contains("obsada:")) {
            return Section.CAST;
        } else if (lowerText.contains("data prapremiery") || lowerText.contains("premiera:") || lowerText.contains("czas trwania")) {
            return Section.ADDITIONAL_INFO;
        }
        return currentSection;
    }

    private void parseContributors(String plainText, List<ContributorDto> contributors) {
        int startPos = 0;

        while (startPos < plainText.length()) {
            RoleMatch currentRole = findNextRole(plainText, startPos);
            if (currentRole == null) break;

            RoleMatch nextRole = findNextRole(plainText, currentRole.position() + currentRole.role().length());
            int endPos = (nextRole != null) ? nextRole.position() : plainText.length();

            String roleAndName = plainText.substring(currentRole.position(), endPos).trim();
            extractAndAddContributor(roleAndName, contributors);

            startPos = currentRole.position() + currentRole.role().length();
        }
    }

    private RoleMatch findNextRole(String text, int startPos) {
        String lowerText = text.toLowerCase();
        return ROLE_PATTERNS.stream()
                .map(pattern -> new RoleMatch(pattern, lowerText.indexOf(pattern, startPos)))
                .filter(match -> match.position() != -1)
                .min(Comparator.comparingInt(RoleMatch::position))
                .orElse(null);
    }

    private void extractAndAddContributor(String roleAndName, List<ContributorDto> contributors) {
        int colonIndex = roleAndName.indexOf(':');
        if (colonIndex > 0 && colonIndex < roleAndName.length() - 1) {
            String role = normalize(roleAndName.substring(0, colonIndex));
            String name = cleanNameFromRoleFragments(normalize(roleAndName.substring(colonIndex + 1)));

            if (!role.isBlank() && !name.isBlank() && name.length() > 2) {
                ContributorDto contributor = new ContributorDto();
                contributor.setRole(role);
                contributor.setName(name);
                contributors.add(contributor);
                log.debug("Dodano twórcę: {} - {}", role, name);
            }
        }
    }

    private String cleanNameFromRoleFragments(String name) {
        if (name == null || name.isBlank()) return name;

        String nameLower = name.toLowerCase();
        for (String keyword : ROLE_KEYWORDS) {
            int keywordPos = nameLower.indexOf(keyword);
            if (keywordPos > 0) {
                String cleanedName = name.substring(0, keywordPos).trim();
                if (cleanedName.endsWith(" i")) {
                    cleanedName = cleanedName.substring(0, cleanedName.length() - 2).trim();
                }
                log.debug("Oczyszczono nazwisko: '{}' -> '{}'", name, cleanedName);
                return cleanedName;
            }
        }
        return name;
    }

    private void parseCast(String htmlText, String plainText, List<CastMemberDto> cast) {
        String castText = plainText;
        int obsadaIndex = castText.toLowerCase().indexOf("obsada:");

        if (obsadaIndex >= 0) {
            castText = castText.substring(obsadaIndex + 7).trim();
        }

        String strongText = Jsoup.parse(htmlText).select("strong").stream()
                .map(Element::text)
                .collect(Collectors.joining(" "));

        if (!strongText.isEmpty()) {
            castText = strongText;
        }

        Arrays.stream(castText.split("[,/]"))
                .map(this::normalize)
                .filter(name -> name.length() >= 3 && isLikelyPersonName(name))
                .forEach(name -> {
                    CastMemberDto castMember = new CastMemberDto();
                    castMember.setName(name);
                    cast.add(castMember);
                    log.debug("Dodano aktora: {}", name);
                });
    }

    private boolean isLikelyPersonName(String name) {
        String lowerName = name.toLowerCase();
        return !lowerName.contains("data") && !lowerName.contains("czas") &&
                !lowerName.contains("premiera") && !lowerName.contains("minut");
    }

    private void applyParsedContent(PlayDetailsDto dto, StringBuilder description,
                                    List<ContributorDto> contributors, List<CastMemberDto> cast,
                                    StringBuilder additionalInfo) {
        if (!description.isEmpty()) dto.setDescription(description.toString());
        if (!contributors.isEmpty()) dto.setContributors(contributors);
        if (!cast.isEmpty()) dto.setCast(cast);

        if (!additionalInfo.isEmpty()) {
            String infoStr = additionalInfo.toString();
            dto.setAdditionalInfo(infoStr);
            extractPremiereFromAdditionalInfo(infoStr, dto);
        }
    }

    private void extractPremiereFromAdditionalInfo(String additionalInfo, PlayDetailsDto dto) {
        Arrays.stream(additionalInfo.split("\n"))
                .filter(line -> line.toLowerCase().contains("premiera") || line.toLowerCase().contains("prapremiery"))
                .findFirst()
                .ifPresent(line -> {
                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0 && colonIndex < line.length() - 1) {
                        String date = line.substring(colonIndex + 1).trim();
                        dto.setPremiereDate(date);
                        log.debug("Znaleziono datę premiery: {}", date);
                    }
                });
    }

    private String extractTitle(Document doc) {
        return Stream.of(
                        doc.selectFirst("h1"),
                        doc.selectFirst("meta[property=og:title]")
                ).filter(Objects::nonNull)
                .map(el -> el.tagName().equals("meta") ? el.attr("content") : el.text())
                .map(this::normalize)
                .filter(val -> !val.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String extractImageUrl(Document doc) {
        return Stream.of(
                        doc.selectFirst(".block-image img"),
                        doc.selectFirst(".single-spektakle-page__img img"),
                        doc.selectFirst("meta[property=og:image]")
                ).filter(Objects::nonNull)
                .map(el -> el.tagName().equals("meta") ? el.attr("content") : el.absUrl("src"))
                .map(String::trim)
                .filter(val -> !val.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void appendWithNewlines(StringBuilder builder, String text) {
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(text);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private record RoleMatch(String role, int position) {}
}