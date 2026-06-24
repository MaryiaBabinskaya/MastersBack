package com.krakow.theaters.repository;

import com.krakow.theaters.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserId(Long userId);

    List<Ticket> findByUserIdAndStatus(Long userId, String status);
}