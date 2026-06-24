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
public class LudowyDetailParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile("videoSrcOriginal:\\s*\"([^\"]+)\"");

    public PlayDetailsDto parse(String url) throws IOException {
        log.info("Parsowanie szczegółów spektaklu Ludowy z URL: {}", url);
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        PlayDetailsDto details = new PlayDetailsDto();
        details.setTitle(extractText(doc, "h1.article-header-title"));
        details.setDescription(extractText(doc, ".content-text"));
        details.setContributors(extractContributors(doc));
        details.setCast(extractCast(doc));
        details.setImageUrl(extractImageUrl(doc));
        details.setGalleryImages(extractGalleryImages(doc));
        details.setYoutubeUrl(extractYoutubeUrl(doc));

        log.info("Sparsowano szczegóły spektaklu: {}", details.getTitle());
        log.info(" - Twórców: {}", details.getContributors().size());
        log.info(" - Obsada: {}", details.getCast().size());
        log.info(" - Zdjęć w galerii: {}", details.getGalleryImages().size());

        return details;
    }

    private List<ContributorDto> extractContributors(Document doc) {
        List<ContributorDto> contributors = new ArrayList<>();
        Elements contributorSections = doc.select(".article-content");

        for (Element section : contributorSections) {
            Element heading = section.selectFirst("h2");
            if (heading != null && heading.text().contains("Twórcy")) {
                Element dl = section.selectFirst("dl");
                if (dl != null) {
                    Elements dts = dl.select("dt");
                    Elements dds = dl.select("dd");

                    int count = Math.min(dts.size(), dds.size());
                    for (int i = 0; i < count; i++) {
                        ContributorDto contributor = new ContributorDto();
                        contributor.setRole(normalize(dts.get(i).text()));
                        contributor.setName(normalize(dds.get(i).text()));
                        contributors.add(contributor);
                        log.debug("Dodano twórcę: {} - {}", contributor.getRole(), contributor.getName());
                    }
                }
            }
        }
        return contributors;
    }

    private List<CastMemberDto> extractCast(Document doc) {
        Element castSection = doc.selectFirst(".cast");
        if (castSection == null) {
            return List.of();
        }

        return castSection.select(".cast-list-item").stream()
                .map(this::buildCastMember)
                .filter(member -> member.getName() != null && !member.getName().isEmpty())
                .peek(member -> log.debug("Dodano aktora: {} jako {}", member.getName(), member.getRole()))
                .toList();
    }

    private CastMemberDto buildCastMember(Element item) {
        CastMemberDto member = new CastMemberDto();
        member.setName(extractText(item, ".list-item-content-title"));
        member.setRole(extractText(item, ".cast-list-item-content-text"));

        String profileUrl = item.attr("href");
        if (!profileUrl.isEmpty()) {
            member.setProfileUrl(profileUrl);
        }

        Element imgElement = item.selectFirst(".cast-list-item-media-img");
        if (imgElement != null) {
            member.setImageUrl(imgElement.absUrl("src"));
        }
        return member;
    }

    private List<String> extractGalleryImages(Document doc) {
        return doc.select(".gallery-slide img").stream()
                .map(img -> img.absUrl("src"))
                .filter(url -> !url.isEmpty())
                .toList();
    }

    private String extractYoutubeUrl(Document doc) {
        Element videoScript = doc.selectFirst("script:containsData(videoSrcOriginal)");
        if (videoScript != null) {
            Matcher matcher = YOUTUBE_URL_PATTERN.matcher(videoScript.data());
            if (matcher.find()) {
                String embedUrl = matcher.group(1);
                String watchUrl = embedUrl.replace("/embed/", "/watch?v=").replace("?autoplay=1", "");
                log.debug("Znaleziono trailer YouTube: {}", watchUrl);
                return watchUrl;
            }
        }
        return null;
    }

    private String extractText(Element container, String cssSelector) {
        Element element = container.selectFirst(cssSelector);
        return element != null ? normalize(element.text()) : null;
    }

    private String extractImageUrl(Document doc) {
        Element element = doc.selectFirst(".page-header-image-media");
        return element != null ? element.absUrl("src") : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}