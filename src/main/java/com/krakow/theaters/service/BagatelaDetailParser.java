package com.krakow.theaters.service;

import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.PlayDetailsDto;
import com.krakow.theaters.dto.UpcomingTermDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BagatelaDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    private static final String DEFAULT_SCENE = "Scena Bagatela";
    private static final String DEFAULT_CATEGORY = "Spektakle";
    private static final String GUEST_PLAY_CATEGORY = "Spektakl gościnny";

    private static final List<String> STOP_KEYWORDS = List.of(
            "obsada", "twórcy", "warto wiedzieć", "osoby", "recenzje", "terminarz", "repertuar", "bilety"
    );
    private static final List<String> SCENE_PATTERNS = List.of("Scena", "Sala", "Teatr Bagatela");
    private static final List<String> GENRE_KEYWORDS = List.of(
            "komedia", "dramat", "tragedia", "farsa", "musical", "muzyczny",
            "stand-up", "kabaret", "widowisko", "performance", "happening",
            "kryminał", "thriller", "horror"
    );
    private static final List<String> CONTRIBUTOR_ROLES = List.of(
            "Reżyseria:", "Scenariusz:", "Muzyka:", "Choreografia:", "Adaptacja:", "Scenografia:", "Tłumaczenie:", "Autor:"
    );

    public PlayDetailsDto parse(String url) throws IOException {
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
        dto.setScene(extractScene(doc));
        dto.setDurationMinutesText(extractDuration(doc));
        dto.setDescription(extractDescription(doc));
        dto.setCategory(extractCategory(doc, url));
        dto.setAdditionalInfo(extractAdditionalInfo(doc));
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setGalleryImages(extractGalleryImages(doc));
        dto.setUpcomingTerms(extractUpcomingTerms(doc));
        dto.setPremiereDate(extractPremiereDate(doc));
        return dto;
    }

    private String extractTitle(Document doc) {
        String h1 = extractTextFromFirst(doc, "h1");
        if (h1 != null) return h1;

        String ogTitle = extractMetaContent(doc, "og:title");
        if (ogTitle != null) return ogTitle;

        return extractTextFromFirst(doc, "title");
    }

    private String extractImageUrl(Document doc) {
        String ogImage = extractMetaContent(doc, "og:image");
        if (ogImage != null) return ogImage;

        Element mainImage = doc.selectFirst(".entry-content img, article img, .post-thumbnail img");
        if (mainImage != null && !mainImage.absUrl("src").isBlank()) {
            return mainImage.absUrl("src");
        }

        return doc.select("img[src]").stream()
                .map(img -> img.absUrl("src"))
                .filter(src -> src.contains("wp-content/uploads") || src.contains("/images/"))
                .findFirst()
                .orElse(null);
    }

    private String extractScene(Document doc) {
        String fullText = doc.text();
        return SCENE_PATTERNS.stream()
                .filter(fullText::contains)
                .flatMap(pattern -> doc.select("p, li, div.info").stream())
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> SCENE_PATTERNS.stream().anyMatch(text::contains))
                .findFirst()
                .orElse(DEFAULT_SCENE);
    }

    private String extractDuration(Document doc) {
        String fullText = doc.text();
        if (!fullText.contains("min") && !fullText.contains("minut")) {
            return null;
        }

        return doc.select("p, li, span, div.info").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> (text.contains("min") || text.contains("minut")) &&
                        (text.contains("Czas") || text.contains("Długość") || text.contains("Trwa")))
                .findFirst()
                .orElse(null);
    }

    private String extractDescription(Document doc) {
        StringBuilder fullDescription = new StringBuilder();
        Element content = doc.selectFirst(".entry-content, .article-content, .post-content");

        if (content != null) {
            extractFromContainer(content, fullDescription);
        } else {
            extractFromDocumentBody(doc, fullDescription);
        }

        if (fullDescription.length() > 100) {
            return fullDescription.toString();
        }

        String ogDesc = extractMetaContent(doc, "og:description");
        return (ogDesc != null && ogDesc.length() > 50) ? ogDesc : null;
    }

    private void extractFromContainer(Element container, StringBuilder descriptionBuilder) {
        for (Element el : container.select("*")) {
            String tagName = el.tagName().toLowerCase();

            if (isHeaderTag(tagName) && isStopKeyword(el.text())) {
                if (descriptionBuilder.length() > 100) return;
            }

            if (tagName.equals("p")) {
                appendParagraph(el, descriptionBuilder);
            }
        }
    }

    private void extractFromDocumentBody(Document doc, StringBuilder descriptionBuilder) {
        boolean startedCollecting = false;

        for (Element el : doc.select("body *")) {
            String tagName = el.tagName().toLowerCase();

            if (isHeaderTag(tagName) && startedCollecting && isStopKeyword(el.text())) {
                if (descriptionBuilder.length() > 100) return;
            }

            if (tagName.equals("p") && !isInsideLayoutContainer(el)) {
                if (appendParagraph(el, descriptionBuilder)) {
                    startedCollecting = true;
                }
            }
        }
    }

    private boolean isInsideLayoutContainer(Element el) {
        Element parent = el.parent();
        while (parent != null) {
            String parentTag = parent.tagName().toLowerCase();
            String className = parent.className().toLowerCase();
            if (parentTag.matches("header|footer|nav") ||
                    className.matches(".*(header|footer|nav|menu).*")) {
                return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    private boolean appendParagraph(Element el, StringBuilder builder) {
        String text = normalize(el.text());
        if (text.length() > 20) {
            if (!builder.isEmpty()) builder.append("\n\n");
            builder.append(text);
            return true;
        }
        return false;
    }

    private String extractAdditionalInfo(Document doc) {
        String info = doc.select(".info, .additional-info, .meta-info").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> !text.isBlank() && text.length() < 500)
                .collect(Collectors.joining(" | "));

        return info.isEmpty() ? null : info;
    }

    private String extractCategory(Document doc, String url) {
        List<String> categories = new ArrayList<>();
        String title = extractTitle(doc);

        if (isGuestPlay(url, title)) {
            categories.add(GUEST_PLAY_CATEGORY);
        }

        String description = getCategoryDescriptionContext(doc);
        if (description != null) {
            GENRE_KEYWORDS.stream()
                    .filter(description::contains)
                    .map(this::capitalize)
                    .filter(genre -> !categories.contains(genre))
                    .forEach(categories::add);
        }

        if (categories.isEmpty() && !doc.select("a[href*='/sceny/']").isEmpty()) {
            categories.add(DEFAULT_CATEGORY);
        }

        return categories.isEmpty() ? DEFAULT_CATEGORY : String.join(", ", categories);
    }

    private String getCategoryDescriptionContext(Document doc) {
        String desc = extractMetaContent(doc, "og:description");
        if (desc == null || desc.isBlank()) {
            Element firstPara = doc.selectFirst(".entry-content p, article p, p");
            return firstPara != null ? normalize(firstPara.text()).toLowerCase() : null;
        }
        return desc.toLowerCase();
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> result = new ArrayList<>();
        Element tworcyHeader = findHeaderContaining(doc, "twórcy");

        if (tworcyHeader != null) {
            String currentRole = null;
            for (Element el : elementsAfterHeader(doc, tworcyHeader)) {
                String tagName = el.tagName().toLowerCase();
                if (tagName.equals("h2")) {
                    currentRole = normalize(el.text());
                } else if (tagName.equals("h3") && currentRole != null && !currentRole.isBlank()) {
                    String name = normalize(el.text());
                    if (!name.isBlank()) {
                        result.add(createContributor(currentRole, name));
                        currentRole = null;
                    }
                }
            }
        }

        if (result.isEmpty()) {
            result.addAll(extractContributorsFallback(doc));
        }
        return result;
    }

    private List<ContributorDto> extractContributorsFallback(Document doc) {
        List<ContributorDto> fallback = new ArrayList<>();
        for (Element el : doc.select(".entry-content p, .entry-content li, article p")) {
            String text = normalize(el.text());
            for (String role : CONTRIBUTOR_ROLES) {
                if (text.startsWith(role)) {
                    String name = text.substring(role.length()).replaceAll("[,;.]$", "").trim();
                    if (!name.isBlank()) {
                        fallback.add(createContributor(role.replace(":", ""), name));
                        break;
                    }
                }
            }
        }
        return fallback;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> result = new ArrayList<>();
        Element obsadaHeader = findHeaderContaining(doc, "obsada");

        if (obsadaHeader != null) {
            for (Element el : elementsAfterHeader(doc, obsadaHeader)) {
                if (el.tagName().equalsIgnoreCase("a") && el.attr("href").contains("/osoby/")) {
                    CastMemberDto member = parseCastMemberCard(el);
                    if (member != null) result.add(member);
                }
            }
        }

        if (result.isEmpty()) {
            result.addAll(extractCastFallback(doc));
        }
        return result;
    }

    private CastMemberDto parseCastMemberCard(Element container) {
        Elements h5Elements = container.select("h5");
        if (h5Elements.size() >= 3) {
            String actorName = normalize(h5Elements.get(0).text());
            String characterName = normalize(h5Elements.get(2).text());
            if (!actorName.isBlank() && !actorName.equals("Więcej") && !characterName.equals("_")) {
                return createCastMember(actorName + " - " + characterName, container);
            }
        } else if (h5Elements.size() == 1) {
            String actorName = normalize(h5Elements.get(0).text());
            if (!actorName.isBlank() && !actorName.equals("Więcej")) {
                return createCastMember(actorName, container);
            }
        }
        return null;
    }

    private List<CastMemberDto> extractCastFallback(Document doc) {
        List<CastMemberDto> fallback = new ArrayList<>();
        Element castSection = findSectionByHeaders(doc, "Obsada", "Występują", "Aktorzy");

        if (castSection != null) {
            for (Element el : castSection.select("p, li")) {
                String text = normalize(el.text());
                if (text.isBlank() || text.length() < 3) continue;

                Arrays.stream(text.split("[,;]"))
                        .map(this::normalize)
                        .filter(name -> name.length() > 3 && name.matches(".*[A-ZĄĆĘŁŃÓŚŹŻ].*"))
                        .forEach(name -> fallback.add(createCastMember(name, null)));
            }
        }
        return fallback;
    }

    private List<String> extractGalleryImages(Document doc) {
        List<String> result = new ArrayList<>(doc.select(".gallery, .wp-block-gallery, .image-gallery").stream()
                .flatMap(gallery -> gallery.select("img[src]").stream())
                .map(img -> img.absUrl("src"))
                .filter(url -> !url.isBlank())
                .distinct()
                .toList());

        if (result.isEmpty()) {
            Element content = doc.selectFirst(".entry-content, article");
            if (content != null) {
                content.select("img[src]").stream()
                        .map(img -> img.absUrl("src"))
                        .filter(url -> !url.isBlank())
                        .distinct()
                        .forEach(result::add);
            }
        }
        return result;
    }

    private List<UpcomingTermDto> extractUpcomingTerms(Document doc) {
        List<UpcomingTermDto> result = new ArrayList<>();
        Element scheduleSection = findSectionByHeaders(doc, "Terminarz", "Najbliższe spektakle", "Repertuar");

        if (scheduleSection != null) {
            for (Element dateEl : scheduleSection.select(".date, .event-date, time")) {
                UpcomingTermDto dto = new UpcomingTermDto();
                dto.setDayLabel(normalize(dateEl.text()));

                Element ticketLink = dateEl.selectFirst("a[href*='bilet'], a[href*='ticket']");
                if (ticketLink != null) {
                    dto.setTicketUrl(ticketLink.absUrl("href"));
                    dto.setStatus("AVAILABLE");
                } else {
                    dto.setStatus("INFO");
                }
                result.add(dto);
            }
        }
        return result;
    }

    private String extractPremiereDate(Document doc) {
        return doc.select("p, li, span, div, strong").stream()
                .map(Element::text)
                .map(this::normalize)
                .filter(text -> text.toLowerCase().contains("premiera") && text.length() < 100)
                .findFirst()
                .orElse(null);
    }

    private List<Element> elementsAfterHeader(Document doc, Element header) {
        List<Element> result = new ArrayList<>();
        boolean found = false;
        for (Element el : doc.select("*")) {
            if (!found && el.equals(header)) { found = true; continue; }
            if (found) {
                if (el.tagName().equalsIgnoreCase("h4")) break;
                result.add(el);
            }
        }
        return result;
    }

    private Element findHeaderContaining(Document doc, String keyword) {
        return doc.select("h4").stream()
                .filter(h4 -> normalize(h4.text()).toLowerCase().contains(keyword))
                .findFirst()
                .orElse(null);
    }

    private Element findSectionByHeaders(Document doc, String... headers) {
        for (String header : headers) {
            for (Element el : doc.select("h2, h3, h4, strong, b")) {
                if (normalize(el.text()).toLowerCase().contains(header.toLowerCase())) {
                    Element parent = el.parent();
                    while (parent != null && !parent.tagName().equals("section")
                            && !parent.hasClass("section") && !parent.tagName().equals("div")) {
                        parent = parent.parent();
                    }
                    return parent != null ? parent : el.parent();
                }
            }
        }
        return null;
    }

    private String extractTextFromFirst(Document doc, String cssQuery) {
        Element el = doc.selectFirst(cssQuery);
        if (el != null) {
            String value = normalize(el.text());
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private String extractMetaContent(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta != null) {
            String value = normalize(meta.attr("content"));
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private ContributorDto createContributor(String role, String name) {
        ContributorDto dto = new ContributorDto();
        dto.setRole(role);
        dto.setName(name);
        return dto;
    }

    private CastMemberDto createCastMember(String name, Element container) {
        CastMemberDto dto = new CastMemberDto();
        dto.setName(name);
        if (container != null) {
            dto.setProfileUrl(container.absUrl("href"));
            Element img = container.selectFirst("img");
            if (img != null) {
                dto.setImageUrl(img.absUrl("src"));
            }
        }
        return dto;
    }

    private boolean isHeaderTag(String tagName) {
        return tagName.equals("h2") || tagName.equals("h3") || tagName.equals("h4");
    }

    private boolean isStopKeyword(String text) {
        String lowerText = normalize(text).toLowerCase();
        return STOP_KEYWORDS.stream().anyMatch(lowerText::contains);
    }

    private boolean isGuestPlay(String url, String title) {
        String urlLower = url.toLowerCase();
        return urlLower.contains("goscinny") || urlLower.contains("gosc") ||
                (title != null && title.toLowerCase().contains("gościnny"));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}