package com.iris.backend.service;

import com.iris.backend.dto.HistoricalPointDTO;
import com.iris.backend.dto.PlaceDTO;
import com.iris.backend.model.Place;
import com.iris.backend.repository.PlaceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iris.backend.dto.CreatePlaceRequestDTO;
import com.iris.backend.model.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);


    /**
     * Constructs a new instance of PlaceService.
     *
     * This service is responsible for handling place-related operations, such as retrieving
     * historical or public gallery data. It utilizes the provided PlaceRepository for database
     * interactions and ObjectMapper for JSON operations.
     *
     * @param placeRepository the repository used for querying and managing Place entities
     * @param objectMapper the JSON mapper used for processing JSON data
     */
    public PlaceService(PlaceRepository placeRepository, ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds a batch of historical galleries based on a list of historical points and a specified radius.
     *
     * This method processes a list of historical geographical points and searches for places
     * that match the historical data within a given radius. It transforms the list of historical
     * data points into JSON format, queries the database using the processed data, and maps the
     * results into a list of PlaceDTO objects.
     *
     * @param history the list of Historical*/
    @Transactional(readOnly = true)
    public List<PlaceDTO> findHistoricalGalleriesBatch(List<HistoricalPointDTO> history, double radius) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        try {
            // Wandle die Liste der Punkte in einen JSON-String um
            String historyJson = objectMapper.writeValueAsString(history);

            List<Place> places = placeRepository.findPlacesMatchingHistoricalBatch(historyJson, radius);

            return places.stream()
                    .map(place -> new PlaceDTO(
                            place.getId(),
                            place.getGooglePlaceId(),
                            place.getName(),
                            place.getAddress(),
                            null // <-- HIER DIE KORREKTUR
                    ))
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            // Fehler bei der JSON-Umwandlung, hier könntest du loggen
            throw new RuntimeException("Error processing historical data", e);
        }
    }

    /**
     * Retrieves a list of public galleries within a specified radius of a given location and time window.
     *
     * The method filters places based on their geographical proximity to the provided latitude and longitude,
     * checks if they have active public photos within the specified time window, and converts the results into
     * a list of PlaceDTO objects.
     *
     * @param latitude the latitude of the center point for the search area
     * @param longitude the longitude of the center point for the search area
     * @param radius the radius (in meters) around the specified location to search for public galleries
     * @param timestamp an optional timestamp used to define the time window for filtering active public photos
     * @return a list of PlaceDTO objects representing the public galleries that meet the specified criteria
     */
    public List<PlaceDTO> getPublicGalleries(double latitude, double longitude, double radius, Optional<OffsetDateTime> timestamp) {
        List<Place> places;

            places = placeRepository.findPlacesWithActivePublicPhotosInTimeWindow(latitude, longitude, radius, timestamp.get());


        return places.stream()
                .map(place -> new PlaceDTO(
                        place.getId(),
                        place.getGooglePlaceId(),
                        place.getName(),
                        place.getAddress(),
                        null // <-- HIER DIE KORREKTUR
                ))
                .collect(Collectors.toList());
    }

    /**
     * Creates and saves a new custom place.
     *
     * @param request The DTO with the place details.
     * @param creator The user who is creating the place.
     * @return A DTO representation of the newly saved place.
     */
    @Transactional
    public PlaceDTO createCustomPlace(CreatePlaceRequestDTO request, User creator) {
        Place newPlace = new Place();

        newPlace.setName(request.name());

        // Da dies kein Google Place ist, generieren wir eine eigene, einzigartige ID,
        // um Konflikte zu vermeiden.
        newPlace.setGooglePlaceId("custom_" + UUID.randomUUID().toString());

        // Erstelle einen PostGIS-Punkt aus den Koordinaten
        Point location = geometryFactory.createPoint(new Coordinate(request.longitude(), request.latitude()));
        newPlace.setLocation(location);

        // Die Adresse lassen wir bei benutzerdefinierten Orten erstmal weg
        newPlace.setAddress("Custom Location");

        Place savedPlace = placeRepository.save(newPlace);

        // Wandle die gespeicherte Entität in ein DTO um und gib sie zurück
        return new PlaceDTO(
                savedPlace.getId(),
                savedPlace.getGooglePlaceId(),
                savedPlace.getName(),
                savedPlace.getAddress(),
                null // Ein neuer Ort hat noch keine Fotos
        );
    }

}