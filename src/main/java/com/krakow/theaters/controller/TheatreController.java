package com.krakow.theaters.controller;

import com.krakow.theaters.dto.GroteskaRepertuar;
import com.krakow.theaters.dto.GroupedPlayDto;
import com.krakow.theaters.dto.KtoRepertuar;
import com.krakow.theaters.dto.LudowyRepertuar;
import com.krakow.theaters.dto.PlayDetailsDto;
import com.krakow.theaters.dto.TeatrWKrakowieDto;
import com.krakow.theaters.dto.VarieteRepertuar;
import com.krakow.theaters.dto.BagatelaRepertuar;
import com.krakow.theaters.model.Play;
import com.krakow.theaters.service.BagatelaDetailParser;
import com.krakow.theaters.service.BagatelaParser;
import com.krakow.theaters.service.BarakahDetailParser;
import com.krakow.theaters.service.GroteskaDetailParser;
import com.krakow.theaters.service.GroteskaParser;
import com.krakow.theaters.service.KtoParser;
import com.krakow.theaters.service.LudowyParser;
import com.krakow.theaters.service.LudowyDetailParser;
import com.krakow.theaters.service.StaryParser;
import com.krakow.theaters.service.StaryDetailParser;
import com.krakow.theaters.service.TeatrWKrakowieDetailParser;
import com.krakow.theaters.service.TeatrWKrakowieParser;
import com.krakow.theaters.service.TheatreService;
import com.krakow.theaters.service.VarieteParser;
import com.krakow.theaters.service.VarieteDetailParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TheatreController {

    private final TheatreService theatreService;
    private final TeatrWKrakowieParser teatrWKrakowieParser;
    private final TeatrWKrakowieDetailParser teatrWKrakowieDetailParser;
    private final BagatelaParser bagatelaParser;
    private final BagatelaDetailParser bagatelaDetailParser;
    private final StaryParser staryParser;
    private final StaryDetailParser staryDetailParser;
    private final BarakahDetailParser barakahDetailParser;
    private final KtoParser ktoParser;
    private final LudowyParser ludowyParser;
    private final LudowyDetailParser ludowyDetailParser;
    private final GroteskaParser groteskaParser;
    private final GroteskaDetailParser groteskaDetailParser;
    private final VarieteParser varieteParser;
    private final VarieteDetailParser varieteDetailParser;

    @GetMapping(value = "/plays", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GroupedPlayDto> getAllPlays() {
        return theatreService.getGroupedPlays();
    }

    @GetMapping(value = "/plays/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Play> getAllPlaysRaw() {
        return theatreService.getAllPlays();
    }

    @GetMapping(value = "/plays/count-by-theatre", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Long> countPlaysByTheatre() {
        return theatreService.countPlaysByTheatre();
    }

    @GetMapping(value = "/theatres", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<com.krakow.theaters.model.Theatre> getAllTheatres() {
        return theatreService.getAllTheatres();
    }

    // --- Teatr w Krakowie ---

    @GetMapping(value = "/teatr-w-krakowie/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TeatrWKrakowieDto.PlayDto> parseTeatrWKrakowie() throws IOException {
        return teatrWKrakowieParser.parseAll();
    }

    @GetMapping(value = "/teatr-w-krakowie/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importTeatrWKrakowie() throws IOException {
        theatreService.parseAndSaveTeatrWKrakowie();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/teatr-w-krakowie/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getPlayDetails(@RequestParam String url) throws IOException {
        return teatrWKrakowieDetailParser.parse(url);
    }

    @GetMapping(value = "/teatr-w-krakowie/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Bagatela ---

    @GetMapping(value = "/bagatela/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public BagatelaRepertuar parseBagatela() throws IOException {
        return bagatelaParser.parseRepertuar();
    }

    @GetMapping(value = "/bagatela/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importBagatela() throws IOException {
        theatreService.parseAndSaveBagatela();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/bagatela/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getBagatelaPlayDetails(@RequestParam String url) throws IOException {
        return bagatelaDetailParser.parse(url);
    }

    @GetMapping(value = "/bagatela/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichBagatelaPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichBagatelaPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Stary Teatr ---

    @GetMapping(value = "/stary/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> parseStary() throws IOException {
        return staryParser.parseRepertuarEventUrls();
    }

    @GetMapping(value = "/stary/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importStary() throws IOException {
        theatreService.parseAndSaveStary();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/stary/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getStaryPlayDetails(@RequestParam String url) throws IOException {
        return staryDetailParser.parse(url);
    }

    // --- AST ---

    @GetMapping(value = "/ast/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importAST() throws IOException {
        theatreService.parseAndSaveAST();
        return "{\"status\": \"success\"}";
    }

    // --- Łaźnia Nowa ---

    @GetMapping(value = "/laznia-nowa/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importLazniaNowa() throws IOException {
        theatreService.parseAndSaveLazniaNowa();
        return "{\"status\": \"success\"}";
    }

    // --- Teatr Nowy Proxima ---

    @GetMapping(value = "/teatr-nowy/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importTeatrNowy() throws IOException {
        theatreService.parseAndSaveTeatrNowy();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/teatr-nowy/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichTeatrNowyPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichTeatrNowyPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Cleanup ---

    @DeleteMapping(value = "/cleanup/delete-by-title", produces = MediaType.APPLICATION_JSON_VALUE)
    public String deletePlaysByTitle(@RequestParam String title) {
        int count = theatreService.deletePlaysByTitle(title);
        return "{\"status\": \"success\", \"count\": " + count + "}";
    }

    @DeleteMapping(value = "/cleanup/delete-by-theatre", produces = MediaType.APPLICATION_JSON_VALUE)
    public String deletePlaysByTheatre(@RequestParam String theatreName) {
        int count = theatreService.deletePlaysByTheatre(theatreName);
        return "{\"status\": \"success\", \"count\": " + count + "}";
    }

    // --- Scena STU ---

    @GetMapping(value = "/scena-stu/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importScenaStu() throws IOException {
        theatreService.parseAndSaveScenaStu();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/scena-stu/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichScenaStuPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichScenaStuPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Opera Krakowska ---

    @GetMapping(value = "/opera-krakowska/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importOperaKrakowska() throws IOException {
        theatreService.parseAndSaveOperaKrakowska();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/opera-krakowska/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichOperaKrakowskaPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichOperaKrakowskaPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Barakah ---

    @GetMapping(value = "/barakah/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importBarakah() throws IOException {
        theatreService.parseAndSaveBarakah();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/barakah/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getBarakahPlayDetails(@RequestParam String url) throws IOException {
        return barakahDetailParser.parse(url);
    }

    @GetMapping(value = "/barakah/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichBarakahPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichBarakahPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Teatr KTO ---

    @GetMapping(value = "/kto/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public KtoRepertuar parseKto(@RequestParam String url) throws IOException {
        return ktoParser.parseRepertuar(url);
    }

    @GetMapping(value = "/kto/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importKTO() {
        theatreService.parseAndSaveKTO();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/kto/update-sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public String updateKTOSources() {
        int count = theatreService.updateKTOSources();
        return "{\"status\": \"success\", \"updated\": " + count + "}";
    }

    // --- Teatr Ludowy ---

    @GetMapping(value = "/ludowy/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public LudowyRepertuar parseLudowy(@RequestParam String url) throws IOException {
        return ludowyParser.parseRepertuar(url);
    }

    @GetMapping(value = "/ludowy/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importLudowy() {
        theatreService.parseAndSaveLudowy();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/ludowy/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getLudowyPlayDetails(@RequestParam String url) throws IOException {
        return ludowyDetailParser.parse(url);
    }

    @GetMapping(value = "/ludowy/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichLudowyPlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichLudowyPlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }

    // --- Groteska ---

    @GetMapping(value = "/groteska/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public GroteskaRepertuar parseGroteska(@RequestParam int month, @RequestParam int year) {
        return groteskaParser.parseRepertuar(month, year);
    }

    @GetMapping(value = "/groteska/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importGroteska() {
        theatreService.parseAndSaveGroteska();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/groteska/update-sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public String updateGroteskaSources() {
        int count = theatreService.updateGroteskaSources();
        return "{\"status\": \"success\", \"updated\": " + count + "}";
    }

    @GetMapping(value = "/groteska/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getGroteskaPlayDetails(@RequestParam String url) throws IOException {
        return groteskaDetailParser.parse(url);
    }

    @GetMapping(value = "/groteska/enrich-manual", produces = MediaType.APPLICATION_JSON_VALUE)
    public String manuallyEnrichGroteskaPlay(
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam String posterUrl,
            @RequestParam String detailUrl,
            @RequestParam String scene,
            @RequestParam(required = false) Boolean isSpectacle) {

        int count = theatreService.manuallyEnrichGroteskaPlay(
                title, category, posterUrl, detailUrl, scene, isSpectacle);
        return "{\"status\": \"success\", \"count\": " + count + "}";
    }

    @GetMapping(value = "/groteska/update-scenes-address", produces = MediaType.APPLICATION_JSON_VALUE)
    public String updateGroteskaScenesWithAddress() {
        int count = theatreService.updateAllGroteskaScenesWithAddress();
        return "{\"status\": \"success\", \"count\": " + count + "}";
    }

    // --- Variete ---

    @GetMapping(value = "/variete/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public VarieteRepertuar parseVariete() {
        return varieteParser.parseRepertuar();
    }

    @GetMapping(value = "/variete/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public String importVariete() {
        theatreService.parseAndSaveVariete();
        return "{\"status\": \"success\"}";
    }

    @GetMapping(value = "/variete/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayDetailsDto getVarietePlayDetails(@RequestParam String url) throws IOException {
        return varieteDetailParser.parse(url);
    }

    @GetMapping(value = "/variete/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public String enrichVarietePlaysWithDetails() {
        new Thread(() -> {
            try { theatreService.enrichVarietePlaysWithDetails(); } catch (IOException ignored) {}
        }).start();
        return "{\"status\": \"started\"}";
    }
}
