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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class StaryDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów wydarzenia Stary Teatr: {}", url);
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
        dto.setCategory(extractCategory(doc));
        dto.setYoutubeUrl(extractYouTubeLink(doc));
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setUpcomingTerms(extractUpcomingTerms(doc));
        dto.setGalleryImages(extractGalleryImages(doc));

        return dto;
    }

    private String extractTitle(Document doc) {
        Elements ogTitles = doc.select("meta[property=og:title]");
        for (Element ogTitle : ogTitles) {
            String value = normalize(ogTitle.attr("content"));
            if (!value.isBlank() &&
                    !value.toLowerCase().contains("sprawdź terminy") &&
                    !value.toLowerCase().contains("narodowy stary teatr") &&
                    value.length() > 2 && value.length() < 100) {
                log.info("Znaleziono tytuł w og:title: {}", value);
                return value;
            }
        }

        Elements ticketLinks = doc.select("a[href*='/kup-bilety/']");
        for (Element link : ticketLinks) {
            String title = link.attr("title");
            if (title.contains("Spektakl:")) {
                try {
                    int start = title.indexOf("Spektakl:") + "Spektakl:".length();
                    int end = title.indexOf(" - ", start);
                    if (end > start) {
                        String playTitle = normalize(title.substring(start, end));
                        if (playTitle.length() > 2 && playTitle.length() < 100) {
                            log.info("Znaleziono tytuł w atrybucie title linku do biletów: {}", playTitle);
                            return playTitle;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Błąd parsowania tytułu z linku: {}", e.getMessage());
                }
            }
        }

        Elements allElements = doc.select("div, p, span, h1, h2, h3, h4, strong, b");
        for (Element el : allElements) {
            String text = normalize(el.text());
            if (text.toLowerCase().contains("reż.") || text.toLowerCase().contains("reżyseria")) {
                String[] parts = text.split("reż\\.");
                if (parts.length > 0 && parts[0].trim().length() > 2 && parts[0].trim().length() < 100) {
                    String candidateTitle = parts[0].trim();
                    candidateTitle = candidateTitle.replaceAll("\\(.*?\\)", "").trim();
                    if (candidateTitle.length() > 2 && !candidateTitle.toLowerCase().contains("spektakle")) {
                        log.info("Znaleziono tytuł przed 'reż.': {}", candidateTitle);
                        return candidateTitle;
                    }
                }
            }
        }

        log.warn("Nie znaleziono tytułu dla wydarzenia");
        return null;
    }

    private String extractImageUrl(Document doc) {
        for (Element img : doc.select("img[src*='image.bilety24.pl']")) {
            String src = img.absUrl("src");
            if (!src.contains(".svg") && !src.contains("logo") && !src.contains("_thumb_") && !src.contains("dealer-default")) {
                return src;
            }
        }

        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) {
            String value = ogImage.attr("content").trim();
            if (!value.isBlank() && !value.contains(".svg")) {
                return value;
            }
        }

        Element mainImage = doc.selectFirst("img[alt='Grafika']");
        if (mainImage != null) {
            String src = mainImage.absUrl("src");
            if (!src.isBlank() && !src.contains(".svg")) {
                return src;
            }
        }

        return null;
    }

    private String extractScene(Document doc) {
        String fullText = doc.text();
        String[] venues = {
                "Duża Scena",
                "Nowa Scena",
                "Mała Scena",
                "ul. Jagiellońska 1",
                "Stary Teatr"
        };

        for (String venue : venues) {
            if (fullText.contains(venue)) {
                Elements paragraphs = doc.select("p, li, div, span");
                for (Element p : paragraphs) {
                    String text = normalize(p.text());
                    if (text.contains(venue)) {
                        return text.length() < 200 ? text : venue;
                    }
                }
                return venue;
            }
        }
        return "Stary Teatr";
    }

    private String extractDuration(Document doc) {
        String fullText = doc.text();
        if (fullText.contains("Czas trwania")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Czas trwania\\s+(.*?)(?=\\.|\\n|$)", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String duration = matcher.group(1).trim();
                if (duration.matches(".*\\d+\\s*(godz|min).*")) {
                    return duration.split("(reż|Obsada|Twórcy)")[0].trim();
                }
            }
        }

        if (fullText.contains("godzin") || fullText.contains("minut")) {
            Elements elements = doc.select("p, li, span, div");
            for (Element el : elements) {
                String text = normalize(el.text());
                if ((text.contains("godzin") || text.contains("minut")) && text.length() < 100) {
                    if (text.matches(".*\\d+\\s*(godzin|minut).*")) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }

        log.info("Original description length: {}", description.length());
        String original = description;

        int endIndex = getEndIndex(description);

        description = description.substring(0, endIndex).trim();

        description = description.replaceAll("(?i)^(wg powieści|na motywach)[^\n]*\n?", "");

        description = description.replaceAll("(?i)^.*reż\\..*\n?", "");

        description = description.replaceAll("(?i)^[^\n]*?Czas trwania[^\n]*\n?", "");
        description = description.replaceAll("(?i)\\bCzas trwania[^\n]*\n?", "");

        description = description.replaceAll("(?i)^(Nowa Scena|Duża Scena|Mała Scena)[^\n]*\n?", "");
        description = description.replaceAll("(?i)^ul\\.\\s*Jagiellońska[^\n]*\n?", "");

        description = description.replaceAll("(?i)^\\s*\\d+\\s*(godz|godzin|min|minut)[^\n]*\n?", "");

        description = description.replaceAll("\n{3,}", "\n\n");
        description = description.trim();

        log.info("Cleaned description length: {} (removed {} chars)", description.length(), original.length() - description.length());
        return description;
    }

    private static int getEndIndex(String description) {
        int endIndex = description.length();
        int obsadaIndex = description.indexOf("Obsada:");
        if (obsadaIndex < 0) {
            obsadaIndex = description.toLowerCase().indexOf("obsada:");
        }
        int tworczyIndex = description.indexOf("Twórcy:");
        if (tworczyIndex < 0) {
            tworczyIndex = description.toLowerCase().indexOf("twórcy:");
        }

        if (obsadaIndex > 0 && obsadaIndex < endIndex) {
            endIndex = obsadaIndex;
        }
        if (tworczyIndex > 0 && tworczyIndex < endIndex) {
            endIndex = tworczyIndex;
        }
        return endIndex;
    }

    private String extractDescription(Document doc) {
        Elements descriptionDivs = doc.select("div.title-description-content, div.description-content");
        log.info("Znaleziono {} divów z opisem", descriptionDivs.size());

        String longestDescription = null;
        int maxLength = 0;

        for (Element div : descriptionDivs) {
            log.info("Przetwarzam div z opisem...");
            String html = div.html();
            String textFromHtml = html
                    .replaceAll("(?i)<br[^>]*>", "\n")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&")
                    .trim();

            String fullText = normalize(textFromHtml);

            fullText = fullText.replaceAll("(?i)czytaj więcej", "").trim();

            log.info("Długość tekstu z div (HTML method): {} znaków", fullText.length());

            if (fullText.length() > 100) {
                String lowerText = fullText.toLowerCase();
                if (!lowerText.contains("wszystkie prawa") &&
                        !lowerText.contains("polityka prywatności") &&
                        !lowerText.contains("regulamin serwisu")) {

                    fullText = cleanDescription(fullText);

                    if (fullText.length() > maxLength) {
                        longestDescription = fullText;
                        maxLength = fullText.length();
                    }
                }
            }
        }

        if (longestDescription != null) {
            log.info("Zwracam najdłuższy opis: {} znaków", maxLength);
            return longestDescription;
        }

        Elements allParagraphs = doc.select("p");
        List<String> descriptionParagraphs = new ArrayList<>();

        for (Element p : allParagraphs) {
            if (isInNavigationArea(p)) {
                continue;
            }

            String text = normalize(p.text());

            if (text.length() > 50 && !text.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
                String lowerText = text.toLowerCase();
                if (!lowerText.contains("wszystkie prawa") &&
                        !lowerText.contains("polityka prywatności") &&
                        !lowerText.contains("regulamin serwisu") &&
                        !lowerText.contains("kup bilet") &&
                        !lowerText.equals("czytaj więcej")) {
                    descriptionParagraphs.add(text);
                }
            }
        }

        if (!descriptionParagraphs.isEmpty()) {
            String combined = String.join("\n\n", descriptionParagraphs);
            log.debug("Zebrano opis z {} akapitów, łącznie {} znaków", descriptionParagraphs.size(), combined.length());
            return cleanDescription(combined);
        }

        Element ogDesc = doc.selectFirst("meta[property=og:description]");
        if (ogDesc != null) {
            String value = normalize(ogDesc.attr("content"));
            if (!value.isBlank() && value.length() > 50) {
                return value;
            }
        }
        return null;
    }

    private boolean isInNavigationArea(Element element) {
        Element parent = element;
        while (parent != null) {
            String parentTag = parent.tagName().toLowerCase();
            String className = parent.className().toLowerCase();
            if (parentTag.equals("header") || parentTag.equals("footer") || parentTag.equals("nav") ||
                    className.contains("header") || className.contains("footer") || className.contains("nav") || className.contains("menu")) {
                return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> result = new ArrayList<>();
        String[] rolePatterns = {
                "reżyseria", "scenografia", "kostiumy", "muzyka", "choreografia",
                "asystent reżysera", "asystent scenografa", "dramaturgia",
                "tłumaczenie", "adaptacja", "tekst"
        };

        Elements allText = doc.select("p, li, div");
        for (Element el : allText) {
            String text = normalize(el.text()).toLowerCase();
            for (String role : rolePatterns) {
                if (text.contains(role + ":") || text.contains(role + " ")) {
                    String[] parts = text.split(role + "[:\\s]+", 2);
                    if (parts.length == 2) {
                        String name = parts[1].trim();
                        for (String otherRole : rolePatterns) {
                            if (name.toLowerCase().contains(otherRole)) {
                                name = name.substring(0, name.toLowerCase().indexOf(otherRole)).trim();
                                break;
                            }
                        }
                        if (name.length() > 2 && name.length() < 100) {
                            ContributorDto dto = new ContributorDto();
                            dto.setRole(capitalize(role));
                            dto.setName(name);
                            dto.setProfileUrl(null);
                            result.add(dto);
                            log.debug("Dodano twórcę: {} - {}", role, name);
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> result = new ArrayList<>();

        String[] blacklist = {
                "bilety", "muzeum", "repertuar", "newsletter", "główne menu",
                "menu", "kontakt", "warto wiedzieć", "dla kupujących", "informacje",
                "regulamin", "polityka", "spektakle", "wydarzenia", "dostawca",
                "system", "sprzedaży", "prawa", "zastrzeżone", "wszelkie", "©",
                "2025", "2024", "2026", "cookie", "rodo", "nowa scena",
                "mała scena", "duża scena", "stary teatr", "ul.", "jagiellońska"
        };

        Elements allElements = doc.select("p, li, div");
        for (Element el : allElements) {
            String text = normalize(el.text());
            String textLower = text.toLowerCase();

            if (textLower.contains("obsada")) {
                String html = el.html();
                int obsadaIndex = html.toUpperCase().indexOf("OBSADA");
                if (obsadaIndex >= 0) {
                    String afterObsada = html.substring(obsadaIndex + "OBSADA".length());
                    String[] lines = afterObsada.split("(?i)<br[^>]*>");

                    int consecutiveNonCastLines = 0;
                    boolean hasFoundAnyCast = false;

                    for (String line : lines) {
                        String cleanLine = normalize(line.replaceAll("<[^>]*>", ""));
                        String lineLower = cleanLine.toLowerCase();

                        if (cleanLine.isEmpty() || lineLower.contains("class=") ||
                                lineLower.contains("read-more") || lineLower.contains("<p>")) {
                            if (hasFoundAnyCast) {
                                consecutiveNonCastLines++;
                                if (consecutiveNonCastLines >= 3) {
                                    break;
                                }
                            }
                            continue;
                        }

                        if (cleanLine.length() < 10 || cleanLine.length() > 60) {
                            if (hasFoundAnyCast) {
                                consecutiveNonCastLines++;
                                if (consecutiveNonCastLines >= 2) {
                                    break;
                                }
                            }
                            continue;
                        }

                        if (lineLower.contains("premiera") || lineLower.contains("terminarz") ||
                                lineLower.contains("kup bilet") || lineLower.contains("daty") ||
                                lineLower.contains("teatr dla") || lineLower.contains("radio") ||
                                lineLower.contains("spektakl bierze") || lineLower.contains("konkurs")) {
                            break;
                        }

                        boolean isBlacklisted = false;
                        for (String blacklisted : blacklist) {
                            if (lineLower.contains(blacklisted)) {
                                isBlacklisted = true;
                                break;
                            }
                        }

                        if (!isBlacklisted) {
                            String[] names = cleanLine.split("[,;]");
                            boolean foundNameInLine = false;

                            for (String name : names) {
                                name = normalize(name);
                                if (name.matches(".*[A-ZĄĆĘŁŃÓŚŹŻ].*\\s+.*[A-ZĄĆĘŁŃÓŚŹŻ].*")) {
                                    CastMemberDto dto = new CastMemberDto();
                                    dto.setName(name);
                                    dto.setProfileUrl(null);
                                    dto.setImageUrl(null);
                                    result.add(dto);
                                    log.debug("Dodano aktora: {}", name);
                                    foundNameInLine = true;
                                    hasFoundAnyCast = true;
                                }
                            }

                            if (foundNameInLine) {
                                consecutiveNonCastLines = 0;
                            } else {
                                if (hasFoundAnyCast) {
                                    consecutiveNonCastLines++;
                                    if (consecutiveNonCastLines >= 3) {
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (hasFoundAnyCast) {
                                consecutiveNonCastLines++;
                                if (consecutiveNonCastLines >= 3) {
                                    break;
                                }
                            }
                        }
                    }

                    if (!result.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    private List<UpcomingTermDto> extractUpcomingTerms(Document doc) {
        List<UpcomingTermDto> result = new ArrayList<>();
        Elements ticketLinks = doc.select("a[href*='/kup-bilety/']");
        for (Element link : ticketLinks) {
            String href = link.absUrl("href");
            UpcomingTermDto dto = new UpcomingTermDto();

            Element dateSpan = link.selectFirst("span.date");
            Element hourSpan = link.selectFirst("span.hour");

            if (dateSpan != null && hourSpan != null) {
                String date = normalize(dateSpan.text());
                String hour = normalize(hourSpan.text());

                if (!date.isBlank() && !hour.isBlank()) {
                    try {
                        String[] dateParts = date.split("\\.");
                        if (dateParts.length == 3) {
                            String isoDate = dateParts[2] + "-" + dateParts[1] + "-" + dateParts[0] + " " + hour;
                            dto.setDayLabel(isoDate);
                        } else {
                            dto.setDayLabel(date + " " + hour);
                        }
                    } catch (Exception e) {
                        log.debug("Błąd konwersji daty: {}", e.getMessage());
                        dto.setDayLabel(date + " " + hour);
                    }
                }
            } else {
                String title = link.attr("title");
                if (title.contains(" - ")) {
                    String[] parts = title.split(" - ");
                    if (parts.length >= 4) {
                        String datePart = parts[2].trim().split(" ")[0];
                        String timePart = parts[3].trim();
                        dto.setDayLabel(datePart + " " + timePart);
                    } else if (parts.length == 3) {
                        dto.setDayLabel(parts[2].trim());
                    }
                }
            }

            dto.setTicketUrl(href);
            dto.setStatus("AVAILABLE");

            if (dto.getDayLabel() != null && !dto.getDayLabel().isBlank()) {
                result.add(dto);
            }
        }
        log.debug("Znaleziono {} terminów", result.size());
        return result;
    }

    private List<String> extractGalleryImages(Document doc) {
        List<String> result = new ArrayList<>();
        for (Element img : doc.select("img[src*='bilety24.pl']")) {
            String url = img.absUrl("src");
            if (!url.isBlank() && !result.contains(url)) {
                result.add(url);
            }
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String extractCategory(Document doc) {
        List<String> categories = new ArrayList<>();

        Elements categoryLinks = doc.select("a[href*='b24_title_category']");
        for (Element link : categoryLinks) {
            String category = normalize(link.text());
            if (!category.isBlank() && !category.toLowerCase().contains("wszystkie") && !categories.contains(category)) {
                categories.add(category);
            }
        }

        if (categories.isEmpty()) {
            Elements titleCategoryLinks = doc.select("div.title-categories a");
            for (Element link : titleCategoryLinks) {
                String category = normalize(link.text());
                if (!category.isBlank() && !category.toLowerCase().contains("wszystkie") && !categories.contains(category)) {
                    categories.add(category);
                }
            }
        }

        if (!categories.isEmpty()) {
            String result = String.join(", ", categories);
            log.debug("Znaleziono kategorie: {}", result);
            return result;
        }

        String fullText = doc.text();
        String[] knownCategories = {"Spektakle", "Dla dzieci", "Wydarzenia", "Koncerty", "Wystawy", "Spotkania"};
        for (String cat : knownCategories) {
            if (fullText.contains(cat)) {
                return cat;
            }
        }

        return null;
    }

    private String extractYouTubeLink(Document doc) {
        Elements iframes = doc.select("iframe[src*='youtube.com'], iframe[src*='youtu.be']");
        if (!iframes.isEmpty()) {
            String src = Objects.requireNonNull(iframes.first()).absUrl("src");
            if (!src.isBlank()) {
                log.debug("Znaleziono YouTube iframe: {}", src);
                return src;
            }
        }

        Elements youtubeLinks = doc.select("a[href*='youtube.com/watch'], a[href*='youtu.be/']");
        if (!youtubeLinks.isEmpty()) {
            String href = Objects.requireNonNull(youtubeLinks.first()).absUrl("href");
            if (!href.isBlank()) {
                log.debug("Znaleziono YouTube link: {}", href);
                return href;
            }
        }

        return null;
    }
}