package com.krakow.theaters.repository;

import com.krakow.theaters.model.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TheatreRepository extends JpaRepository<Theatre, String> {

    Optional<Theatre> findByName(String name);
}