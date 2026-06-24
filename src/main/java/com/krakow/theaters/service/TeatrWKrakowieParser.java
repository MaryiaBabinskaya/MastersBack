package com.krakow.theaters.service;

import com.krakow.theaters.dto.TeatrWKrakowieDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeatrWKrakowieParser {

    private static final String BASE_URL = "https://teatrwkrakowie.pl";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private final TeatrWKrakowieAjaxClient ajaxClient;

    public List<TeatrWKrakowieDto.PlayDto> parseAll() throws IOException {
        List<TeatrWKrakowieDto.PlayDto> all = new ArrayList<>();
        YearMonth from = YearMonth.now();
        YearMonth to = from.plusMonths(3);

        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            String startDate = ym.atDay(1).toString();
            Document doc = ajaxClient.fetchMonth(startDate);
            List<TeatrWKrakowieDto.PlayDto> monthItems = parseMonthDocument(doc, ym);
            log.info("Teatr w Krakowie: {} -> {} rekordów", ym, monthItems.size());
            all.addAll(monthItems);
        }

        return mergePlayDtos(all);
    }

    public List<TeatrWKrakowieDto.PlayDto> parseMonths(YearMonth from, YearMonth to) throws IOException {
        List<TeatrWKrakowieDto.PlayDto> all = new ArrayList<>();

        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            String startDate = ym.atDay(1).toString();
            Document doc = ajaxClient.fetchMonth(startDate);
            List<TeatrWKrakowieDto.PlayDto> monthItems = parseMonthDocument(doc, ym);
            log.info("Teatr w Krakowie (zakres): {} -> {} rekordów", ym, monthItems.size());
            all.addAll(monthItems);
        }

        return mergePlayDtos(all);
    }

    private List<TeatrWKrakowieDto.PlayDto> parseMonthDocument(Document doc, YearMonth fallbackYm) {
        List<TeatrWKrakowieDto.PlayDto> result = new ArrayList<>();
        Elements dayWraps = doc.select(".day-wrap");

        for (Element dayWrap : dayWraps) {
            LocalDate date = extractDateFromDayWrap(dayWrap, fallbackYm);
            Elements blocks = dayWrap.select(".spektakle-list > .block");

            for (Element block : blocks) {
                TeatrWKrakowieDto.PlayDto dto = parseBlock(block, date);
                if (dto == null) {
                    continue;
                }
                enrichPlayFromDetailPage(dto);
                result.add(dto);
            }
        }
        return result;
    }

    private TeatrWKrakowieDto.PlayDto parseBlock(Element block, LocalDate date) {
        Element titleLink = block.selectFirst("h2 a[href*=/spektakl/]");
        if (titleLink == null) {
            return null;
        }

        String title = clean(titleLink.text());
        String detailUrl = titleLink.absUrl("href");

        Element eventTypeEl = block.selectFirst(".event-type");
        String eventType = cleanOwnText(eventTypeEl);
        String eventInfo = extractEventInfo(eventTypeEl);

        boolean spectacle = "Spektakl".equalsIgnoreCase(eventType);
        boolean repertoireEvent = "Repertuar".equalsIgnoreCase(eventType);

        String descText = cleanText(block.selectFirst(".desc"));
        String scene = extractSceneFromDesc(block.selectFirst(".desc"));
        String stageDirector = extractDirectorFromDesc(block.selectFirst(".desc"));

        String imageLink = extractImageFromBlock(block);
        String ticketUrl = extractTicketUrl(block);

        List<TeatrWKrakowieDto.PerformanceDto> performances = extractPerformances(block, date, scene);

        TeatrWKrakowieDto.PlayDto dto = new TeatrWKrakowieDto.PlayDto();
        dto.setName(title);
        dto.setType(eventType);
        dto.setCategory(eventType);
        dto.setEventInfo(eventInfo);
        dto.setSpectacle(spectacle);
        dto.setRepertoireEvent(repertoireEvent);
        dto.setDescription(descText);
        dto.setScene(scene);
        dto.setStageDirector(stageDirector);
        dto.setImageLink(imageLink);
        dto.setDetailUrl(detailUrl);
        dto.setTicketLink(ticketUrl);
        dto.setPerformances(performances);

        return dto;
    }

    private String extractEventInfo(Element eventTypeEl) {
        if (eventTypeEl == null) {
            return null;
        }

        Element redInfo = eventTypeEl.selectFirst(".red-info");
        if (redInfo != null) {
            return clean(redInfo.text());
        }

        String fullText = clean(eventTypeEl.text());
        String ownText = clean(eventTypeEl.ownText());

        if (isBlank(fullText) || isBlank(ownText)) {
            return null;
        }

        String info = fullText.replace(ownText, "").trim();
        return isBlank(info) ? null : info;
    }

    private List<TeatrWKrakowieDto.PerformanceDto> extractPerformances(Element block, LocalDate date, String scene) {
        List<TeatrWKrakowieDto.PerformanceDto> performances = new ArrayList<>();
        Elements timeEls = block.select(".column-time .time");

        for (Element timeEl : timeEls) {
            String time = clean(timeEl.text());
            if (isBlank(time)) {
                continue;
            }

            TeatrWKrakowieDto.PerformanceDto performance = new TeatrWKrakowieDto.PerformanceDto();
            performance.setDate(date != null ? date.toString() : null);
            performance.setTime(time);
            performance.setDateTime(buildDateTime(date, time));
            performance.setScene(scene);

            String instanceId = clean(timeEl.attr("data-instance"));
            performance.setInstanceId(instanceId);

            performances.add(performance);
        }

        if (performances.isEmpty()) {
            TeatrWKrakowieDto.PerformanceDto performance = new TeatrWKrakowieDto.PerformanceDto();
            performance.setDate(date != null ? date.toString() : null);
            performance.setTime(null);
            performance.setDateTime(null);
            performance.setScene(scene);
            performances.add(performance);
        }

        return performances;
    }

    private LocalDate extractDateFromDayWrap(Element dayWrap, YearMonth fallbackYm) {
        String dayNumber = cleanText(dayWrap.selectFirst(".day .day-number"));
        Integer day = parseIntSafe(dayNumber);

        if (day == null) {
            return null;
        }

        YearMonth ym = extractYearMonthFromDayWrap(dayWrap);
        if (ym == null) {
            ym = fallbackYm;
        }

        try {
            return ym.atDay(day);
        } catch (Exception e) {
            return null;
        }
    }

    private YearMonth extractYearMonthFromDayWrap(Element dayWrap) {
        String monthYearText = cleanText(dayWrap.selectFirst(".day .month-year"));
        if (isBlank(monthYearText)) {
            return null;
        }

        monthYearText = monthYearText.toLowerCase(Locale.ROOT);
        Integer year = extractYear(monthYearText);
        Integer month = extractPolishMonth(monthYearText);

        if (year == null || month == null) {
            return null;
        }

        return YearMonth.of(year, month);
    }

    private Integer extractYear(String text) {
        Matcher m = Pattern.compile("(20\\d{2})").matcher(text);
        return m.find() ? parseIntSafe(m.group(1)) : null;
    }

    private Integer extractPolishMonth(String text) {
        Map<String, Integer> months = new LinkedHashMap<>();
        months.put("styczeń", 1);
        months.put("stycznia", 1);
        months.put("luty", 2);
        months.put("lutego", 2);
        months.put("marzec", 3);
        months.put("marca", 3);
        months.put("kwiecień", 4);
        months.put("kwietnia", 4);
        months.put("maj", 5);
        months.put("maja", 5);
        months.put("czerwiec", 6);
        months.put("czerwca", 6);
        months.put("lipiec", 7);
        months.put("lipca", 7);
        months.put("sierpień", 8);
        months.put("sierpnia", 8);
        months.put("wrzesień", 9);
        months.put("września", 9);
        months.put("październik", 10);
        months.put("października", 10);
        months.put("listopad", 11);
        months.put("listopada", 11);
        months.put("grudzień", 12);
        months.put("grudnia", 12);

        for (Map.Entry<String, Integer> e : months.entrySet()) {
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private void enrichPlayFromDetailPage(TeatrWKrakowieDto.PlayDto dto) {
        if (isBlank(dto.getDetailUrl())) {
            return;
        }

        try {
            Document detailDoc = Jsoup.connect(dto.getDetailUrl())
                    .userAgent(USER_AGENT)
                    .timeout(30000)
                    .get();

            Element h1 = detailDoc.selectFirst("h1");
            if (h1 != null && !isBlank(h1.text())) {
                dto.setName(clean(h1.text()));
            }

            if (isBlank(dto.getDescription())) {
                dto.setDescription(extractBestDescription(detailDoc));
            }
            if (isBlank(dto.getScene())) {
                dto.setScene(extractSceneFromDetail(detailDoc));
            }
            if (isBlank(dto.getStageDirector())) {
                dto.setStageDirector(extractDirectorFromDetail(detailDoc));
            }
            if (isBlank(dto.getImageLink())) {
                dto.setImageLink(extractImage(detailDoc));
            }
            if (isBlank(dto.getTrailerLink())) {
                dto.setTrailerLink(extractTrailer(detailDoc));
            }
            if (dto.getActors() == null || dto.getActors().isEmpty()) {
                dto.setActors(extractActors(detailDoc));
            }
        } catch (Exception e) {
            log.warn("Nie udało się pobrać szczegółów {}: {}", dto.getDetailUrl(), e.getMessage());
        }
    }

    private String extractSceneFromDesc(Element descEl) {
        if (descEl == null) {
            return null;
        }

        for (Element p : descEl.select("p")) {
            String txt = clean(p.text());
            if (txt.contains("Scena")) {
                return txt;
            }
            if (txt.contains("Duża Scena")) {
                return txt;
            }
            if (txt.contains("Teatr w Krakowie")) {
                return txt;
            }
        }

        String text = clean(descEl.text());
        return extractSceneGeneric(text);
    }

    private String extractDirectorFromDesc(Element descEl) {
        if (descEl == null) return null;

        for (Element p : descEl.select("p")) {
            String txt = clean(p.text());

            if (txt.startsWith("Reżyseria:")) {
                return clean(txt.replaceFirst("^Reżyseria:\\s*", ""));
            }
            if (txt.startsWith("Tłumaczenie, adaptacja, reżyseria:")) {
                return clean(txt.replaceFirst("^Tłumaczenie,\\s*adaptacja,\\s*reżyseria:\\s*", ""));
            }
            if (txt.startsWith("Scenariusz, reżyseria:")) {
                return clean(txt.replaceFirst("^Scenariusz,\\s*reżyseria:\\s*", ""));
            }
        }
        return null;
    }

    private String extractSceneFromDetail(Document doc) {
        String text = clean(doc.text());
        return extractSceneGeneric(text);
    }

    private String extractSceneGeneric(String text) {
        if (isBlank(text)) {
            return null;
        }

        Matcher m = Pattern.compile("(Duża Scena|Scena MOS|Scena Miniatura|Dom Machin|Scena w Domu Machin)").matcher(text);
        if (m.find()) {
            return clean(m.group(1));
        }

        m = Pattern.compile("(Teatr w Krakowie[^\\n]*)").matcher(text);
        if (m.find()) {
            return clean(m.group(1));
        }

        return null;
    }

    private String extractDirectorFromDetail(Document doc) {
        String text = clean(doc.text());
        if (isBlank(text)) {
            return null;
        }

        String[] patterns = {
                "Reżyseria:\\s*(.+?)(?:Scenografia:|Muzyka:|Kostiumy:|Choreografia:|Obsada:|Premiera:|Czas trwania:|$)",
                "Tłumaczenie, adaptacja, reżyseria:\\s*(.+?)(?:Scenografia:|Muzyka:|Kostiumy:|Choreografia:|Obsada:|Premiera:|Czas trwania:|$)",
                "Scenariusz, reżyseria:\\s*(.+?)(?:Scenografia:|Muzyka:|Kostiumy:|Choreografia:|Obsada:|Premiera:|Czas trwania:|$)"
        };

        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            if (m.find()) {
                return clean(m.group(1));
            }
        }
        return null;
    }

    private List<TeatrWKrakowieDto.ActorDto> extractActors(Document doc) {
        List<TeatrWKrakowieDto.ActorDto> actors = new ArrayList<>();
        String text = clean(doc.text());

        Matcher blockMatcher = Pattern.compile(
                "Obsada:\\s*(.+?)(?:Trailer wideo|Zdjęcia ze spektaklu|Najbliższe terminy|Bilety|Zapisz się|Czas trwania:|$)"
        ).matcher(text);

        if (!blockMatcher.find()) {
            return actors;
        }

        String castBlock = clean(blockMatcher.group(1));

        Matcher nameMatcher = Pattern.compile("([A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+(?:\\s+[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+)+)").matcher(castBlock);
        Map<String, TeatrWKrakowieDto.ActorDto> unique = new LinkedHashMap<>();

        while (nameMatcher.find()) {
            String name = clean(nameMatcher.group(1));
            if (isBlank(name)) continue;
            unique.putIfAbsent(name.toLowerCase(Locale.ROOT), buildActor(name));
        }

        actors.addAll(unique.values());
        return actors;
    }

    private TeatrWKrakowieDto.ActorDto buildActor(String name) {
        TeatrWKrakowieDto.ActorDto actor = new TeatrWKrakowieDto.ActorDto();
        actor.setName(name);
        actor.setRole(null);
        return actor;
    }

    private String extractImageFromBlock(Element block) {
        Element img = block.selectFirst(".img-container");
        if (img == null) {
            return null;
        }

        String style = img.attr("style");
        Matcher m = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)").matcher(style);
        if (!m.find()) {
            return null;
        }

        String url = m.group(1);
        if (url.startsWith("http")) {
            return url;
        }
        return BASE_URL + url;
    }

    private String extractTicketUrl(Element block) {
        Element ticketLink = block.selectFirst(".tickets a[href]");
        if (ticketLink == null) return null;

        String href = ticketLink.absUrl("href");
        return isBlank(href) ? null : href;
    }

    private String extractBestDescription(Document doc) {
        String best = null;
        for (Element p : doc.select("p")) {
            String txt = clean(p.text());
            if (txt.length() > 120) {
                if (best == null || txt.length() > best.length()) {
                    best = txt;
                }
            }
        }
        return best;
    }

    private String extractImage(Document doc) {
        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null && !isBlank(og.attr("content"))) {
            return og.attr("content");
        }

        Element img = doc.selectFirst("img[src]");
        return img != null ? img.absUrl("src") : null;
    }

    private String extractTrailer(Document doc) {
        Element iframe = doc.selectFirst("iframe[src*='youtube'], iframe[src*='youtu.be'], iframe[src*='vimeo']");
        if (iframe != null) {
            return iframe.absUrl("src");
        }

        Element link = doc.selectFirst("a[href*='youtube'], a[href*='youtu.be'], a[href*='vimeo']");
        return link != null ? link.absUrl("href") : null;
    }

    private List<TeatrWKrakowieDto.PlayDto> mergePlayDtos(List<TeatrWKrakowieDto.PlayDto> input) {
        Map<String, TeatrWKrakowieDto.PlayDto> merged = new LinkedHashMap<>();

        for (TeatrWKrakowieDto.PlayDto item : input) {
            String key = isBlank(item.getDetailUrl())
                    ? item.getDetailUrl() : normalize(item.getName());

            TeatrWKrakowieDto.PlayDto existing = merged.get(key);

            if (existing == null) {
                TeatrWKrakowieDto.PlayDto copy = getPlayDto(item);

                if (item.getPerformances() != null) {
                    copy.getPerformances().addAll(item.getPerformances());
                }

                merged.put(key, copy);
            } else {
                if (isBlank(existing.getDescription())) existing.setDescription(item.getDescription());
                if (isBlank(existing.getScene())) existing.setScene(item.getScene());
                if (isBlank(existing.getStageDirector())) existing.setStageDirector(item.getStageDirector());
                if (isBlank(existing.getTrailerLink())) existing.setTrailerLink(item.getTrailerLink());
                if (isBlank(existing.getImageLink())) existing.setImageLink(item.getImageLink());
                if (isBlank(existing.getTicketLink())) existing.setTicketLink(item.getTicketLink());

                if ((existing.getActors() == null || existing.getActors().isEmpty()) && item.getActors() != null) {
                    existing.setActors(item.getActors());
                }

                if (item.getPerformances() != null) {
                    for (TeatrWKrakowieDto.PerformanceDto perf : item.getPerformances()) {
                        if (!containsPerformance(existing.getPerformances(), perf)) {
                            existing.getPerformances().add(perf);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static TeatrWKrakowieDto.PlayDto getPlayDto(TeatrWKrakowieDto.PlayDto item) {
        TeatrWKrakowieDto.PlayDto copy = new TeatrWKrakowieDto.PlayDto();
        copy.setName(item.getName());
        copy.setType(item.getType());
        copy.setCategory(item.getCategory());
        copy.setEventInfo(item.getEventInfo());
        copy.setSpectacle(item.isSpectacle());
        copy.setRepertoireEvent(item.isRepertoireEvent());
        copy.setDescription(item.getDescription());
        copy.setActors(item.getActors());
        copy.setScene(item.getScene());
        copy.setStageDirector(item.getStageDirector());
        copy.setTrailerLink(item.getTrailerLink());
        copy.setImageLink(item.getImageLink());
        copy.setDetailUrl(item.getDetailUrl());
        copy.setTicketLink(item.getTicketLink());
        copy.setPerformances(new ArrayList<>());
        return copy;
    }

    private boolean containsPerformance(List<TeatrWKrakowieDto.PerformanceDto> existing,
                                        TeatrWKrakowieDto.PerformanceDto candidate) {
        for (TeatrWKrakowieDto.PerformanceDto perf : existing) {
            if (Objects.equals(perf.getDate(), candidate.getDate())
                    && Objects.equals(perf.getTime(), candidate.getTime())
                    && Objects.equals(perf.getScene(), candidate.getScene())) {
                return true;
            }
        }
        return false;
    }

    private String buildDateTime(LocalDate date, String time) {
        if (date == null || isBlank(time)) {
            return null;
        }
        try {
            return LocalDateTime.of(date, LocalTime.parse(time)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanOwnText(Element el) {
        return el == null ? null : clean(el.ownText());
    }

    private String cleanText(Element el) {
        return el == null ? null : clean(el.text());
    }

    private String clean(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String normalize(String s) {
        return clean(s).toLowerCase(Locale.ROOT);
    }
}