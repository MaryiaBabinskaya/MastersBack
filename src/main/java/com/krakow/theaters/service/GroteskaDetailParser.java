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
import java.util.List;

@Slf4j
@Service
public class GroteskaDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final String BASE_URL = "https://www.groteska.pl";

    private enum Section {
        CREATORS, CAST, ADDITIONAL_INFO
    }

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu Groteska z URL: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        PlayDetailsDto details = new PlayDetailsDto();

        details.setTitle(extractText(doc, "h2.m-heading.m-heading--underline"));
        details.setImageUrl(extractPosterUrl(doc));
        details.setDescription(extractDescription(doc));

        String subtitle = extractText(doc, "h3.m-title.mb-5.font-weight-bold");
        parseContentBlock(doc, details, subtitle);

        log.info("Sparsowano szczegóły spektaklu: {}", details.getTitle());
        return details;
    }

    private String extractText(Document doc, String cssQuery) {
        Element element = doc.selectFirst(cssQuery);
        return element != null ? normalize(element.text()) : "";
    }

    private String extractPosterUrl(Document doc) {
        Element posterElement = doc.selectFirst("div.m-aside.mb-5 img");
        if (posterElement != null) {
            String imgSrc = posterElement.attr("src");
            if (!imgSrc.isEmpty() && !imgSrc.startsWith("http")) {
                return BASE_URL + imgSrc;
            }
            return imgSrc;
        }
        return null;
    }

    private String extractDescription(Document doc) {
        Element descriptionBlock = doc.selectFirst("div#blocks div.content-block-translatable_ckeditor");
        if (descriptionBlock != null) {
            return normalize(Jsoup.parse(descriptionBlock.html()).text());
        }
        return null;
    }

    private void parseContentBlock(Document doc, PlayDetailsDto details, String subtitle) {
        Element contentBlock = doc.selectFirst("div.content-block");
        if (contentBlock == null) return;

        List<String> creatorsLines = new ArrayList<>();
        List<String> castLines = new ArrayList<>();
        List<String> additionalInfoLines = new ArrayList<>();

        Section currentSection = Section.CREATORS;

        for (Element p : contentBlock.select("p")) {
            String text = normalize(p.text());
            if (text.isEmpty()) continue;

            if (text.equalsIgnoreCase("Występują:") || text.equalsIgnoreCase("Wystepuja:")) {
                currentSection = Section.CAST;
                continue;
            }

            if (isAdditionalInfoMarker(text)) {
                currentSection = Section.ADDITIONAL_INFO;
            }

            switch (currentSection) {
                case CREATORS -> creatorsLines.add(text);
                case CAST -> castLines.add(text);
                case ADDITIONAL_INFO -> additionalInfoLines.add(text);
            }
        }

        details.setContributors(buildContributors(creatorsLines, subtitle));
        details.setCast(buildCast(castLines));

        String additionalInfoStr = String.join("\n", additionalInfoLines).trim();
        details.setAdditionalInfo(additionalInfoStr.isEmpty() ? null : additionalInfoStr);

        extractMetadataFromAdditionalInfo(additionalInfoLines, details);
    }

    private boolean isAdditionalInfoMarker(String text) {
        String lowerText = text.toLowerCase();
        return lowerText.contains("premiera:") ||
                lowerText.contains("czas trwania:") ||
                lowerText.contains("spektakl dla") ||
                lowerText.contains("scena dla");
    }

    private List<ContributorDto> buildContributors(List<String> lines, String subtitle) {
        List<ContributorDto> contributors = new ArrayList<>();

        if (subtitle != null && !subtitle.isEmpty()) {
            contributors.add(createContributor("Autor", subtitle));
        }

        for (String line : lines) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                contributors.add(createContributor(normalize(parts[0]), normalize(parts[1])));
            } else {
                contributors.add(createContributor("", normalize(line)));
            }
        }
        return contributors;
    }

    private List<CastMemberDto> buildCast(List<String> lines) {
        List<CastMemberDto> cast = new ArrayList<>();

        for (String line : lines) {
            CastMemberDto member = new CastMemberDto();
            if (line.contains(" jako ")) {
                String[] parts = line.split(" jako ", 2);
                member.setName(normalize(parts[0]));
                member.setRole(normalize(parts[1]));
            } else if (line.contains(" - ")) {
                String[] parts = line.split(" - ", 2);
                member.setName(normalize(parts[0]));
                member.setRole(normalize(parts[1]));
            } else {
                member.setName(normalize(line));
                member.setRole("");
            }
            cast.add(member);
        }
        return cast;
    }

    private void extractMetadataFromAdditionalInfo(List<String> lines, PlayDetailsDto details) {
        for (String line : lines) {
            String lowerLine = line.toLowerCase();

            if (lowerLine.contains("scena") && details.getScene() == null) {
                details.setScene(normalize(line));
            } else if (lowerLine.contains("czas trwania:")) {
                details.setDurationMinutesText(extractAfterKeyword(line, "czas trwania:"));
            } else if (lowerLine.contains("premiera:")) {
                details.setPremiereDate(extractAfterKeyword(line, "premiera:"));
            }
        }
    }

    private String extractAfterKeyword(String text, String keyword) {
        int index = text.toLowerCase().indexOf(keyword);
        if (index >= 0) {
            return normalize(text.substring(index + keyword.length()));
        }
        return "";
    }

    private ContributorDto createContributor(String role, String name) {
        ContributorDto dto = new ContributorDto();
        dto.setRole(role);
        dto.setName(name);
        return dto;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}