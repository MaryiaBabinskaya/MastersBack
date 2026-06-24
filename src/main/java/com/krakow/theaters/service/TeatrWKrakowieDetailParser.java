package com.krakow.theaters.service;

import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.PlayDetailsDto;
import com.krakow.theaters.dto.UpcomingTermDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TeatrWKrakowieDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
        return parse(doc, url);
    }

    public PlayDetailsDto parse(Document doc, String url) {
        PlayDetailsDto dto = new PlayDetailsDto();
        dto.setSource(extractSource(doc, url));
        dto.setTitle(extractTitle(doc));
        dto.setImageUrl(extractImageUrl(doc));
        dto.setScene(extractScene(doc));
        dto.setDurationMinutesText(extractDuration(doc));
        dto.setDescription(extractDescription(doc));
        dto.setAdditionalInfo(extractAdditionalInfo(doc));
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setGalleryImages(extractGalleryImages(doc));
        dto.setUpcomingTerms(extractUpcomingTerms(doc));
        dto.setYoutubeUrl(extractYoutubeUrl(doc));

        log.info("Sparsowano spektakl: {}", dto.getTitle());
        return dto;
    }

    private String extractSource(Document doc, String fallbackUrl) {
        Element ogUrl = doc.selectFirst("meta[property=og:url]");
        if (ogUrl != null) {
            String value = ogUrl.attr("content").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallbackUrl;
    }

    private String extractTitle(Document doc) {
        Element h1 = doc.selectFirst("h1.title");
        if (h1 != null) {
            String value = normalize(h1.text());
            if (!value.isBlank()) {
                return cleanTitle(value);
            }
        }

        Element title = doc.selectFirst("title");
        if (title != null) {
            String value = normalize(title.text());
            if (!value.isBlank()) {
                return cleanTitle(value);
            }
        }

        return null;
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return title;
        }

        title = title.replaceFirst("^WYDARZENIE ZEWN[ĘE]TRZNE\\s*-\\s*", "");
        String[] patterns = {
                " Na podstawie ",
                " W CYKLU ",
                " JAZZ - FILOZOFIA - STAND-UP",
                " SPEKTAKL GRANY W ",
                " SW ",
                " spektakl w ",
                " / spektakl w ",
                " koprodukcja z "
        };

        for (String pattern : patterns) {
            int idx = title.indexOf(pattern);
            if (idx > 0) {
                title = title.substring(0, idx);
                break;
            }
        }
        return title.trim();
    }

    private String extractImageUrl(Document doc) {
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) {
            String value = ogImage.attr("content").trim();
            if (!value.isBlank()) {
                return value;
            }
        }

        Element sliderImage = doc.selectFirst(".media-container img");
        if (sliderImage != null) {
            String dataOriginal = sliderImage.absUrl("data-original");
            if (!dataOriginal.isBlank()) {
                return dataOriginal;
            }
            String src = sliderImage.absUrl("src");
            if (!src.isBlank()) {
                return src;
            }
        }

        return null;
    }

    private String extractScene(Document doc) {
        Element el = doc.selectFirst(".info-container .column:nth-of-type(2) li.bordered");
        return el != null ? normalize(el.text()) : null;
    }

    private String extractDuration(Document doc) {
        Element timeBar = doc.selectFirst(".time-bar");
        if (timeBar == null) {
            return null;
        }

        for (Element col : timeBar.select(".col")) {
            String text = normalize(col.text());
            if (text.contains("min")) {
                return text;
            }
        }

        String text = normalize(timeBar.text());
        return text.isBlank() ? null : text;
    }

    private String extractDescription(Document doc) {
        Elements candidates = doc.select(".show-more-container .show-more-container-inner");
        for (Element candidate : candidates) {
            if (candidate.selectFirst(".gallery-thumbnails") != null) {
                continue;
            }

            if (candidate.selectFirst("button.show-more-trigger") != null) {
                continue;
            }

            String html = candidate.html();

            Document temp = Jsoup.parse(html);
            String text = normalize(temp.text());

            if (text.length() < 100) {
                continue;
            }

            if (text.toLowerCase().contains("alternatywa tekstowa")) {
                continue;
            }

            int obsadaIndex = -1;
            if (html.contains("<strong>Obsada:</strong>")) {
                obsadaIndex = html.indexOf("<strong>Obsada:</strong>");
            } else if (html.contains("<p>Obsada:</p>")) {
                obsadaIndex = html.indexOf("<p>Obsada:</p>");
            } else if (html.contains(">Obsada:</")) {
                obsadaIndex = html.indexOf(">Obsada:</");
            }

            if (obsadaIndex >= 0) {
                html = html.substring(0, obsadaIndex);
                temp = Jsoup.parse(html);
                text = normalize(temp.text());
            }

            if (html.contains("<strong>O spektaklu:</strong>")) {
                int firstOccurrence = html.indexOf("<strong>O spektaklu:</strong>");
                int secondOccurrence = html.indexOf("<strong>O spektaklu:</strong>", firstOccurrence + 1);

                if (secondOccurrence > 0) {
                    html = html.substring(0, secondOccurrence);
                    temp = Jsoup.parse(html);
                    text = normalize(temp.text());
                }
            }

            if (text.length() > 100) {
                return text;
            }
        }
        return null;
    }

    private String extractAdditionalInfo(Document doc) {
        Element secondColumn = doc.selectFirst(".info-container .column:nth-of-type(2)");
        if (secondColumn == null) {
            return null;
        }

        List<String> additionalInfoItems = new ArrayList<>();
        for (Element li : secondColumn.select("li")) {
            if (li.hasClass("bordered")) {
                continue;
            }

            if (li.selectFirst(".tickets, a.btn-tickets, .btn-tickets") != null) {
                continue;
            }

            String text = normalize(li.text());

            if (text.isBlank()) {
                continue;
            }

            if (text.startsWith("Scena ") || text.matches("^[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+$")) {
                continue;
            }

            additionalInfoItems.add(text);
        }

        if (additionalInfoItems.isEmpty()) {
            return null;
        }
        return String.join("\n", additionalInfoItems);
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> result = new ArrayList<>();

        Element firstColumn = doc.selectFirst(".info-container .column:first-of-type");
        if (firstColumn == null) {
            return result;
        }

        for (Element li : firstColumn.select("li")) {
            String fullText = normalize(li.text());
            if (fullText.isBlank()) {
                continue;
            }

            int colonIndex = fullText.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            String role = normalize(fullText.substring(0, colonIndex));
            String name;
            String profileUrl = null;

            Element a = li.selectFirst("a[href]");
            if (a != null) {
                name = normalize(a.text());
                profileUrl = a.absUrl("href");
            } else {
                name = normalize(fullText.substring(colonIndex + 1));
            }

            if (name.isBlank()) {
                continue;
            }

            ContributorDto dto = new ContributorDto();
            dto.setRole(role);
            dto.setName(name);
            dto.setProfileUrl(profileUrl);
            result.add(dto);
        }

        return result;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> result = new ArrayList<>();

        Element castBlock = findBlockByHeader(doc, "Obsada");
        if (castBlock == null) {
            return result;
        }

        for (Element a : castBlock.select("a[href]")) {
            Element nameEl = a.selectFirst(".person-title");
            if (nameEl == null) {
                continue;
            }

            String fullText = normalize(nameEl.text());
            if (fullText.isBlank()) {
                continue;
            }

            Element signatureEl = a.selectFirst(".person-signature");
            Element subtitleEl = a.selectFirst(".person-subtitle");

            String actorName = fullText;
            String role;

            if (signatureEl != null) {
                role = normalize(signatureEl.text());
            } else if (subtitleEl != null) {
                role = normalize(subtitleEl.text());
            } else {
                String[] parts = splitActorAndRole(fullText);
                actorName = parts[0];
                role = parts[1];
            }

            CastMemberDto dto = new CastMemberDto();
            dto.setName(actorName);
            dto.setRole(role);
            dto.setProfileUrl(a.absUrl("href"));

            Element slide = a.selectFirst(".slide[style]");
            if (slide != null) {
                dto.setImageUrl(extractUrlFromStyle(doc, slide.attr("style")));
            }

            if (dto.getName() != null && !dto.getName().isBlank()) {
                result.add(dto);
            }
        }

        return result;
    }

    private String[] splitActorAndRole(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return new String[]{null, null};
        }

        String cleaned = fullText
                .replace("(gościnnie)", "")
                .replace("(goscinnie)", "")
                .replace("(dublura)", "")
                .trim();

        String actorName;
        String role;

        if (cleaned.contains(" - ")) {
            String[] parts = cleaned.split(" - ", 2);
            actorName = parts[0].trim();
            role = parts.length > 1 ? parts[1].trim() : null;
        } else {
            String[] words = cleaned.split("\\s+");

            if (words.length <= 2) {
                actorName = cleaned;
                role = null;
            } else if (words.length == 3) {
                actorName = words[0] + " " + words[1];
                role = words[2];
            } else {
                actorName = words[0] + " " + words[1];
                role = String.join(" ", java.util.Arrays.copyOfRange(words, 2, words.length));
            }
        }

        if (fullText.contains("(gościnnie)") || fullText.contains("(goscinnie)")) {
            actorName = actorName + " (gościnnie)";
        }
        if (fullText.contains("(dublura)")) {
            actorName = actorName + " (dublura)";
        }

        return new String[]{actorName, role};
    }

    private List<String> extractGalleryImages(Document doc) {
        List<String> result = new ArrayList<>();

        Element galleryBlock = findBlockByHeader(doc, "fot. ");
        if (galleryBlock == null) {
            galleryBlock = doc.selectFirst(".block-gallery");
        }

        if (galleryBlock == null) {
            return result;
        }

        for (Element img : galleryBlock.select("img")) {
            String url = img.absUrl("data-original");
            if (url.isBlank()) {
                url = img.absUrl("src");
            }
            if (!url.isBlank() && !result.contains(url)) {
                result.add(url);
            }
        }

        return result;
    }

    private List<UpcomingTermDto> extractUpcomingTerms(Document doc) {
        List<UpcomingTermDto> result = new ArrayList<>();

        for (Element date : doc.select(".calendar .date")) {
            UpcomingTermDto dto = new UpcomingTermDto();

            Element monthEl = date.selectFirst(".date-month");
            Element dayEl = date.selectFirst(".date-number");
            Element dayLabelEl = date.selectFirst(".date-label");
            Element timeEl = date.selectFirst(".time");
            Element ticketEl = date.selectFirst("a.btn-tickets[href]");
            Element infoEl = date.selectFirst(".btn-info");

            dto.setMonth(monthEl != null ? normalize(monthEl.text()) : null);
            dto.setDayOfMonth(dayEl != null ? normalize(dayEl.text()) : null);
            dto.setDayLabel(dayLabelEl != null ? normalize(dayLabelEl.text()) : null);
            dto.setTime(timeEl != null ? normalize(timeEl.text()) : null);
            dto.setTicketUrl(ticketEl != null ? ticketEl.absUrl("href") : null);

            String status = infoEl != null ? normalize(infoEl.text()) : null;
            if (status == null || status.isBlank()) {
                status = dto.getTicketUrl() != null && !dto.getTicketUrl().isBlank()
                        ? "AVAILABLE" : null;
            }
            dto.setStatus(status);

            result.add(dto);
        }

        return result;
    }

    private Element findBlockByHeader(Document doc, String headerFragment) {
        for (Element block : doc.select(".block")) {
            Element h2 = block.selectFirst("h2");
            if (h2 != null) {
                String text = normalize(h2.text().toLowerCase());
                if (text.contains(headerFragment.toLowerCase())) {
                    return block;
                }
            }
        }
        return null;
    }

    private String extractUrlFromStyle(Document doc, String style) {
        if (style == null || style.isBlank()) {
            return null;
        }

        String extracted = null;

        int start = style.indexOf("url('");
        if (start >= 0) {
            int from = start + 5;
            int end = style.indexOf("')", from);
            if (end > from) {
                extracted = style.substring(from, end);
            }
        }

        if (extracted == null) {
            start = style.indexOf("url(\"");
            if (start >= 0) {
                int from = start + 5;
                int end = style.indexOf("\")", from);
                if (end > from) {
                    extracted = style.substring(from, end);
                }
            }
        }

        if (extracted == null) {
            start = style.indexOf("url(");
            if (start >= 0) {
                int from = start + 4;
                int end = style.indexOf(")", from);
                if (end > from) {
                    extracted = style.substring(from, end).replace("'", "").replace("\"", "").trim();
                }
            }
        }

        if (extracted == null || extracted.isBlank()) {
            return null;
        }

        doc.location();
        return !doc.location().isBlank()
                ? URI.create(doc.location()).resolve(extracted).toString() : extracted;
    }

    private String extractYoutubeUrl(Document doc) {
        for (Element iframe : doc.select("iframe[src*=youtube.com/embed]")) {
            String src = iframe.attr("src");
            if (!src.isBlank()) {
                String videoId = extractYoutubeVideoId(src);
                if (videoId != null) {
                    return "https://www.youtube.com/watch?v=" + videoId;
                }
            }
        }

        for (Element a : doc.select("a[href*=youtube.com], a[href*=youtu.be]")) {
            String href = a.absUrl("href");
            if (!href.isBlank()) {
                String videoId = extractYoutubeVideoId(href);
                if (videoId != null) {
                    return "https://www.youtube.com/watch?v=" + videoId;
                }
            }
        }

        return null;
    }

    private String extractYoutubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        if (url.contains("/embed/")) {
            int start = url.indexOf("/embed/") + 7;
            int end = url.indexOf("?", start);
            if (end < 0) end = url.indexOf("&", start);
            if (end < 0) end = url.length();

            String videoId = url.substring(start, end);
            return videoId.startsWith("/") ? videoId.substring(1) : videoId;
        }

        if (url.contains("watch?v=")) {
            int start = url.indexOf("watch?v=") + 8;
            int end = url.indexOf("&", start);
            if (end < 0) end = url.length();

            String videoId = url.substring(start, end);
            return videoId.startsWith("/") ? videoId.substring(1) : videoId;
        }

        if (url.contains("youtu.be/")) {
            int start = url.indexOf("youtu.be/") + 9;
            int end = url.indexOf("?", start);
            if (end < 0) end = url.indexOf("&", start);
            if (end < 0) end = url.length();

            String videoId = url.substring(start, end);
            return videoId.startsWith("/") ? videoId.substring(1) : videoId;
        }

        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}