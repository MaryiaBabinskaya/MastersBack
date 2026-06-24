package com.krakow.theaters.controller;

import com.krakow.theaters.dto.ErrorResponse;
import com.krakow.theaters.dto.TicketDto;
import com.krakow.theaters.service.TicketService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping(value = "/purchase", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> purchaseTicket(@RequestBody PurchaseRequest request) {
        try {
            TicketDto ticket = ticketService.purchaseTicket(
                    request.getUserId(),
                    request.getPlayId(),
                    request.getSeatInfo()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("PURCHASE_FAILED", e.getMessage())
            );
        }
    }

    @GetMapping(value = "/user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TicketDto>> getUserTickets(@PathVariable Long userId) {
        List<TicketDto> tickets = ticketService.getUserTickets(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping(value = "/user/{userId}/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TicketDto>> getUserActiveTickets(@PathVariable Long userId) {
        List<TicketDto> tickets = ticketService.getUserActiveTickets(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping(value = "/{ticketId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTicketById(@PathVariable Long ticketId) {
        try {
            TicketDto ticket = ticketService.getTicketById(ticketId);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse("TICKET_NOT_FOUND", e.getMessage())
            );
        }
    }

    @PutMapping(value = "/{ticketId}/status", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateTicketStatus(
            @PathVariable Long ticketId,
            @RequestBody StatusUpdateRequest request) {
        try {
            TicketDto ticket = ticketService.updateTicketStatus(ticketId, request.getStatus());
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("UPDATE_FAILED", e.getMessage())
            );
        }
    }

    @DeleteMapping(value = "/{ticketId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelTicket(@PathVariable Long ticketId) {
        try {
            ticketService.cancelTicket(ticketId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("CANCEL_FAILED", e.getMessage())
            );
        }
    }

    @Getter
    @Setter
    public static class PurchaseRequest {
        private Long userId;
        private String playId;
        private String seatInfo;
    }

    @Getter
    @Setter
    public static class StatusUpdateRequest {
        private String status;
    }
}
