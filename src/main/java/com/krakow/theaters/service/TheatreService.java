package com.krakow.theaters.service;

import com.krakow.theaters.dto.BagatelaRepertuar;
import com.krakow.theaters.dto.BagatelaSpektakl;
import com.krakow.theaters.dto.BarakahRepertuar;
import com.krakow.theaters.dto.BarakahSpektakl;
import com.krakow.theaters.dto.CastMemberDto;
import com.krakow.theaters.dto.ContributorDto;
import com.krakow.theaters.dto.GroupedPlayDto;
import com.krakow.theaters.dto.GroteskaEvent;
import com.krakow.theaters.dto.GroteskaRepertuar;
import com.krakow.theaters.dto.KtoEvent;
import com.krakow.theaters.dto.KtoRepertuar;
import com.krakow.theaters.dto.LudowyEvent;
import com.krakow.theaters.dto.LudowyRepertuar;
import com.krakow.theaters.dto.PlayDetailsDto;
import com.krakow.theaters.dto.TeatrWKrakowieDto;
import com.krakow.theaters.dto.UpcomingTermDto;
import com.krakow.theaters.dto.VarieteRepertuar;
import com.krakow.theaters.dto.VarieteSpektakl;
import com.krakow.theaters.model.Play;
import com.krakow.theaters.model.Theatre;
import com.krakow.theaters.repository.PlayRepository;
import com.krakow.theaters.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TheatreService {

    private final TheatreRepository theatreRepository;
    private final PlayRepository playRepository;
    private final TeatrWKrakowieParser teatrWKrakowieParser;
    private final TeatrWKrakowieDetailParser teatrWKrakowieDetailParser;
    private final BagatelaParser bagatelaParser;
    private final BagatelaDetailParser bagatelaDetailParser;
    private final StaryParser staryParser;
    private final StaryDetailParser staryDetailParser;
    private final ASTParser astParser;
    private final ASTDetailParser astDetailParser;
    private final LazniaNowaParser lazniaNowaParser;
    private final LazniaNowaDetailParser lazniaNowaDetailParser;
    private final TeatrNowyParser teatrNowyParser;
    private final TeatrNowyDetailParser teatrNowyDetailParser;
    private final ScenaStuParser scenaStuParser;
    private final ScenaStuDetailParser scenaStuDetailParser;
    private final OperaKrakowskaParser operaKrakowskaParser;
    private final OperaKrakowskaDetailParser operaKrakowskaDetailParser;
    private final BarakahParser barakahParser;
    private final BarakahDetailParser barakahDetailParser;
    private final KtoParser ktoParser;
    private final LudowyParser ludowyParser;
    private final LudowyDetailParser ludowyDetailParser;
    private final GroteskaParser groteskaParser;
    private final GroteskaDetailParser groteskaDetailParser;
    private final VarieteParser varieteParser;
    private final VarieteDetailParser varieteDetailParser;

    public List<Play> getAllPlays() {
        return playRepository.findAll();
    }

    public Map<String, Long> countPlaysByTheatre() {
        return playRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getTheatre() != null ? p.getTheatre().getId() + " (" + p.getTheatre().getName() + ")" : "NO_THEATRE",
                        Collectors.counting()
                ));
    }

    public List<Theatre> getAllTheatres() {
        return theatreRepository.findAll();
    }

    public List<GroupedPlayDto> getGroupedPlays() {
        List<Play> allPlays = playRepository.findAll();

        // Group by title and theatre (same play in same theatre should be grouped together)
        Map<String, List<Play>> grouped = allPlays.stream()
                .collect(Collectors.groupingBy(play ->
                        play.getTitle() + "|" + (play.getTheatre() != null ? play.getTheatre().getId() : "")
                ));

        List<GroupedPlayDto> result = new ArrayList<>();

        for (Map.Entry<String, List<Play>> entry : grouped.entrySet()) {
            List<Play> plays = entry.getValue();
            if (plays.isEmpty()) continue;

            GroupedPlayDto dto = getGroupedPlayDto(plays);

            // Collect unique showtimes (deduplicate by showtime value)
            Map<String, GroupedPlayDto.ShowtimeDto> uniqueShowtimes = new LinkedHashMap<>();
            for (Play play : plays) {
                String key = play.getShowtime() != null ? play.getShowtime() : "";

                if (!uniqueShowtimes.containsKey(key)) {
                    GroupedPlayDto.ShowtimeDto showtime = new GroupedPlayDto.ShowtimeDto();
                    showtime.setId(play.getId());
                    showtime.setShowtime(play.getShowtime());
                    // Use the existing getter on Play and Lombok-generated setter on ShowtimeDto
                    showtime.setShowtimeAsDateTime(play.getShowtimesDateTime());
                    showtime.setTicketUrl(play.getTicketUrl());
                    uniqueShowtimes.put(key, showtime);
                }
            }

            dto.getShowtimes().addAll(uniqueShowtimes.values());

            // Sort showtimes by date
            dto.getShowtimes().sort(Comparator.comparing(
                    s -> s.getShowtimeAsDateTime() != null ? s.getShowtimeAsDateTime() : java.time.LocalDateTime.MIN
            ));

            result.add(dto);
        }

        // Sort by title
        result.sort(Comparator.comparing(GroupedPlayDto::getTitle));
        return result;
    }

    private static GroupedPlayDto getGroupedPlayDto(List<Play> plays) {
        Play first = plays.get(0);

        GroupedPlayDto dto = new GroupedPlayDto();
        dto.setTitle(first.getTitle());
        dto.setPrice(first.getPrice());
        dto.setSource(first.getSource());
        dto.setCategory(first.getCategory());
        dto.setEventInfo(first.getEventInfo());
        dto.setIsSpectacle(first.getIsSpectacle());
        dto.setIsRepertoire(first.getIsRepertoire());
        dto.setImageUrl(first.getImageUrl());
        dto.setScene(first.getScene());
        dto.setDuration(first.getDuration());
        dto.setDescription(first.getDescription());
        dto.setAdditionalInfo(first.getAdditionalInfo());
        dto.setDetailsJson(first.getDetailsJson());
        dto.setYoutubeUrl(first.getYoutubeUrl());

        // Convert theatre
        if (first.getTheatre() != null) {
            GroupedPlayDto.TheatreDto theatreDto = new GroupedPlayDto.TheatreDto();
            theatreDto.setId(first.getTheatre().getId());
            theatreDto.setName(first.getTheatre().getName());
            theatreDto.setUrl(first.getTheatre().getUrl());
            theatreDto.setImageUrl(first.getTheatre().getImageUrl());
            dto.setTheatre(theatreDto);
        }
        return dto;
    }

    @Transactional
    public void parseAndSaveTeatrWKrakowie() throws IOException {
        log.info("Rozpoczynam parsowanie Teatr w Krakowie...");

        // Sprawdź lub utwórz teatr
        String theatreName = "Teatr w Krakowie";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-KRAKOW");
                    t.setName(theatreName);
                    t.setUrl("https://teatrwkrakowie.pl");
                    t.setImageUrl("https://teatrwkrakowie.pl/images/logo.png");
                    return theatreRepository.save(t);
                });

        // Parsuj dane z listy spektakli
        List<TeatrWKrakowieDto.PlayDto> parsedPlays = teatrWKrakowieParser.parseAll();
        log.info("Sparsowano {} spektakli z listy", parsedPlays.size());

        final Theatre finalTheatre = theatre;

        // Zbierz unikalne URLe spektakli
        Map<String, TeatrWKrakowieDto.PlayDto> uniquePlays = new LinkedHashMap<>();
        for (TeatrWKrakowieDto.PlayDto playDto : parsedPlays) {
            if (playDto.getDetailUrl() != null && !playDto.getDetailUrl().isBlank()) {
                uniquePlays.put(playDto.getDetailUrl(), playDto);
            }
        }

        log.info("Znaleziono {} unikalnych spektakli", uniquePlays.size());
        int savedCount = 0;

        // Dla każdego spektaklu pobierz szczegóły i zapisz terminy
        for (Map.Entry<String, TeatrWKrakowieDto.PlayDto> entry : uniquePlays.entrySet()) {
            String detailUrl = entry.getKey();
            TeatrWKrakowieDto.PlayDto playDto = entry.getValue();

            try {
                log.info("Teatr w Krakowie: parsowanie spektaklu {} - {}", playDto.getName(), detailUrl);

                // Pobierz szczegóły ze strony spektaklu
                PlayDetailsDto details = teatrWKrakowieDetailParser.parse(detailUrl);

                // Użyj tytułu ze szczegółów (jest czyszczony), a tytułu z listy jako fallback
                if (details.getTitle() == null || details.getTitle().isBlank()) {
                    if (playDto.getName() != null && !playDto.getName().isBlank()) {
                        details.setTitle(playDto.getName());
                    }
                }

                // Pomiń jeśli brak tytułu
                if (details.getTitle() == null || details.getTitle().isBlank()) {
                    log.warn("Pomijam spektakl bez tytułu: {}", detailUrl);
                    continue;
                }

                // Jeśli są terminy, utwórz Play dla każdego
                if (details.getUpcomingTerms() != null && !details.getUpcomingTerms().isEmpty()) {
                    for (UpcomingTermDto term : details.getUpcomingTerms()) {
                        String showtime = formatTermShowtime(term);

                        // Pomiń jeśli brak showtime
                        if (showtime == null || showtime.isBlank()) {
                            continue;
                        }

                        final String playName = details.getTitle();

                        // Sprawdź czy ten konkretny termin już istnieje
                        boolean exists = playRepository.findAll().stream()
                                .anyMatch(p ->
                                        Objects.equals(p.getTitle(), playName) &&
                                                Objects.equals(p.getShowtime(), showtime) &&
                                                Objects.equals(p.getTheatre(), finalTheatre)
                                );

                        if (exists) {
                            log.debug("Pomijam duplikat: {} w {}", playName, showtime);
                            continue;
                        }

                        Play play = new Play();
                        play.setId(UUID.randomUUID().toString());
                        play.setTitle(details.getTitle());
                        play.setShowtime(showtime);
                        play.setSource(detailUrl);
                        play.setImageUrl(details.getImageUrl());
                        play.setScene(details.getScene());
                        play.setDuration(details.getDurationMinutesText());
                        play.setDescription(details.getDescription());
                        play.setCategory(details.getCategory());
                        play.setAdditionalInfo(details.getAdditionalInfo());
                        play.setYoutubeUrl(details.getYoutubeUrl());
                        play.setTicketUrl(term.getTicketUrl()); // Zapisz link do biletu
                        play.setDetailsJson(serializeDetails(details));
                        play.setTheatre(finalTheatre);
                        play.setIsSpectacle(playDto.isSpectacle());
                        play.setIsRepertoire(playDto.isRepertoireEvent());
                        play.setPrice(null); // Brak informacji o cenie na liście

                        playRepository.save(play);
                        savedCount++;
                    }
                } else {
                    // Jeśli brak terminów, utwórz jeden rekord bez showtime
                    final String playName = details.getTitle();

                    // Sprawdź czy ten spektakl już istnieje (bez konkretnego terminu)
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p ->
                                    Objects.equals(p.getTitle(), playName) &&
                                            Objects.equals(p.getTheatre(), finalTheatre) &&
                                            Objects.equals(p.getSource(), detailUrl)
                            );

                    if (!exists) {
                        Play play = new Play();
                        play.setId(UUID.randomUUID().toString());
                        play.setTitle(details.getTitle());
                        play.setShowtime(null);
                        play.setSource(detailUrl);
                        play.setImageUrl(details.getImageUrl());
                        play.setScene(details.getScene());
                        play.setDuration(details.getDurationMinutesText());
                        play.setDescription(details.getDescription());
                        play.setCategory(details.getCategory());
                        play.setAdditionalInfo(details.getAdditionalInfo());
                        play.setYoutubeUrl(details.getYoutubeUrl());
                        play.setDetailsJson(serializeDetails(details));
                        play.setTheatre(finalTheatre);
                        play.setIsSpectacle(playDto.isSpectacle());
                        play.setIsRepertoire(playDto.isRepertoireEvent());
                        play.setPrice(null);

                        playRepository.save(play);
                        savedCount++;
                    }
                }

                // Poczekaj między requestami
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Błąd podczas parsowania spektaklu {}: {}", detailUrl, e.getMessage());
            }
        }

        log.info("Zapisano {} spektakli do bazy danych", savedCount);
    }

    @Transactional
    public void enrichPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        // Grupuj po source URL - nie pobieraj tego samego spektaklu wielokrotnie
        Map<String, List<Play>> bySource = allPlays.stream()
                .filter(p -> p.getSource() != null && !p.getSource().isBlank())
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> playsWithSameSource = entry.getValue();

            try {
                log.info("Pobieram szczegóły dla: {}", sourceUrl);
                PlayDetailsDto details = teatrWKrakowieDetailParser.parse(sourceUrl);

                // Zapisz szczegóły do wszystkich spektakli z tym samym source
                for (Play play : playsWithSameSource) {
                    play.setImageUrl(details.getImageUrl());
                    play.setScene(details.getScene());
                    play.setDuration(details.getDurationMinutesText());
                    play.setDescription(details.getDescription());
                    play.setCategory(details.getCategory());
                    play.setAdditionalInfo(details.getAdditionalInfo());
                    play.setYoutubeUrl(details.getYoutubeUrl());

                    // Znajdź odpowiedni termin z upcomingTerms i zapisz ticketUrl
                    if (details.getUpcomingTerms() != null && play.getShowtime() != null) {
                        log.debug("Szukam ticketUrl dla showtime: {} (spektakl: {})", play.getShowtime(), play.getTitle());
                        log.debug("Dostępne terminy: {}", details.getUpcomingTerms().size());

                        for (UpcomingTermDto term : details.getUpcomingTerms()) {
                            // Porównaj showtime z terminem
                            // showtime może być w formacie "2026-04-25 19:00" lub "25 Kwiecień 19:00"
                            String termShowtime = formatTermShowtime(term);
                            log.debug(" Sprawdzam termin: {} vs {} (ticketUrl: {})", play.getShowtime(), termShowtime, term.getTicketUrl());

                            if (termShowtime != null && matchesShowtime(play.getShowtime(), termShowtime)) {
                                play.setTicketUrl(term.getTicketUrl());
                                log.debug(" -> Dopasowano ticket URL dla {}: {}", play.getShowtime(), term.getTicketUrl());
                                break;
                            }
                        }

                        if (play.getTicketUrl() == null) {
                            log.warn("Nie znaleziono ticketUrl dla showtime: {}", play.getShowtime());
                        }
                    }

                    // Zapisz pełne detale jako JSON
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(sourceUrl, "SUCCESS");

                // Poczekaj chwilę między requestami żeby nie przeciążać serwera
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów dla {}: {}", sourceUrl, e.getMessage());
                errorCount++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli, {} błędów", successCount, errorCount);

        // Log detailed results so the `results` map is actually used (avoids 'updated but never queried')
        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveBagatela() throws IOException {
        log.info("Rozpoczynam parsowanie Bagatela...");

        // Sprawdź lub utwórz teatr
        String theatreName = "Teatr Bagatela";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-BAGATELA");
                    t.setName(theatreName);
                    t.setUrl("https://bagatela.pl");
                    t.setImageUrl("https://bagatela.pl/wp-content/themes/bagatela/images/logo.png");
                    return theatreRepository.save(t);
                });

        // Parsuj dane
        BagatelaRepertuar repertuar = bagatelaParser.parseRepertuar();
        log.info("Bagatela: sparsowano {} spektakli", repertuar.getSpektakle() != null ? repertuar.getSpektakle().size() : 0);

        if (repertuar.getSpektakle() == null || repertuar.getSpektakle().isEmpty()) {
            log.warn("Nie znaleziono spektakli Bagatela");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (BagatelaSpektakl spektakl : repertuar.getSpektakle()) {
            // Skip spectacles without name
            if (spektakl.getNazwa() == null || spektakl.getNazwa().isBlank()) {
                continue;
            }

            // Skip spectacles without date
            if (spektakl.getKiedy() == null || spektakl.getKiedy().isBlank()) {
                log.warn("Bagatela: pomijam spektakl bez daty: {}", spektakl.getNazwa());
                continue;
            }

            final String playName = spektakl.getNazwa();
            final String showtime = spektakl.getKiedy();

            // Check if this exact performance already exists
            boolean exists = playRepository.findAll().stream()
                    .anyMatch(p ->
                            Objects.equals(p.getTitle(), playName) &&
                                    Objects.equals(p.getShowtime(), showtime) &&
                                    Objects.equals(p.getTheatre(), finalTheatre)
                    );

            if (exists) {
                log.debug("Bagatela: pomijam duplikat: {} {}", playName, showtime);
                continue;
            }

            Play play = new Play();
            play.setId(UUID.randomUUID().toString());
            play.setTitle(spektakl.getNazwa());
            play.setShowtime(spektakl.getKiedy());
            play.setPrice(parsePriceToDouble(spektakl.getCena()));
            play.setCategory(spektakl.getTyp());
            play.setDescription(null); // Description will be populated by enrichment
            play.setTicketUrl(spektakl.getTicketUrl());
            play.setTheatre(finalTheatre);
            play.setIsSpectacle(true);
            play.setIsRepertoire(true);
            play.setSource(spektakl.getOpis());

            playRepository.save(play);
            savedCount++;
        }

        log.info("Zapisano {} nowych spektakli Bagatela", savedCount);
    }

    // Added: import and save Barakah repertuar into DB (controller calls this)
    @Transactional
    public void parseAndSaveBarakah() throws IOException {
        log.info("Rozpoczynam parsowanie Teatr Barakah...");

        String theatreName = "Teatr Barakah";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-BARAKAH");
                    t.setName(theatreName);
                    t.setUrl("https://teatrbarakah.com");
                    t.setImageUrl("https://teatrbarakah.com/wp-content/uploads/2024/logo.png");
                    return theatreRepository.save(t);
                });

        BarakahRepertuar repertuar = barakahParser.parseRepertuar();
        if (repertuar == null || repertuar.getSpektakle() == null) {
            log.warn("Brak repertuaru Barakah");
            return;
        }

        int saved = 0;
        for (BarakahSpektakl s : repertuar.getSpektakle()) {
            if (s == null || s.getTytul() == null || s.getTytul().isBlank()) continue;

            String showtime;
            if (s.getData() != null && s.getGodzina() != null) {
                showtime = (s.getData() + " " + s.getGodzina()).trim();
            } else if (s.getData() != null) {
                showtime = s.getData();
            } else {
                showtime = null;
            }

            final String playName = s.getTytul();

            boolean exists = playRepository.findAll().stream()
                    .anyMatch(p -> Objects.equals(p.getTitle(), playName)
                            && Objects.equals(p.getShowtime(), showtime)
                            && Objects.equals(p.getTheatre(), theatre));

            if (exists) continue;

            Play play = new Play();
            play.setId(UUID.randomUUID().toString());
            play.setTitle(playName);
            play.setShowtime(showtime);
            //play.setSource(s.getLinkSpektaklu() != null ? s.getLinkSpektaklu() : s.getLinkBilety());
            play.setImageUrl(s.getLinkPoster());
            play.setTicketUrl(s.getLinkBilety());
            play.setTheatre(theatre);
            play.setIsSpectacle(true);
            play.setIsRepertoire(true);
            play.setPrice(null);

            playRepository.save(play);
            saved++;
        }

        log.info("Zapisano {} spektakli Barakah do bazy danych", saved);
    }

    // Added: parse and save KTO repertuar (maj + czerwiec 2026)
    @Transactional
    public void parseAndSaveKTO() {
        log.info("Rozpoczynam parsowanie Teatr KTO...");

        String theatreName = "Teatr KTO";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-KTO");
                    t.setName(theatreName);
                    t.setUrl("https://teatrkto.pl");
                    t.setImageUrl("");
                    return theatreRepository.save(t);
                });

        String[] urls = new String[]{"https://teatrkto.pl/maj-2026/", "https://teatrkto.pl/czerwiec-2026/"};

        Map<String, String> monthMap = getStringStringMap();

        for (String url : urls) {
            try {
                KtoRepertuar rep = ktoParser.parseRepertuar(url);
                if (rep == null || rep.getWydarzenia() == null) continue;

                String monthYear = rep.getMiesiac(); // e.g. "maj-2026"
                String monthNum = null;
                String year = String.valueOf(LocalDate.now().getYear());
                if (monthYear != null && monthYear.contains("-")) {
                    String[] parts = monthYear.split("-");
                    String monthName = parts[0].toLowerCase();
                    year = parts.length > 1 ? parts[1] : year;
                    monthNum = monthMap.getOrDefault(monthName, null);
                }

                for (KtoEvent ev : rep.getWydarzenia()) {
                    if (ev == null || ev.getTytul() == null || ev.getTytul().isBlank()) continue;

                    // Extract day from ev.getData() (e.g., "1 PT" -> "1")
                    String dayStr = null;
                    if (ev.getData() != null) {
                        String[] tokens = ev.getData().trim().split("\\s+");
                        if (tokens.length > 0) {
                            dayStr = tokens[0];
                        }
                    }

                    String showtime;
                    if (monthNum != null && dayStr != null && ev.getGodzina() != null) {
                        String day = dayStr.length() == 1 ? "0" + dayStr : dayStr;
                        showtime = year + "-" + monthNum + "-" + day + " " + ev.getGodzina();
                    } else if (ev.getGodzina() != null) {
                        showtime = ev.getData() + " " + ev.getGodzina();
                    } else {
                        showtime = ev.getData();
                    }

                    final String playName = ev.getTytul();

                    String finalShowtime = showtime;
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p -> Objects.equals(p.getTitle(), playName)
                                    && Objects.equals(p.getShowtime(), finalShowtime)
                                    && Objects.equals(p.getTheatre(), theatre));

                    if (exists) continue;

                    Play play = new Play();
                    play.setId(UUID.randomUUID().toString());
                    play.setTitle(playName);
                    play.setShowtime(showtime);
                    play.setSource(ev.getLinkBilety() != null ? ev.getLinkBilety() : ev.getLinkSpektaklu());
                    play.setTicketUrl(ev.getLinkBilety());
                    play.setTheatre(theatre);
                    play.setIsSpectacle(true);
                    play.setIsRepertoire(true);
                    play.setPrice(null);

                    playRepository.save(play);
                }

                // short sleep to be nice to the site
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("Błąd parsowania KTO z URL {}: {}", url, e.getMessage());
            }
        }

    }

    private static Map<String, String> getStringStringMap() {
        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("styczen", "01");
        monthMap.put("styczeń", "01");
        monthMap.put("styczenia", "01");
        monthMap.put("luty", "02");
        monthMap.put("lutego", "02");
        monthMap.put("marzec", "03");
        monthMap.put("marca", "03");
        monthMap.put("kwiecien", "04");
        monthMap.put("kwiecień", "04");
        monthMap.put("kwietnia", "04");
        monthMap.put("maj", "05");
        monthMap.put("czerwiec", "06");
        monthMap.put("czerwca", "06");
        return monthMap;
    }

    @Transactional
    public int deletePlaysByTitle(String title) {
        log.info("Usuwanie spektakli z tytułem: {}", title);
        List<Play> playsToDelete = playRepository.findByTitle(title);
        int count = playsToDelete.size();

        if (count > 0) {
            playRepository.deleteAll(playsToDelete);
            log.info("Usunięto {} spektakli", count);
        } else {
            log.info("Nie znaleziono spektakli do usunięcia o tytule: {}", title);
        }
        return count;
    }

    @Transactional
    public int deletePlaysByTheatre(String theatreName) {
        log.info("Usuwanie spektakli z teatru: {}", theatreName);
        List<Play> playsToDelete = playRepository.findByTheatreName(theatreName);
        int count = playsToDelete.size();

        if (count > 0) {
            playRepository.deleteAll(playsToDelete);
            log.info("Usunięto {} spektakli z teatru {}", count, theatreName);
        } else {
            log.info("Nie znaleziono spektakli do usunięcia dla teatru: {}", theatreName);
        }
        return count;
    }

    private String serializeDetails(PlayDetailsDto details) {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"contributors\": [");
            for (int i = 0; i < details.getContributors().size(); i++) {
                if (i > 0) json.append(", ");
                ContributorDto c = details.getContributors().get(i);
                json.append("{\"role\":\"").append(escapeJson(c.getRole()))
                        .append("\", \"name\":\"").append(escapeJson(c.getName()))
                        .append("\", \"profileUrl\":\"").append(escapeJson(c.getProfileUrl())).append("\"}");
            }
            json.append("], \"cast\": [");
            for (int i = 0; i < details.getCast().size(); i++) {
                if (i > 0) json.append(",");
                CastMemberDto c = details.getCast().get(i);
                json.append("{\"name\":\"").append(escapeJson(c.getName()))
                        .append("\", \"role\":\"").append(escapeJson(c.getRole()))
                        .append("\", \"profileUrl\":\"").append(escapeJson(c.getProfileUrl()))
                        .append("\", \"imageUrl\":\"").append(escapeJson(c.getImageUrl())).append("\"}");
            }
            json.append("], \"galleryImages\": [");
            for (int i = 0; i < details.getGalleryImages().size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(details.getGalleryImages().get(i))).append("\"");
            }
            json.append("]}");
            return json.toString();
        } catch (Exception e) {
            log.error("Błąd serializacji: {}", e.getMessage());
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Double parsePriceToDouble(String priceString) {
        if (priceString == null || priceString.isBlank()) {
            return null;
        }
        try {
            // Remove common price text patterns and extract just the number
            String cleanPrice = priceString.replaceAll("[^0-9,.]", "");
            // Replace comma with dot for decimal
            cleanPrice = cleanPrice.replace(",", ".");

            if (cleanPrice.isBlank()) {
                return null;
            }
            return Double.parseDouble(cleanPrice);
        } catch (NumberFormatException e) {
            log.warn("Nie udało się sparsować ceny: {}", priceString);
            return null;
        }
    }

    private String formatTermShowtime(UpcomingTermDto term) {
        if (term == null) return null;
        if (term.getDayLabel() != null && term.getDayLabel().matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return term.getDayLabel();
        }

        if (term.getDayOfMonth() != null && term.getMonth() != null && term.getTime() != null) {
            Map<String, String> monthMap = new HashMap<>();
            monthMap.put("styczeń", "01"); monthMap.put("stycznia", "01");
            monthMap.put("luty", "02"); monthMap.put("lutego", "02");
            monthMap.put("marzec", "03"); monthMap.put("marca", "03");
            monthMap.put("kwiecień", "04"); monthMap.put("kwietnia", "04");
            monthMap.put("maj", "05"); monthMap.put("maja", "05");
            monthMap.put("czerwiec", "06"); monthMap.put("czerwca", "06");
            monthMap.put("lipiec", "07"); monthMap.put("lipca", "07");
            monthMap.put("sierpień", "08"); monthMap.put("sierpnia", "08");
            monthMap.put("wrzesień", "09"); monthMap.put("września", "09");
            monthMap.put("październik", "10"); monthMap.put("października", "10");
            monthMap.put("listopad", "11"); monthMap.put("listopada", "11");
            monthMap.put("grudzień", "12"); monthMap.put("grudnia", "12");

            String monthNum = monthMap.get(term.getMonth().toLowerCase());
            if (monthNum != null) {
                String year = String.valueOf(LocalDate.now().getYear());
                String day = term.getDayOfMonth().length() == 1 ? "0" + term.getDayOfMonth() : term.getDayOfMonth();
                return year + "-" + monthNum + "-" + day + " " + term.getTime();
            }
        }
        return null;
    }

    private boolean matchesShowtime(String showtime1, String showtime2) {
        if (showtime1 == null || showtime2 == null) return false;

        // Normalize formats - replace T with space
        String s1 = showtime1.replace("T", " ");
        String s2 = showtime2.replace("T", " ");

        // Direct match after normalization
        if (s1.equals(s2)) return true;

        // Try to extract date and time components and compare
        try {
            // Extract time from both (last part after space or T)
            int space1 = Math.max(s1.lastIndexOf(' '), s1.lastIndexOf('T'));
            int space2 = Math.max(s2.lastIndexOf(' '), s2.lastIndexOf('T'));

            if (space1 < 0 || space2 < 0) return false;

            String time1 = s1.substring(space1 + 1).trim();
            String time2 = s2.substring(space2 + 1).trim();

            // Normalize time format (remove seconds if present)
            time1 = time1.substring(0, Math.min(time1.length(), 5)); // Take only HH:mm
            time2 = time2.substring(0, Math.min(time2.length(), 5));

            if (!time1.equals(time2)) return false; // Times must match

            // Extract dates
            String date1 = s1.substring(0, space1).trim();
            String date2 = s2.substring(0, space2).trim();

            // If both are in ISO format (YYYY-MM-DD), compare directly
            if (date1.matches("\\d{4}-\\d{2}-\\d{2}") && date2.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return date1.equals(date2);
            }

            // Extract day and month numbers for comparison
            // date1 might be "2026-04-25", date2 might be "25 kwiecien" or "2026-04-25"
            String day1 = extractDay(date1);
            String day2 = extractDay(date2);
            String month1 = extractMonth(date1);
            String month2 = extractMonth(date2);

            // If we can compare both day and month, do so
            if (day1 != null && day2 != null && month1 != null && month2 != null) {
                return day1.equals(day2) && month1.equals(month2);
            }

            // Fallback: if days and times match, consider it a match
            if (day1 != null && day2 != null) {
                return day1.equals(day2);
            }

            return false;

        } catch (Exception e) {
            log.debug("Błąd porównywania showtimes: {} vs {}", showtime1, showtime2);
            return false;
        }
    }

    private String extractDay(String date) {
        try {
            // Try ISO format first (YYYY-MM-DD)
            if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = date.split("-");
                return parts[2]; // Day
            }

            // Extract first number found (e.g., "25" from "25 kwiecien")
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\d+");
            java.util.regex.Matcher m = p.matcher(date);
            if (m.find()) {
                String day = m.group();
                // Pad with zero if single digit
                return day.length() == 1 ? "0" + day : day;
            }
        } catch (Exception e) {
            log.debug("Error extracting day from: {}", date);
        }
        return null;
    }

    private String extractMonth(String date) {
        try {
            // Try ISO format first (YYYY-MM-DD)
            if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = date.split("-");
                return parts[1]; // Month
            }

            // Try to find Polish month name
            Map<String, String> monthMap = new HashMap<>();
            monthMap.put("styczeń", "01"); monthMap.put("sterycznia", "01");
            monthMap.put("luty", "02"); monthMap.put("lutego", "02");
            monthMap.put("marzec", "03"); monthMap.put("marca", "03");
            monthMap.put("kwiecień", "04"); monthMap.put("kwietnia", "04");
            monthMap.put("maj", "05"); monthMap.put("maja", "05");
            monthMap.put("czerwiec", "06"); monthMap.put("czerwca", "06");
            monthMap.put("lipiec", "07"); monthMap.put("lipca", "07");
            monthMap.put("sierpień", "08"); monthMap.put("sierpnia", "08");
            monthMap.put("wrzesień", "09"); monthMap.put("września", "09");
            monthMap.put("październik", "10"); monthMap.put("października", "10");
            monthMap.put("listopad", "11"); monthMap.put("listopada", "11");
            monthMap.put("grudzień", "12"); monthMap.put("grudnia", "12");

            for (Map.Entry<String, String> entry : monthMap.entrySet()) {
                if (date.toLowerCase().contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting month from: {}", date);
        }
        return null;
    }

    @Transactional
    public void parseAndSaveLazniaNowa() throws IOException {
        log.info("Rozpoczynam parsowanie Łaźnia Nowa...");

        // Create or find theatre
        String theatreName = "Teatr Łaźnia Nowa";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-LAZNIA-NOWA");
                    t.setName(theatreName);
                    t.setUrl("https://www.laznianowa.pl");
                    t.setImageUrl("https://www.laznianowa.pl/themes/custom/theater/img/logo/logo.svg");
                    return theatreRepository.save(t);
                });

        // Parse events from calendar page
        List<LazniaNowaParser.EventInfo> events = lazniaNowaParser.parseKalendariumEvents();
        log.info("Łaźnia Nowa: znaleziono {} wydarzeń", events.size());

        if (events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń Łaźnia Nowa");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        // Group events by detail URL to fetch details only once per play
        Map<String, List<LazniaNowaParser.EventInfo>> eventsByUrl = new LinkedHashMap<>();
        for (LazniaNowaParser.EventInfo event : events) {
            eventsByUrl.computeIfAbsent(event.url(), k -> new ArrayList<>()).add(event);
        }

        for (Map.Entry<String, List<LazniaNowaParser.EventInfo>> entry : eventsByUrl.entrySet()) {
            String detailUrl = entry.getKey();
            List<LazniaNowaParser.EventInfo> eventsForThisPlay = entry.getValue();

            try {
                log.info("Łaźnia Nowa: parsowanie spektaklu {} - {}", eventsForThisPlay.get(0).title(), detailUrl);

                PlayDetailsDto details = lazniaNowaDetailParser.parse(detailUrl);

                if (eventsForThisPlay.get(0).title() != null && !eventsForThisPlay.get(0).title().isBlank()) {
                    details.setTitle(eventsForThisPlay.get(0).title());
                }

                if (details.getTitle() == null || details.getTitle().isBlank()) {
                    log.warn("Pomijam wydarzenie bez tytułu: {}", detailUrl);
                    continue;
                }

                for (LazniaNowaParser.EventInfo event : eventsForThisPlay) {
                    final String showtime;
                    if (event.date() != null && event.time() != null) {
                        showtime = event.date() + " " + event.time();
                    } else if (event.date() != null) {
                        showtime = event.date();
                    } else {
                        log.warn("Łaźnia Nowa: pomijam wydarzenie bez daty: {}", event.title());
                        continue;
                    }

                    final String playName = details.getTitle();

                    // Check if this exact performance already exists
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                    Objects.equals(p.getShowtime(), showtime) &&
                                    Objects.equals(p.getTheatre(), finalTheatre));

                    if (exists) {
                        log.debug("Łaźnia Nowa: pomijam duplikat: {} {}", playName, showtime);
                        continue;
                    }

                    Play play = new Play();
                    play.setId(UUID.randomUUID().toString());
                    play.setTitle(details.getTitle());
                    play.setShowtime(showtime);
                    play.setSource(detailUrl);
                    play.setImageUrl(details.getImageUrl());
                    play.setScene(event.scene());
                    play.setDuration(details.getDurationMinutesText());
                    play.setDescription(details.getDescription());
                    play.setAdditionalInfo(details.getAdditionalInfo());
                    play.setCategory(event.type());
                    play.setDetailsJson(serializeDetails(details));
                    play.setTicketUrl(event.ticketUrl());
                    play.setTheatre(finalTheatre);
                    play.setIsSpectacle(event.type() != null && event.type().toLowerCase().contains("spektakl"));
                    play.setIsRepertoire(true);

                    playRepository.save(play);
                    savedCount++;
                }

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas parsowania wydarzenia {}: {}", detailUrl, e.getMessage());
            }
        }
        log.info("Zapisano {} spektakli Łaźnia Nowa do bazy danych", savedCount);
    }

    @Transactional
    public void parseAndSaveTeatrNowy() throws IOException {
        log.info("Rozpoczynam parsowanie Teatr Nowy Proxima...");

        // Create or find theatre
        String theatreName = "Teatr Nowy Proxima";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-NOWY");
                    t.setName(theatreName);
                    t.setUrl("https://teatrnowy.com.pl");
                    t.setImageUrl("https://teatrnowy.com.pl/wp-content/uploads/2020/06/Group-91.svg");
                    return theatreRepository.save(t);
                });

        List<TeatrNowyParser.EventInfo> events = teatrNowyParser.parseRepertuarEvents();
        log.info("Teatr Nowy: znaleziono {} wydarzeń", events.size());

        if (events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń Teatr Nowy");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (TeatrNowyParser.EventInfo event : events) {
            try {
                if (event.title() == null || event.title().isBlank()) {
                    log.warn("Teatr Nowy: pomijam wydarzenie bez tytułu");
                    continue;
                }

                final String showtime;
                if (event.date() != null && event.time() != null) {
                    showtime = event.date() + " " + event.time();
                } else if (event.date() != null) {
                    showtime = event.date();
                } else {
                    log.warn("Teatr Nowy: pomijam wydarzenie bez daty: {}", event.title());
                    continue;
                }

                final String playName = event.title();

                boolean exists = playRepository.findAll().stream()
                        .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                Objects.equals(p.getShowtime(), showtime) &&
                                Objects.equals(p.getTheatre(), finalTheatre));

                if (exists) {
                    log.debug("Teatr Nowy: pomijam duplikat: {} {}", playName, showtime);
                    continue;
                }

                Play play = new Play();
                play.setId(UUID.randomUUID().toString());
                play.setTitle(event.title());
                play.setShowtime(showtime);
                play.setSource(event.url());
                play.setScene(event.scene());
                play.setTicketUrl(event.ticketUrl());
                play.setTheatre(finalTheatre);
                play.setIsSpectacle(true);
                play.setIsRepertoire(true);
                play.setPrice(null);

                playRepository.save(play);
                savedCount++;

                log.debug("Teatr Nowy: dodano wydarzenie: {} {} {}", event.title(), showtime, event.scene());
            } catch (Exception e) {
                log.error("Teatr Nowy: błąd podczas zapisywania wydarzenia: {}", e.getMessage());
            }
        }
        log.info("Zapisano {} spektakli Teatr Nowy do bazy danych", savedCount);
    }

    @Transactional
    public void enrichTeatrNowyPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Teatr Nowy o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        // Filter only Teatr Nowy plays
        List<Play> teatrNowyPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-NOWY".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && p.getSource().startsWith("https://teatrnowy.com.pl/repertoire/"))
                .toList();

        log.info("Found {} Teatr Nowy plays to enrich", teatrNowyPlays.size());

        // Group by source URL - don't fetch the same play multiple times
        Map<String, List<Play>> bySource = teatrNowyPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> playsWithSameSource = entry.getValue();

            try {
                log.info("Pobieram szczegóły Teatr Nowy dla: {}", sourceUrl);
                PlayDetailsDto details = teatrNowyDetailParser.parse(sourceUrl);

                // Save details to all plays with the same source URL
                for (Play play : playsWithSameSource) {
                    play.setImageUrl(details.getImageUrl());
                    play.setDescription(details.getDescription());
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(sourceUrl, "SUCCESS");

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Teatr Nowy dla {}: {}", sourceUrl, e.getMessage());
                errorCount++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Teatr Nowy, {} błędów", successCount, errorCount);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Teatr Nowy (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveScenaStu() throws IOException {
        log.info("Rozpoczynam parsowanie Scena STU...");

        // Create or find theatre
        String theatreName = "Scena STU";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-SCENA-STU");
                    t.setName(theatreName);
                    t.setUrl("https://scenastu.pl");
                    t.setImageUrl("https://scenastu.pl/wp-content/themes/scena-stu/assets/img/logo.svg");
                    return theatreRepository.save(t);
                });

        // Parse events from calendar
        List<ScenaStuParser.EventInfo> events = scenaStuParser.parseCalendarEvents();
        log.info("Scena STU: znaleziono {} wydarzeń", events.size());

        if (events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń Scena STU");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (ScenaStuParser.EventInfo event : events) {
            try {
                if (event.title() == null || event.title().isBlank()) {
                    log.warn("Scena STU: pomijam wydarzenie bez tytułu");
                    continue;
                }

                final String showtime;
                if (event.date() != null && event.time() != null) {
                    showtime = event.date() + " " + event.time();
                } else if (event.date() != null) {
                    showtime = event.date();
                } else {
                    log.warn("Scena STU: pomijam wydarzenie bez daty: {}", event.title());
                    continue;
                }

                final String playName = event.title();

                boolean exists = playRepository.findAll().stream()
                        .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                Objects.equals(p.getShowtime(), showtime) &&
                                Objects.equals(p.getTheatre(), finalTheatre));

                if (exists) {
                    log.debug("Scena STU: pomijam duplikat: {} {}", playName, showtime);
                    continue;
                }

                Play play = new Play();
                play.setId(UUID.randomUUID().toString());
                play.setTitle(event.title());
                play.setShowtime(showtime);
                play.setSource(event.url());
                play.setTicketUrl(event.ticketUrl());
                play.setTheatre(finalTheatre);
                play.setIsSpectacle(true);
                play.setIsRepertoire(true);
                play.setPrice(null);

                playRepository.save(play);
                savedCount++;
                log.debug("Dodano wydarzenie: {} - {}", event.title(), showtime);

            } catch (Exception e) {
                log.error("Scena STU: błąd podczas zapisywania wydarzenia: {}", e.getMessage());
            }
        }
        log.info("Zapisano {} spektakli Scena STU do bazy danych", savedCount);
    }

    @Transactional
    public void enrichScenaStuPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Scena STU o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        List<Play> scenaStuPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-SCENA-STU".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && p.getSource().startsWith("https://scenastu.pl/spektakl/"))
                .toList();

        log.info("Found {} Scena STU plays to enrich", scenaStuPlays.size());

        Map<String, List<Play>> bySource = scenaStuPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> playsWithSameSource = entry.getValue();

            try {
                log.info("Pobieram szczegóły Scena STU dla: {}", sourceUrl);
                PlayDetailsDto details = scenaStuDetailParser.parse(sourceUrl);

                // Save details to all plays with the same source URL
                for (Play play : playsWithSameSource) {
                    play.setImageUrl(details.getImageUrl());
                    play.setDescription(details.getDescription());
                    play.setAdditionalInfo(details.getAdditionalInfo());
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(sourceUrl, "SUCCESS");

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Scena STU dla {}: {}", sourceUrl, e.getMessage());
                errorCount++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Scena STU, {} błędów", successCount, errorCount);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Scena STU (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveOperaKrakowska() throws IOException {
        log.info("Rozpoczynam parsowanie Opera Krakowska...");

        // Create or find theatre
        String theatreName = "Opera Krakowska";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-OPERA-KRAKOW");
                    t.setName(theatreName);
                    t.setUrl("https://opera.krakow.pl");
                    t.setImageUrl("https://opera.krakow.pl/assets/front/images/logo.png");
                    return theatreRepository.save(t);
                });

        // Parse events from AJAX API (May to August 2026)
        List<OperaKrakowskaParser.EventInfo> events = operaKrakowskaParser.parseRepertuarEvents();
        log.info("Opera Krakowska: znaleziono {} wydarzeń", events.size());

        if (events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń Opera Krakowska");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        // For each event, create a Play entry
        for (OperaKrakowskaParser.EventInfo event : events) {
            try {
                if (event.title() == null || event.title().isBlank()) {
                    log.warn("Opera Krakowska: pomijam wydarzenie bez tytułu");
                    continue;
                }

                final String showtime;
                if (event.date() != null && event.time() != null) {
                    showtime = event.date() + " " + event.time();
                } else if (event.date() != null) {
                    showtime = event.date();
                } else {
                    log.warn("Opera Krakowska: pomijam wydarzenie bez daty: {}", event.title());
                    continue;
                }

                final String playName = event.title();

                boolean exists = playRepository.findAll().stream()
                        .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                Objects.equals(p.getShowtime(), showtime) &&
                                Objects.equals(p.getTheatre(), finalTheatre));

                if (exists) {
                    log.debug("Opera Krakowska: pomijam duplikat: {} {}", playName, showtime);
                    continue;
                }

                Play play = new Play();
                play.setId(UUID.randomUUID().toString());
                play.setTitle(event.title());
                play.setShowtime(showtime);
                play.setSource(event.slug() != null ? "https://opera.krakow.pl/spektakle/" + event.slug() : null);
                play.setTicketUrl(event.ticketUrl());
                play.setScene(event.place());
                play.setCategory(event.type());
                play.setTheatre(finalTheatre);
                play.setIsSpectacle(true);
                play.setIsRepertoire(true);
                play.setPrice(null);

                playRepository.save(play);
                savedCount++;

                log.debug("Dodano wydarzenie: {} - {} - {} - {}", event.title(), showtime, event.place(), event.type());
            } catch (Exception e) {
                log.error("Opera Krakowska: błąd podczas zapisywania wydarzenia: {}", e.getMessage());
            }
        }
        log.info("Zapisano {} spektakli Opera Krakowska do bazy danych", savedCount);
    }

    @Transactional
    public void enrichOperaKrakowskaPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Opera Krakowska o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        // Filter only Opera Krakowska plays
        List<Play> operaPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-OPERA-KRAKOW".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && p.getSource().startsWith("https://opera.krakow.pl/spektakle/"))
                .toList();

        log.info("Found {} Opera Krakowska plays to enrich", operaPlays.size());

        // Group by source URL - don't fetch the same play multiple times
        Map<String, List<Play>> bySource = operaPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> playsWithSameSource = entry.getValue();

            try {
                log.info("Pobieram szczegóły Opera Krakowska dla: {}", sourceUrl);
                PlayDetailsDto details = operaKrakowskaDetailParser.parse(sourceUrl);

                // Save details to all plays with the same source URL
                for (Play play : playsWithSameSource) {
                    play.setImageUrl(details.getImageUrl());
                    play.setDescription(details.getDescription());
                    play.setAdditionalInfo(details.getAdditionalInfo());
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(sourceUrl, "SUCCESS");

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Opera Krakowska dla {}: {}", sourceUrl, e.getMessage());
                errorCount++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Opera Krakowska, {} błędów", successCount, errorCount);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Opera Krakowska (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveLudowy() {
        log.info("Rozpoczynam parsowanie Teatr Ludowy...");

        // Create or find theatre
        String theatreName = "Teatr Ludowy";

        final Theatre finalTheatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-LUDOWY");
                    t.setName(theatreName);
                    t.setUrl("https://ludowy.pl");
                    t.setImageUrl("https://ludowy.pl/wp-content/uploads/2024/01/logo-teatr-ludowy-1.png");
                    return theatreRepository.save(t);
                });
        int savedCount = 0;

        // Parse May, June, and August 2026
        String[] monthUrls = {
                "https://ludowy.pl/repertuar/?rep_month=5&rep_year=2026",
                "https://ludowy.pl/repertuar/?rep_month=6&rep_year=2026",
                "https://ludowy.pl/repertuar/?rep_month=8&rep_year=2026"
        };

        for (String url : monthUrls) {
            try {
                log.info("Parsowanie repertuaru Ludowy z URL: {}", url);
                LudowyRepertuar repertuar = ludowyParser.parseRepertuar(url);

                if (repertuar.getWydarzenia() == null || repertuar.getWydarzenia().isEmpty()) {
                    log.warn("Nie znaleziono wydarzeń dla {}", repertuar.getMiesiac());
                    continue;
                }

                // Parse month from "MM-YYYY" format
                String monthYear = repertuar.getMiesiac(); // e.g., "05-2026"
                String year = String.valueOf(LocalDate.now().getYear());
                String monthNum = "05";

                if (monthYear != null && monthYear.contains("-")) {
                    String[] parts = monthYear.split("-");
                    monthNum = parts[0]; // Already in MM format
                    year = parts[1];
                }

                for (LudowyEvent event : repertuar.getWydarzenia()) {
                    // Skip events without title
                    if (event.getTytul() == null || event.getTytul().isBlank()) {
                        continue;
                    }

                    // Skip events without date or time
                    if (event.getData() == null || event.getData().isBlank() ||
                            event.getGodzina() == null || event.getGodzina().isBlank()) {
                        log.warn("Ludowy: pomijam wydarzenie bez daty/godziny: {}", event.getTytul());
                        continue;
                    }

                    // Extract day from data (e.g., "13 Środa" -> "13")
                    String dayStr = event.getData().split("\\s+")[0];
                    String day = dayStr.length() == 1 ? "0" + dayStr : dayStr;

                    // Build showtime in ISO format: "2026-05-13 19:00"
                    final String showtime = year + "-" + monthNum + "-" + day + " " + event.getGodzina();
                    final String playName = event.getTytul();

                    // Check if this exact performance already exists
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                    Objects.equals(p.getShowtime(), showtime) &&
                                    Objects.equals(p.getTheatre(), finalTheatre));

                    if (exists) {
                        log.debug("Ludowy: pomijam duplikat: {} {}", playName, showtime);
                        continue;
                    }

                    Play play = new Play();
                    play.setId(UUID.randomUUID().toString());
                    play.setTitle(event.getTytul());
                    play.setShowtime(showtime);
                    play.setScene(event.getScena());
                    play.setTicketUrl(event.getLinkBilety());
                    play.setImageUrl(event.getPlakat());
                    play.setSource(event.getLinkSpektaklu() != null ? event.getLinkSpektaklu() : url);
                    play.setTheatre(finalTheatre);
                    play.setIsSpectacle(true);
                    play.setIsRepertoire(true);
                    play.setPrice(null); // No price info available

                    playRepository.save(play);
                    savedCount++;

                    log.debug("Ludowy: dodano wydarzenie: {} {} {}", event.getTytul(), event.getData(), event.getGodzina());
                }
            } catch (Exception e) {
                log.error("Błąd podczas parsowania repertuaru Ludowy z {}: {}", url, e.getMessage());
            }
        }
        log.info("Zapisano {} wydarzeń Teatr Ludowy do bazy danych", savedCount);
    }

    @Transactional
    public void enrichLudowyPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Teatr Ludowy o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        // Filter only Ludowy plays that have detail URLs in source field
        List<Play> ludowyPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-LUDOWY".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && p.getSource().startsWith("https://ludowy.pl/spektakle/"))
                .toList();

        log.info("Found {} Ludowy plays to enrich", ludowyPlays.size());

        // Group by detail URL (stored in source) - don't fetch the same play multiple times
        Map<String, List<Play>> byDetailUrl = ludowyPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : byDetailUrl.entrySet()) {
            String detailUrl = entry.getKey();
            List<Play> playsWithSameUrl = entry.getValue();

            try {
                log.info("Pobieram szczegóły Ludowy dla: {}", detailUrl);
                PlayDetailsDto details = ludowyDetailParser.parse(detailUrl);

                // Save details to all plays with the same detail URL
                for (Play play : playsWithSameUrl) {
                    play.setImageUrl(details.getImageUrl());
                    play.setDescription(details.getDescription());
                    play.setYoutubeUrl(details.getYoutubeUrl());
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(detailUrl, "SUCCESS");

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Ludowy dla {}: {}", detailUrl, e.getMessage());
                errorCount++;
                results.put(detailUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Ludowy, {} błędów", successCount, errorCount);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Ludowy (detailUrl -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveGroteska() {
        log.info("Rozpoczynam parsowanie Teatr Groteska...");

        // Create or find theatre
        String theatreName = "Teatr Groteska";

        final Theatre finalTheatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-GROTESKA");
                    t.setName(theatreName);
                    t.setUrl("https://www.groteska.pl");
                    t.setImageUrl("https://www.groteska.pl/wp-content/themes/groteska/assets/img/logo.png");
                    return theatreRepository.save(t);
                });
        int savedCount = 0;

        // Parse May, June, July, and August 2026
        int[] months = {5, 6, 7, 8};

        for (int month : months) {
            try {
                log.info("Parsowanie repertuaru Groteska dla miesiąca: {}", month);
                GroteskaRepertuar repertuar = groteskaParser.parseRepertuar(month, 2026);

                if (repertuar.getWydarzenia() == null || repertuar.getWydarzenia().isEmpty()) {
                    log.warn("Nie znaleziono wydarzeń dla miesiąca {}", month);
                    continue;
                }

                for (GroteskaEvent event : repertuar.getWydarzenia()) {
                    // Skip events without title
                    if (event.getTytul() == null || event.getTytul().isBlank()) {
                        continue;
                    }

                    // Skip events without date or time
                    if (event.getData() == null || event.getData().isBlank() ||
                            event.getGodzina() == null || event.getGodzina().isBlank()) {
                        log.warn("Groteska: pomijam wydarzenie bez daty/godziny: {}", event.getTytul());
                        continue;
                    }

                    // Convert date from "DD.MM.YYYY" to "YYYY-MM-DD HH:MM" format
                    String[] dateParts = event.getData().split("\\.");
                    if (dateParts.length != 3) {
                        log.warn("Nieprawidłowy format daty: {}", event.getData());
                        continue;
                    }

                    String day = dateParts[0];
                    String monthStr = dateParts[1];
                    String year = dateParts[2];

                    // Build showtime in ISO format
                    final String showtime = year + "-" + monthStr + "-" + day + " " + event.getGodzina();
                    final String playName = event.getTytul();

                    // Check if this exact performance already exists
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                                    Objects.equals(p.getShowtime(), showtime) &&
                                    Objects.equals(p.getTheatre(), finalTheatre));

                    if (exists) {
                        log.debug("Groteska: pomijam duplikat: {} {}", playName, showtime);
                        continue;
                    }

                    Play play = new Play();
                    play.setId(UUID.randomUUID().toString());
                    play.setTitle(event.getTytul());
                    play.setShowtime(showtime);
                    play.setScene(event.getScena());
                    play.setTicketUrl(event.getLinkBilety());
                    play.setSource(event.getEventId() != null
                            ? "https://kup-bilet.groteska.pl/msi/mvc/pl/event/" + event.getEventId()
                            : event.getLinkBilety());
                    play.setTheatre(finalTheatre);
                    play.setIsSpectacle(true);
                    play.setIsRepertoire(true);
                    play.setPrice(null); // No price info available

                    playRepository.save(play);
                    savedCount++;

                    log.debug("Groteska: dodano wydarzenie: {} {} {}", event.getTytul(), event.getData(), event.getGodzina());
                }
            } catch (Exception e) {
                log.error("Błąd podczas parsowania repertuaru Groteska dla miesiąca {}: {}", month, e.getMessage());
            }
        }
        log.info("Zapisano {} spektakli Teatr Groteska do bazy danych (maj-sierpień 2026)", savedCount);
    }

    @Transactional
    public int updateKTOSources() {
        Theatre theatre = theatreRepository.findByName("Teatr KTO").orElse(null);
        if (theatre == null) {
            log.warn("Teatr KTO nie istnieje w bazie");
            return 0;
        }

        String[] urls = new String[]{"https://teatrkto.pl/maj-2026/", "https://teatrkto.pl/czerwiec-2026/"};
        Map<String, String> monthMap = getStringStringMap();
        int updatedCount = 0;

        for (String url : urls) {
            try {
                KtoRepertuar rep = ktoParser.parseRepertuar(url);
                if (rep == null || rep.getWydarzenia() == null) continue;

                String monthYear = rep.getMiesiac();
                String monthNum = null;
                String year = String.valueOf(LocalDate.now().getYear());
                if (monthYear != null && monthYear.contains("-")) {
                    String[] parts = monthYear.split("-");
                    monthNum = monthMap.getOrDefault(parts[0].toLowerCase(), null);
                    year = parts.length > 1 ? parts[1] : year;
                }

                for (KtoEvent ev : rep.getWydarzenia()) {
                    if (ev == null || ev.getTytul() == null || ev.getTytul().isBlank()) continue;

                    String newSource = ev.getLinkBilety() != null ? ev.getLinkBilety() : ev.getLinkSpektaklu();
                    if (newSource == null) continue;

                    String dayStr = null;
                    if (ev.getData() != null) {
                        String[] tokens = ev.getData().trim().split("\\s+");
                        if (tokens.length > 0) dayStr = tokens[0];
                    }

                    String showtime;
                    if (monthNum != null && dayStr != null && ev.getGodzina() != null) {
                        String day = dayStr.length() == 1 ? "0" + dayStr : dayStr;
                        showtime = year + "-" + monthNum + "-" + day + " " + ev.getGodzina();
                    } else if (ev.getGodzina() != null) {
                        showtime = ev.getData() + " " + ev.getGodzina();
                    } else {
                        showtime = ev.getData();
                    }

                    final String playName = ev.getTytul();
                    final String finalShowtime = showtime;
                    final Theatre finalTheatre = theatre;

                    List<Play> matching = playRepository.findAll().stream()
                            .filter(p -> Objects.equals(p.getTitle(), playName)
                                    && Objects.equals(p.getShowtime(), finalShowtime)
                                    && Objects.equals(p.getTheatre(), finalTheatre))
                            .toList();

                    for (Play play : matching) {
                        play.setSource(newSource);
                        playRepository.save(play);
                        updatedCount++;
                        log.info("KTO: zaktualizowano source '{}' {} → {}", playName, showtime, newSource);
                    }
                }

                Thread.sleep(500);
            } catch (Exception e) {
                log.error("Błąd podczas aktualizacji source KTO z {}: {}", url, e.getMessage());
            }
        }

        log.info("Zaktualizowano source dla {} spektakli KTO", updatedCount);
        return updatedCount;
    }

    @Transactional
    public int updateGroteskaSources() {
        List<Play> groteskaPlays = playRepository.findAll().stream()
                .filter(p -> p.getTheatre() != null && "TH-GROTESKA".equals(p.getTheatre().getId()))
                .toList();

        if (groteskaPlays.isEmpty()) {
            log.warn("Brak spektakli Groteska w bazie");
            return 0;
        }

        int updatedCount = 0;
        for (Play play : groteskaPlays) {
            String uniqueSource = play.getTicketUrl() != null && play.getShowtime() != null
                    ? play.getTicketUrl() + "|" + play.getShowtime()
                    : play.getId();
            play.setSource(uniqueSource);
            playRepository.save(play);
            updatedCount++;
            log.debug("Groteska: zaktualizowano source '{}' {} → {}", play.getTitle(), play.getShowtime(), uniqueSource);
        }

        log.info("Zaktualizowano source dla {} spektakli Groteska", updatedCount);
        return updatedCount;
    }

    @Transactional
    public void parseAndSaveVariete() {
        log.info("Rozpoczynam parsowanie Teatr Variete...");

        // Create or find theatre
        String theatreName = "Teatr Variete";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-VARIETE");
                    t.setName(theatreName);
                    t.setUrl("https://www.teatrvariete.pl");
                    t.setImageUrl("https://www.teatrvariete.pl/wp-content/themes/variete/assets/img/logo.png");
                    return theatreRepository.save(t);
                });

        // Parse repertoire (May-August 2026)
        VarieteRepertuar repertuar = varieteParser.parseRepertuar();
        log.info("Variete: sparsowano {} spektakli", repertuar.getSpektakle() != null ? repertuar.getSpektakle().size() : 0);

        if (repertuar.getSpektakle() == null || repertuar.getSpektakle().isEmpty()) {
            log.warn("Nie znaleziono spektakli Variete");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (VarieteSpektakl spektakl : repertuar.getSpektakle()) {
            // Skip spectacles without name
            if (spektakl.getNazwa() == null || spektakl.getNazwa().isBlank()) {
                continue;
            }

            // Skip spectacles without date
            if (spektakl.getKiedy() == null || spektakl.getKiedy().isBlank()) {
                log.warn("Variete: pomijam spektakl bez daty: {}", spektakl.getNazwa());
                continue;
            }

            final String playName = spektakl.getNazwa();
            final String showtime = spektakl.getKiedy();

            // Check if this exact performance already exists
            boolean exists = playRepository.findAll().stream()
                    .anyMatch(p -> Objects.equals(p.getTitle(), playName) &&
                            Objects.equals(p.getShowtime(), showtime) &&
                            Objects.equals(p.getTheatre(), finalTheatre));

            if (exists) {
                log.debug("Pomijam duplikat: {} - {}", playName, showtime);
                continue;
            }

            Play play = new Play();
            play.setId(UUID.randomUUID().toString());
            play.setTitle(spektakl.getNazwa());
            play.setShowtime(spektakl.getKiedy());
            play.setPrice(parsePriceToDouble(spektakl.getCena()));
            play.setCategory(spektakl.getTyp());
            play.setDescription(null); // Description will be populated by enrichment if needed
            play.setTicketUrl(spektakl.getTicketUrl());
            play.setTheatre(finalTheatre);
            play.setIsSpectacle(true);
            play.setIsRepertoire(true);
            play.setSource(spektakl.getOpis()); // Store detail URL in source field

            playRepository.save(play);
            savedCount++;
        }

        log.info("Zapisano {} spektakli Variete do bazy danych (maj-sierpień 2026)", savedCount);
    }

    @Transactional
    public void enrichVarietePlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Variete o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        // Filter only Variete plays that have detail URLs in source field
        List<Play> varietePlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-VARIETE".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && p.getSource().startsWith("https://wordpress.teatrvariete.pl/blog/repertoire/"))
                .toList();

        log.info("Found {} Variete plays to enrich", varietePlays.size());

        // Group by detail URL (stored in source) - don't fetch the same play multiple times
        Map<String, List<Play>> byDetailUrl = varietePlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int successCount = 0;
        int errorCount = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : byDetailUrl.entrySet()) {
            String detailUrl = entry.getKey();
            List<Play> playsWithSameUrl = entry.getValue();

            try {
                log.info("Pobieram szczegóły Variete dla: {}", detailUrl);
                PlayDetailsDto details = varieteDetailParser.parse(detailUrl);

                // Save details to all plays with the same detail URL
                for (Play play : playsWithSameUrl) {
                    // Update source to be the detail URL
                    play.setSource(detailUrl);
                    play.setImageUrl(details.getImageUrl());
                    play.setDescription(details.getDescription());
                    play.setCategory(details.getCategory());
                    play.setAdditionalInfo(details.getAdditionalInfo());

                    // Save full details as JSON
                    play.setDetailsJson(serializeDetails(details));
                    playRepository.save(play);
                }

                successCount++;
                results.put(detailUrl, "SUCCESS");

                // Wait between requests to avoid overloading the server
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Variete dla {}: {}", detailUrl, e.getMessage());
                errorCount++;
                results.put(detailUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Variete, {} błędów", successCount, errorCount);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Variete (detailUrl -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void parseAndSaveStary() throws IOException {
        log.info("Rozpoczynam parsowanie Stary Teatr...");

        String theatreName = "Stary Teatr";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-STARY");
                    t.setName(theatreName);
                    t.setUrl("https://bilety.stary.pl");
                    t.setImageUrl("");
                    return theatreRepository.save(t);
                });

        List<StaryParser.EventInfo> events = staryParser.parseRepertuarEvents();
        log.info("Znaleziono {} wydarzeń Stary", events != null ? events.size() : 0);

        if (events == null || events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń Stary");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (StaryParser.EventInfo ev : events) {
            try {
                if (ev == null || ev.url() == null) continue;

                PlayDetailsDto details = staryDetailParser.parse(ev.url());
                String title = (details != null && details.getTitle() != null && !details.getTitle().isBlank()) ? details.getTitle() : ev.title();
                if (title == null || title.isBlank()) continue;

                if (details != null && details.getUpcomingTerms() != null && !details.getUpcomingTerms().isEmpty()) {
                    for (UpcomingTermDto term : details.getUpcomingTerms()) {
                        String showtime = formatTermShowtime(term);
                        if (showtime == null || showtime.isBlank()) continue;

                        boolean exists = playRepository.findAll().stream()
                                .anyMatch(p -> Objects.equals(p.getTitle(), title)
                                        && Objects.equals(p.getShowtime(), showtime)
                                        && Objects.equals(p.getTheatre(), finalTheatre));
                        if (exists) continue;

                        Play play = new Play();
                        play.setId(UUID.randomUUID().toString());
                        play.setTitle(title);
                        play.setShowtime(showtime);
                        play.setSource(ev.url());
                        play.setImageUrl(details.getImageUrl());
                        play.setScene(details.getScene());
                        play.setDuration(details.getDurationMinutesText());
                        play.setDescription(details.getDescription());
                        play.setCategory(details.getCategory());
                        play.setAdditionalInfo(details.getAdditionalInfo());
                        play.setYoutubeUrl(details.getYoutubeUrl());
                        play.setDetailsJson(serializeDetails(details));
                        play.setTicketUrl(term.getTicketUrl());
                        play.setTheatre(finalTheatre);
                        play.setIsSpectacle(true);
                        play.setIsRepertoire(true);
                        play.setPrice(null);

                        playRepository.save(play);
                        savedCount++;
                    }
                } else {
                    boolean exists = playRepository.findAll().stream()
                            .anyMatch(p -> Objects.equals(p.getTitle(), title)
                                    && Objects.equals(p.getTheatre(), finalTheatre)
                                    && Objects.equals(p.getSource(), ev.url()));
                    if (exists) continue;

                    Play play = new Play();
                    play.setId(UUID.randomUUID().toString());
                    play.setTitle(title);
                    play.setShowtime(null);
                    play.setSource(ev.url());
                    if (details != null) {
                        play.setImageUrl(details.getImageUrl());
                        play.setScene(details.getScene());
                        play.setDuration(details.getDurationMinutesText());
                        play.setDescription(details.getDescription());
                        play.setCategory(details.getCategory());
                        play.setAdditionalInfo(details.getAdditionalInfo());
                        play.setYoutubeUrl(details.getYoutubeUrl());
                        play.setDetailsJson(serializeDetails(details));
                    }
                    play.setTheatre(finalTheatre);
                    play.setIsSpectacle(true);
                    play.setIsRepertoire(true);
                    play.setPrice(null);

                    playRepository.save(play);
                    savedCount++;
                }

                Thread.sleep(300);
            } catch (Exception e) {
                log.error("Błąd parsowania Stary wydarzenia {}: {}", ev.url(), e.getMessage());
            }
        }

        log.info("Zapisano {} spektakli Stary Teatr do bazy danych", savedCount);
    }

    @Transactional
    public void parseAndSaveAST() throws IOException {
        log.info("Rozpoczynam parsowanie AST...");

        String theatreName = "Teatr AST";
        Theatre theatre = theatreRepository.findByName(theatreName)
                .orElseGet(() -> {
                    Theatre t = new Theatre();
                    t.setId("TH-AST");
                    t.setName(theatreName);
                    t.setUrl("https://krakow.ast.krakow.pl");
                    t.setImageUrl("");
                    return theatreRepository.save(t);
                });

        List<ASTParser.EventInfo> events = astParser.parseRepertuarEvents();
        log.info("Znaleziono {} wydarzeń AST", events != null ? events.size() : 0);

        if (events == null || events.isEmpty()) {
            log.warn("Nie znaleziono wydarzeń AST");
            return;
        }

        final Theatre finalTheatre = theatre;
        int savedCount = 0;

        for (ASTParser.EventInfo ev : events) {
            try {
                if (ev == null || ev.url() == null) continue;

                PlayDetailsDto details = astDetailParser.parse(ev.url());
                String title = (details != null && details.getTitle() != null && !details.getTitle().isBlank()) ? details.getTitle() : ev.title();
                if (title == null || title.isBlank()) continue;

                String showtime = (ev.date() != null && ev.time() != null) ? ev.date() + " " + ev.time() : null;

                boolean exists = playRepository.findAll().stream()
                        .anyMatch(p -> Objects.equals(p.getTitle(), title)
                                && Objects.equals(p.getShowtime(), showtime)
                                && Objects.equals(p.getTheatre(), finalTheatre));
                if (exists) continue;

                Play play = new Play();
                play.setId(UUID.randomUUID().toString());
                play.setTitle(title);
                play.setShowtime(showtime);
                play.setSource(ev.url());
                if (details != null) {
                    play.setImageUrl(details.getImageUrl());
                    play.setScene(ev.scene() != null ? ev.scene() : details.getScene());
                    play.setDuration(details.getDurationMinutesText());
                    play.setDescription(details.getDescription());
                    play.setCategory(details.getCategory());
                    play.setAdditionalInfo(details.getAdditionalInfo());
                    play.setYoutubeUrl(details.getYoutubeUrl());
                    play.setDetailsJson(serializeDetails(details));
                }
                play.setTheatre(finalTheatre);
                play.setIsSpectacle(true);
                play.setIsRepertoire(true);
                play.setPrice(null);

                playRepository.save(play);
                savedCount++;

                Thread.sleep(200);
            } catch (Exception e) {
                log.error("Błąd parsowania AST wydarzenia {}: {}", ev.url(), e.getMessage());
            }
        }

        log.info("Zapisano {} spektakli AST do bazy danych", savedCount);
    }

    @Transactional
    public void enrichBagatelaPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Bagatela o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        List<Play> bagatelaPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-BAGATELA".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && !p.getSource().isBlank())
                .toList();

        log.info("Found {} Bagatela plays to enrich", bagatelaPlays.size());

        Map<String, List<Play>> bySource = bagatelaPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int success = 0;
        int errors = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> plays = entry.getValue();

            try {
                log.info("Pobieram szczegóły Bagatela dla: {}", sourceUrl);
                PlayDetailsDto details = bagatelaDetailParser.parse(sourceUrl);

                for (Play play : plays) {
                    if (details != null) {
                        play.setImageUrl(details.getImageUrl());
                        play.setDescription(details.getDescription());
                        play.setDetailsJson(serializeDetails(details));
                        playRepository.save(play);
                    }
                }

                success++;
                results.put(sourceUrl, "SUCCESS");
                Thread.sleep(300);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Bagatela dla {}: {}", sourceUrl, e.getMessage());
                errors++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Bagatela, {} błędów", success, errors);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Bagatela (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public void enrichBarakahPlaysWithDetails() throws IOException {
        log.info("Rozpoczynam wzbogacanie spektakli Barakah o szczegóły...");
        List<Play> allPlays = playRepository.findAll();

        List<Play> barakahPlays = allPlays.stream()
                .filter(p -> p.getTheatre() != null && "TH-BARAKAH".equals(p.getTheatre().getId()))
                .filter(p -> p.getSource() != null && !p.getSource().isBlank())
                .toList();

        log.info("Found {} Barakah plays to enrich", barakahPlays.size());

        Map<String, List<Play>> bySource = barakahPlays.stream()
                .collect(Collectors.groupingBy(Play::getSource));

        int success = 0;
        int errors = 0;
        Map<String, String> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Play>> entry : bySource.entrySet()) {
            String sourceUrl = entry.getKey();
            List<Play> plays = entry.getValue();

            try {
                log.info("Pobieram szczegóły Barakah dla: {}", sourceUrl);
                PlayDetailsDto details = barakahDetailParser.parse(sourceUrl);

                for (Play play : plays) {
                    if (details != null) {
                        play.setImageUrl(details.getImageUrl());
                        play.setDescription(details.getDescription());
                        play.setDetailsJson(serializeDetails(details));
                        playRepository.save(play);
                    }
                }

                success++;
                results.put(sourceUrl, "SUCCESS");
                Thread.sleep(300);
            } catch (Exception e) {
                log.error("Błąd podczas pobierania szczegółów Barakah dla {}: {}", sourceUrl, e.getMessage());
                errors++;
                results.put(sourceUrl, "ERROR: " + e.getMessage());
            }
        }

        log.info("Wzbogacono {} spektakli Barakah, {} błędów", success, errors);

        if (!results.isEmpty()) {
            log.info("Szczegółowe wyniki pobierania szczegółów Barakah (source -> status):");
            results.forEach((source, status) -> log.debug("  {} -> {}", source, status));
        }
    }

    @Transactional
    public int manuallyEnrichGroteskaPlay(
            String title,
            String category,
            String posterUrl,
            String detailUrl,
            String scene,
            Boolean isSpectacle) {

        log.info("Ręczne wzbogacanie spektakli Groteska o tytule: {}, pobieranie szczegółów z URL: {}", title, detailUrl);

        List<Play> plays = playRepository.findAll().stream()
                .filter(p -> p.getTheatre() != null && "TH-GROTESKA".equals(p.getTheatre().getId()))
                .filter(p -> Objects.equals(p.getTitle(), title))
                .toList();

        if (plays.isEmpty()) {
            log.warn("Nie znaleziono spektakli Groteska o tytule: {}", title);
            return 0;
        }

        try {
            PlayDetailsDto details = groteskaDetailParser.parse(detailUrl);

            for (Play play : plays) {
                play.setImageUrl(details.getImageUrl() != null ? details.getImageUrl() : posterUrl);
                play.setDescription(details.getDescription());
                play.setAdditionalInfo(details.getAdditionalInfo());
                play.setScene(details.getScene() != null ? details.getScene() : scene);
                play.setCategory(category != null && !category.isBlank() ? category : details.getCategory());
                play.setDuration(details.getDurationMinutesText());
                play.setYoutubeUrl(details.getYoutubeUrl());
                if (isSpectacle != null) {
                    play.setIsSpectacle(isSpectacle);
                }
                play.setDetailsJson(serializeDetails(details));
                play.setSource(detailUrl);
                playRepository.save(play);
            }

            log.info("Wzbogacono {} spektakli Groteska o tytule: {}", plays.size(), title);
            return plays.size();

        } catch (Exception e) {
            log.error("Błąd podczas parsowania szczegółów Groteska z {}: {}", detailUrl, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int updateAllGroteskaScenesWithAddress() {
        log.info("Aktualizowanie scen dla spektakli Groteska z adresem Skarbowa 2...");

        List<Play> groteskaPlays = playRepository.findAll().stream()
                .filter(p -> p.getTheatre() != null && "TH-GROTESKA".equals(p.getTheatre().getId()))
                .toList();

        String address = "Skarbowa 2";
        int updatedCount = 0;

        for (Play play : groteskaPlays) {
            String currentScene = play.getScene();

            if (currentScene == null || currentScene.isBlank()) {
                play.setScene(address);
            } else if (!currentScene.contains(address)) {
                play.setScene(currentScene + ", " + address);
            } else {
                continue;
            }

            playRepository.save(play);
            updatedCount++;
        }

        log.info("Zaktualizowano sceny dla {} spektakli Groteska z adresem Skarbowa 2", updatedCount);
        return updatedCount;
    }

}
