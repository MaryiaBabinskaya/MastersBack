package com.krakow.theaters.repository;

import com.krakow.theaters.model.Play;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayRepository extends JpaRepository<Play, String> {

    List<Play> findByTheatreName(String theatreName);

    List<Play> findByTitle(String title);

    List<Play> findBySource(String source);
}