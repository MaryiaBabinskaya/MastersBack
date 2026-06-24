package com.krakow.theaters.service;

import com.krakow.theaters.dto.TicketDto;
import com.krakow.theaters.model.Play;
import com.krakow.theaters.model.Ticket;
import com.krakow.theaters.model.User;
import com.krakow.theaters.repository.PlayRepository;
import com.krakow.theaters.repository.TicketRepository;
import com.krakow.theaters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final PlayRepository playRepository;

    @Transactional
    public TicketDto purchaseTicket(Long userId, String playId, String seatInfo) {
        log.info("[TICKET CREATE] Starting ticket purchase - userId: {}, playId: {}, seatInfo: {}", userId, playId, seatInfo);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[TICKET CREATE] User not found - userId: {}", userId);
                    return new RuntimeException("User not found");
                });

        log.info("[TICKET CREATE] User found - nickname: {}", user.getNickname());

        Play play = playRepository.findById(playId)
                .orElseThrow(() -> {
                    log.error("[TICKET CREATE] Play not found - playId: {}", playId);
                    return new RuntimeException("Play not found");
                });

        log.info("[TICKET CREATE] Play found - title: {}, showtime: {}", play.getTitle(), play.getShowtime());

        Ticket ticket = new Ticket();
        ticket.setUser(user);
        ticket.setPlay(play);
        ticket.setPurchasePrice(play.getPrice());
        ticket.setSeatInfo(seatInfo);

        Ticket savedTicket = ticketRepository.save(ticket);

        log.info("[TICKET CREATE] Ticket saved successfully - ticketId: {}, status: {}", savedTicket.getId(), savedTicket.getStatus());

        TicketDto result = mapToDto(savedTicket);
        log.info("[TICKET CREATE] Purchase completed - returning TicketDto with id: {}", result.getId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<TicketDto> getUserTickets(Long userId) {
        return ticketRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDto> getUserActiveTickets(Long userId) {
        return ticketRepository.findByUserIdAndStatus(userId, "active").stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDto getTicketById(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        return mapToDto(ticket);
    }

    @Transactional
    public TicketDto updateTicketStatus(Long ticketId, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(status);
        Ticket savedTicket = ticketRepository.save(ticket);
        return mapToDto(savedTicket);
    }

    @Transactional
    public void cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus("cancelled");
        ticketRepository.save(ticket);
    }

    private TicketDto mapToDto(Ticket ticket) {
        TicketDto dto = new TicketDto();
        dto.setId(ticket.getId());
        dto.setUserId(ticket.getUser().getId());
        dto.setUserNickname(ticket.getUser().getNickname());
        dto.setPlayId(ticket.getPlay().getId());
        dto.setPlayTitle(ticket.getPlay().getTitle());
        dto.setPlayShowtime(ticket.getPlay().getShowtime());
        dto.setPlayImageUrl(ticket.getPlay().getImageUrl());
        dto.setTheatreName(ticket.getPlay().getTheatre() != null ? ticket.getPlay().getTheatre().getName() : null);
        dto.setPurchaseDate(ticket.getPurchaseDate());
        dto.setPurchasePrice(ticket.getPurchasePrice());
        dto.setStatus(ticket.getStatus());
        dto.setSeatInfo(ticket.getSeatInfo());

        return dto;
    }
}