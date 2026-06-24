package com.krakow.theaters.service;

import com.krakow.theaters.dto.LudowyEvent;
import com.krakow.theaters.dto.LudowyRepertuar;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LudowyParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;

    public LudowyRepertuar parseRepertuar(String url) throws IOException {
        log.info("Parsowanie repertuaru Ludowy z URL: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        LudowyRepertuar repertuar = new LudowyRepertuar();
        repertuar.setMiesiac(extractMonthFromUrl(url));

        List<LudowyEvent> wydarzenia = new ArrayList<>();
        Elements dayItems = doc.select(".repertoire-list-list-item");

        for (Element dayItem : dayItems) {
            wydarzenia.addAll(parseDayItem(dayItem));
        }

        repertuar.setWydarzenia(wydarzenia);
        log.info("Sparsowano {} wydarzeń dla miesiąca {}", wydarzenia.size(), repertuar.getMiesiac());

        return repertuar;
    }

    private List<LudowyEvent> parseDayItem(Element dayItem) {
        List<LudowyEvent> dayEvents = new ArrayList<>();

        Element dayNr = dayItem.selectFirst(".repertoire-list-list-item-day-content-nr");
        Element dayName = dayItem.selectFirst(".repertoire-list-list-item-day-content-day");

        if (dayNr == null || dayName == null) return dayEvents;

        String dateString = normalize(dayNr.text()) + " " + normalize(dayName.text());
        log.debug("Przetwarzanie dnia: {}", dateString);

        for (Element spectacleItem : dayItem.select(".repertoire-list-list-item-spectacles-item")) {
            parseSpectacleItem(spectacleItem, dateString).ifPresent(dayEvents::add);
        }

        return dayEvents;
    }

    private Optional<LudowyEvent> parseSpectacleItem(Element item, String dateString) {
        LudowyEvent event = new LudowyEvent();
        event.setData(dateString);
        event.setGodzina(extractText(item, ".repertoire-list-list-item-spectacles-item-hour"));
        event.setScena(extractText(item, ".repertoire-list-list-item-spectacles-item-tags span"));

        Element titleEl = item.selectFirst(".repertoire-list-list-item-spectacles-item-content-link-title");
        if (titleEl != null) {
            String title = normalize(titleEl.text()).replaceAll("Czytaj wi.cej o spektaklu.*", "").trim();
            event.setTytul(title);
        }

        Element linkEl = item.selectFirst(".repertoire-list-list-item-spectacles-item-content-link");
        if (linkEl != null) {
            event.setLinkSpektaklu(linkEl.absUrl("href"));
            Element imgEl = linkEl.selectFirst("img");
            if (imgEl != null) event.setPlakat(imgEl.absUrl("src"));
        }

        event.setLinkBilety(Optional.ofNullable(item.selectFirst("a[href*='bilety.ludowy.pl']"))
                .map(e -> e.absUrl("href")).orElse(null));

        if (event.getTytul() == null || event.getTytul().isEmpty()) {
            return Optional.empty();
        }

        log.debug("Dodano wydarzenie: {} - {} {}", event.getTytul(), event.getData(), event.getGodzina());
        return Optional.of(event);
    }

    private String extractMonthFromUrl(String url) {
        try {
            String query = url.split("\\?")[1];
            String month = "";
            String year = "";

            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if ("rep_month".equals(pair[0])) month = pair[1];
                if ("rep_year".equals(pair[0])) year = pair[1];
            }

            if (month.length() == 1) month = "0" + month;
            return month + "-" + year;
        } catch (Exception e) {
            log.error("Błąd podczas ekstraktowania miesiąca z URL: {}", url);
            return null;
        }
    }

    private String extractText(Element parent, String selector) {
        Element el = parent.selectFirst(selector);
        return el != null ? normalize(el.text()) : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}