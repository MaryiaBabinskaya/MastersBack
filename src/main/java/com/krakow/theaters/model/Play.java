package com.krakow.theaters.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Play {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "title")
    private String title;

    @Column(name = "showtime")
    private String showtime;

    @Column(name = "price")
    private Double price;

    @Column(name = "source")
    private String source;

    @Column(name = "category")
    private String category;

    @Column(name = "event_info")
    private String eventInfo;

    @Column(name = "is_spectacle")
    private Boolean isSpectacle;

    @Column(name = "is_repertoire")
    private Boolean isRepertoire;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "scene")
    private String scene;

    @Column(name = "duration")
    private String duration;

    @Column(name = "description", length = 5000)
    private String description;

    @Column(name = "additional_info", length = 1000)
    private String additionalInfo;

    @Column(name = "details_json", length = 10000)
    private String detailsJson;

    @Column(name = "ticket_url", length = 1000)
    private String ticketUrl;

    @Column(name = "youtube_url", length = 500)
    private String youtubeUrl;

    @ManyToOne
    @JoinColumn(name = "theatre_id")
    private Theatre theatre;

    @Transient
    public LocalDateTime getShowtimesDateTime() {
        if (showtime == null || showtime.isBlank() || showtime.contains("null")) {
            return null;
        }
        try {
            return LocalDateTime.parse(showtime.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

}