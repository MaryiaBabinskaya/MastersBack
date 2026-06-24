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
import java.util.stream.Collectors;

@Slf4j
@Service
public class ASTDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final String DEFAULT_CATEGORY = "Spektakle";
    private static final String DOMAIN = "krakow.ast.krakow.pl";
    private static final String TITLE_SEPARATOR = "|";
    private static final String ROLE_SEPARATOR = "-";
    private static final String COLON = ":";
    private static final String BR_TAG_REGEX = "<br[^>]*>";

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu AST: {}", url);
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
        dto.setDescription(extractDescription(doc));
        dto.setAdditionalInfo(extractAdditionalInfo(doc));
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setGalleryImages(extractGalleryImages(doc));
        dto.setCategory(DEFAULT_CATEGORY);

        log.info("Sparsowano spektakl: {}", dto.getTitle());
        return dto;
    }

    private String extractTitle(Document doc) {
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            String title = cleanTitle(ogTitle.attr("content"));
            if (title != null) return title;
        }

        for (Element heading : doc.select("h1, h2")) {
            String text = cleanTitle(heading.text());
            if (text != null && text.length() < 200) return text;
        }

        log.warn("Nie znaleziono tytułu dla spektaklu");
        return null;
    }

    private String cleanTitle(String rawTitle) {
        String title = normalize(rawTitle);
        if (title.isBlank() || title.length() <= 2) return null;
        return title.contains(TITLE_SEPARATOR) ? title.substring(0, title.indexOf(TITLE_SEPARATOR)).trim() : title;
    }

    private String extractImageUrl(Document doc) {
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) {
            String url = ogImage.attr("content").trim();
            if (!url.isBlank() && !url.endsWith(".svg")) return url;
        }

        return doc.select("img[src]").stream()
                .filter(this::isValidImage)
                .map(img -> img.absUrl("src"))
                .findFirst()
                .orElse(null);
    }

    private String extractDescription(Document doc) {
        String description = doc.select("p").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(this::isValidDescriptionParagraph)
                .limit(10)
                .collect(Collectors.joining("\n\n"))
                .trim();

        if (description.length() > 2000) {
            description = description.substring(0, 2000).trim();
        }

        return description.isEmpty() ? null : description;
    }

    private String extractAdditionalInfo(Document doc) {
        StringBuilder info = new StringBuilder();

        doc.select("p").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(this::isMetadataText)
                .forEach(text -> info.append(info.isEmpty() ? "" : "\n").append(text));

        Elements warnings = doc.getElementsContainingOwnText("W przedstawieniu pojawiają się");
        warnings.addAll(doc.getElementsContainingOwnText("Spektakl zawiera"));

        warnings.stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> text.length() > 20)
                .forEach(text -> info.append(info.isEmpty() ? "" : "\n\n").append("! ").append(text));

        String result = info.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> contributors = doc.select("p:contains(:)").stream()
                .flatMap(p -> Arrays.stream(p.html().split(BR_TAG_REGEX)))
                .filter(line -> line.contains(COLON))
                .map(this::parseContributorLine)
                .filter(Objects::nonNull)
                .toList();

        return contributors.isEmpty() ? null : contributors;
    }

    private ContributorDto parseContributorLine(String htmlLine) {
        try {
            String cleanLine = Jsoup.parse(htmlLine).text();
            String[] parts = cleanLine.split(COLON, 2);
            if (parts.length == 2) {
                String role = normalize(parts[0]);
                String name = normalize(parts[1]);
                if (!role.isBlank() && !name.isBlank() && name.length() > 2) {
                    log.debug("Znaleziono twórcę: {} - {}", role, name);
                    return createContributor(role, name);
                }
            }
        } catch (Exception e) {
            log.debug("Błąd parsowania linii twórcy: {}", e.getMessage());
        }
        return null;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> cast = doc.select("li").stream()
                .map(this::parseCastMember)
                .filter(Objects::nonNull)
                .toList();

        return cast.isEmpty() ? null : cast;
    }

    private CastMemberDto parseCastMember(Element li) {
        try {
            String text = normalize(li.text());
            if (text.length() < 3) return null;

            String html = li.html();
            if (html.contains("<br")) {
                String cleanText = Jsoup.parse(html.replaceAll(BR_TAG_REGEX, TITLE_SEPARATOR)).text();
                String[] parts = cleanText.split("\\" + TITLE_SEPARATOR);
                if (parts.length >= 2) {
                    return buildCastMember(normalize(parts[0]), normalize(parts[1]));
                }
            }

            if (text.contains(ROLE_SEPARATOR)) {
                String[] parts = text.split(ROLE_SEPARATOR, 2);
                return buildCastMember(normalize(parts[0]), normalize(parts[1]));
            }

            return buildCastMember(text, "");

        } catch (Exception e) {
            log.debug("Błąd parsowania członka obsady: {}", e.getMessage());
            return null;
        }
    }

    private List<String> extractGalleryImages(Document doc) {
        List<String> images = doc.select("img[src]").stream()
                .filter(this::isValidImage)
                .map(img -> img.absUrl("src"))
                .toList();

        return images.isEmpty() ? null : images;
    }

    private boolean isValidImage(Element img) {
        String src = img.absUrl("src").toLowerCase();
        String alt = img.attr("alt").toLowerCase();

        if (src.endsWith(".svg") || alt.contains("logo") || alt.contains("ikona")) {
            return false;
        }
        return src.contains(DOMAIN) && (src.endsWith(".jpg") || src.endsWith(".jpeg") || src.endsWith(".png"));
    }

    private boolean isValidDescriptionParagraph(String text) {
        if (text.length() < 50) return false;
        String lowerText = text.toLowerCase();

        return !isMetadataText(lowerText) &&
                !lowerText.contains("reżyseria:") &&
                !lowerText.contains("scenografia:") &&
                !lowerText.contains("przedstawieniu pojawiają się") &&
                !lowerText.contains("spektakl zawiera");
    }

    private boolean isMetadataText(String text) {
        String lowerText = text.toLowerCase();
        return lowerText.contains("godzina:") ||
                lowerText.contains("kategoria wiekowa") ||
                lowerText.contains("czas trwania") ||
                lowerText.contains("scena im.") ||
                lowerText.contains("ul. straszewskiego");
    }

    private ContributorDto createContributor(String role, String name) {
        ContributorDto contributor = new ContributorDto();
        contributor.setRole(role);
        contributor.setName(name);
        return contributor;
    }

    private CastMemberDto buildCastMember(String name, String role) {
        if (name.isBlank() || name.length() <= 2) return null;
        CastMemberDto member = new CastMemberDto();
        member.setName(name);
        member.setRole(role);
        log.debug("Znaleziono aktora: {} - {}", name, role);
        return member;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}