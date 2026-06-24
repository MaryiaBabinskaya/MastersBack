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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TeatrNowyDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu Teatr Nowy: {}", url);
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
        dto.setContributors(extractContributors(doc));
        dto.setCast(extractCast(doc));
        dto.setDescription(extractDescription(doc));

        log.info("Sparsowano spektakl: {}", dto.getTitle());
        return dto;
    }

    private String extractTitle(Document doc) {
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            String title = normalize(h1.text());
            if (!title.isBlank()) {
                return title;
            }
        }

        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            String title = normalize(ogTitle.attr("content"));
            if (!title.isBlank()) {
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                return title;
            }
        }

        log.warn("Nie znaleziono tytułu dla spektaklu");
        return null;
    }

    private String extractImageUrl(Document doc) {
        Element clipPathImg = doc.selectFirst(".wrap-clippath img");
        if (clipPathImg != null) {
            String imageUrl = clipPathImg.attr("src");
            if (!imageUrl.isBlank()) {
                return imageUrl;
            }
        }

        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) {
            String url = ogImage.attr("content").trim();
            if (!url.isBlank() && !url.contains(".svg")) {
                return url;
            }
        }

        return null;
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> contributors = new ArrayList<>();
        Element descArea = doc.selectFirst(".area-description-post");
        if (descArea == null) {
            return null;
        }

        Elements paragraphs = descArea.select("p");
        for (Element p : paragraphs) {
            String html = p.html();
            if (html.contains("<strong>") && (html.contains("Reżyseria") || html.contains("Rezyseria") || html.contains("scenografia") || html.contains("kostiumy") ||
                    html.contains("kierownictwo") || html.contains("ruch sceniczny") || html.contains("asystent") || html.contains("charakteryzacja") ||
                    html.contains("światła") || html.contains("swiatla") || html.contains("dźwięku") || html.contains("dzwieku"))) {
                parseContributorsFromParagraph(html, contributors);
                break;
            }
        }

        return contributors.isEmpty() ? null : contributors;
    }

    private void parseContributorsFromParagraph(String html, List<ContributorDto> contributors) {
        String[] parts = html.split(";");
        for (String part : parts) {
            try {
                Pattern rolePattern = Pattern.compile("<strong>([^<]+)</strong>");
                Matcher roleMatcher = rolePattern.matcher(part);
                if (roleMatcher.find()) {
                    String role = normalize(roleMatcher.group(1));
                    String afterRole = part.substring(roleMatcher.end());
                    String name = extractNameAfterColon(afterRole);
                    if (!role.isBlank() && !name.isBlank()) {
                        ContributorDto contributor = new ContributorDto();
                        contributor.setRole(role);
                        contributor.setName(name);
                        contributors.add(contributor);
                        log.debug("Znaleziono twórcę: {} - {}", role, name);
                    }
                }
            } catch (Exception e) {
                log.debug("Błąd parsowania twórcy: {}", e.getMessage());
            }
        }
    }

    private String extractNameAfterColon(String text) {
        text = text.replaceAll("<[^>]+>", "");
        int colonIndex = text.indexOf(":");
        if (colonIndex >= 0) {
            text = text.substring(colonIndex + 1);
        }
        return normalize(text);
    }

    private List<CastMemberDto> extractCast(Document doc) {
        List<CastMemberDto> cast = new ArrayList<>();

        Element descArea = doc.selectFirst(".area-description-post");
        if (descArea == null) {
            return null;
        }

        Elements paragraphs = descArea.select("p");
        boolean foundObsadaHeader = false;

        for (Element p : paragraphs) {
            String text = p.text();
            if (text.contains("Obsada:")) {
                foundObsadaHeader = true;
                continue;
            }

            if (foundObsadaHeader) {
                parseCastFromParagraph(text, cast);
                break;
            }
        }

        return cast.isEmpty() ? null : cast;
    }

    private void parseCastFromParagraph(String text, List<CastMemberDto> cast) {
        String[] parts = text.split(";");
        for (String part : parts) {
            try {
                part = normalize(part);
                int colonIndex = part.indexOf(":");
                if (colonIndex > 0) {
                    String role = part.substring(0, colonIndex).trim();
                    String name = part.substring(colonIndex + 1).trim();
                    if (!role.isBlank() && !name.isBlank()) {
                        CastMemberDto member = new CastMemberDto();
                        member.setName(name);
                        member.setRole(role);
                        cast.add(member);
                        log.debug("Znaleziono aktora: {} jako {}", name, role);
                    }
                }
            } catch (Exception e) {
                log.debug("Błąd parsowania obsady: {}", e.getMessage());
            }
        }
    }

    private String extractDescription(Document doc) {
        StringBuilder description = new StringBuilder();

        Element descArea = doc.selectFirst(".area-description-post");
        if (descArea == null) {
            return null;
        }

        Elements paragraphs = descArea.select("p");
        boolean skipNext = false;

        for (Element p : paragraphs) {
            String text = normalize(p.text());

            if (text.contains("Reżyseria") || text.contains("Rezyseria") || text.contains("scenografia") || text.contains("kierownictwo muzyczne")) {
                continue;
            }

            if (text.contains("Obsada:")) {
                skipNext = true;
                continue;
            }

            if (skipNext) {
                skipNext = false;
                continue;
            }

            if (text.length() < 20) {
                continue;
            }

            if (text.toLowerCase().contains("licencja") || text.toLowerCase().contains("zaiks")) {
                continue;
            }

            if (text.toLowerCase().startsWith("czas trwania:")) {
                continue;
            }

            if (!description.isEmpty()) {
                description.append("\n\n");
            }
            description.append(text);
        }

        String result = description.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}