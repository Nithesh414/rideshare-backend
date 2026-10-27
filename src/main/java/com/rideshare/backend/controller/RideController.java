
package com.rideshare.backend.controller;

import com.rideshare.backend.model.Booking;
import com.rideshare.backend.model.Ride;
import com.rideshare.backend.repository.BookingRepository;
import com.rideshare.backend.repository.RideRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RideController {

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;

    public RideController(
            RideRepository rideRepository,
            BookingRepository bookingRepository
    ) {
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
    }

    // GET ALL RIDES / SEARCH RIDES BY ROUTE
    @GetMapping("/rides")
    public List<Ride> getRides(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer seats
    ) {
        return rideRepository.findAll()
                .stream()
                .filter(ride -> ride.getSeats() > 0)

                // Date filter
                .filter(ride ->
                        isBlank(date)
                                || sameText(ride.getDate(), date)
                )

                // Available seats filter
                .filter(ride ->
                        seats == null
                                || (seats > 0 && ride.getSeats() >= seats)
                )

                // Route filter
                .filter(ride -> matchesRoute(ride, from, to))
                .toList();
    }

    // GET ONE RIDE
    @GetMapping("/rides/{id}")
    public ResponseEntity<?> getRide(@PathVariable Long id) {
        return rideRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("message", "Ride not found"))
                );
    }

    // CREATE A RIDE
    @PostMapping("/rides")
    public ResponseEntity<?> createRide(@RequestBody Ride ride) {

        if (isBlank(ride.getFrom())
                || isBlank(ride.getTo())
                || isBlank(ride.getDate())
                || isBlank(ride.getTime())
                || isBlank(ride.getDriverName())) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Required fields are missing"));
        }

        if (ride.getSeats() < 1 || ride.getPrice() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid seats or price"));
        }

        if (sameCity(ride.getFrom(), ride.getTo())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Start and destination must differ"
                    ));
        }

        ride.setFrom(ride.getFrom().trim());
        ride.setTo(ride.getTo().trim());
        ride.setDriverName(ride.getDriverName().trim());
        ride.setRouteStops(normalizeStops(ride.getRouteStops()));

        if (!validRoute(ride)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Route stops must be unique and between start and destination"
                    ));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rideRepository.save(ride));
    }

    // CREATE BOOKING
    @PostMapping("/rides/{id}/bookings")
    @Transactional
    public ResponseEntity<?> createBooking(
            @PathVariable Long id,
            @RequestBody Booking booking
    ) {
        Optional<Ride> optionalRide = rideRepository.findById(id);

        if (optionalRide.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Ride not found"));
        }

        Ride ride = optionalRide.get();

        if (isBlank(booking.getPassengerName())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Passenger name is required"));
        }

        if (booking.getSeats() < 1 || booking.getSeats() > ride.getSeats()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Not enough seats available"));
        }

        booking.setRideId(ride.getId());
        booking.setPassengerName(booking.getPassengerName().trim());
        booking.setTotalPrice(ride.getPrice() * booking.getSeats());

        ride.setSeats(ride.getSeats() - booking.getSeats());

        rideRepository.save(ride);
        Booking savedBooking = bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedBooking);
    }

    // GET ALL BOOKINGS
    @GetMapping("/bookings")
    public List<Booking> getBookings() {
        return bookingRepository.findAll();
    }

    // CANCEL BOOKING AND RESTORE SEATS
    @DeleteMapping("/bookings/{bookingId}")
    @Transactional
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId
    ) {
        Optional<Booking> optionalBooking =
                bookingRepository.findById(bookingId);

        if (optionalBooking.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Booking not found"));
        }

        Booking booking = optionalBooking.get();

        Optional<Ride> optionalRide =
                rideRepository.findById(booking.getRideId());

        if (optionalRide.isPresent()) {
            Ride ride = optionalRide.get();

            ride.setSeats(ride.getSeats() + booking.getSeats());
            rideRepository.save(ride);
        }

        bookingRepository.delete(booking);

        return ResponseEntity.ok(
                Map.of("message", "Booking cancelled successfully")
        );
    }

    // ROUTE MATCHING
    // Pickup must appear before destination
    private boolean matchesRoute(
            Ride ride,
            String pickup,
            String dropoff
    ) {
        List<String> route = getCompleteRoute(ride);

        int pickupIndex = findStop(route, pickup);
        int dropoffIndex = findStop(route, dropoff);

        if (!isBlank(pickup) && pickupIndex == -1) {
            return false;
        }

        if (!isBlank(dropoff) && dropoffIndex == -1) {
            return false;
        }

        if (!isBlank(pickup) && !isBlank(dropoff)) {
            return pickupIndex < dropoffIndex;
        }

        return true;
    }

    // Build complete route
    private List<String> getCompleteRoute(Ride ride) {
        List<String> route = new ArrayList<>();

        route.add(ride.getFrom());

        if (!isBlank(ride.getRouteStops())) {
            for (String stop : ride.getRouteStops().split(",")) {
                if (!stop.isBlank()) {
                    route.add(stop.trim());
                }
            }
        }

        route.add(ride.getTo());

        return route;
    }

    // Find city using normalized names
    private int findStop(List<String> route, String city) {
        if (isBlank(city)) {
            return -1;
        }

        String searchedCity = normalizeCity(city);

        for (int i = 0; i < route.size(); i++) {
            if (normalizeCity(route.get(i)).equals(searchedCity)) {
                return i;
            }
        }

        return -1;
    }

    // Normalize a city/address for comparison
    private String normalizeCity(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String city = value.trim();

        // Example:
        // "Coimbatore, Tamil Nadu, India" -> "coimbatore"
        if (city.contains(",")) {
            city = city.split(",", 2)[0].trim();
        }

        return city
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private boolean sameCity(String first, String second) {
        return normalizeCity(first).equals(normalizeCity(second));
    }

    private boolean sameText(String first, String second) {
        if (first == null || second == null) {
            return false;
        }

        return first.trim().equalsIgnoreCase(second.trim());
    }

    // Validate route has no duplicate city names
    private boolean validRoute(Ride ride) {
        List<String> route = getCompleteRoute(ride);

        for (int i = 0; i < route.size(); i++) {
            if (isBlank(route.get(i))) {
                return false;
            }

            for (int j = i + 1; j < route.size(); j++) {
                if (sameCity(route.get(i), route.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    // Normalize intermediate stops
    private String normalizeStops(String stops) {
        if (stops == null || stops.isBlank()) {
            return "";
        }

        List<String> normalized = new ArrayList<>();

        for (String stop : stops.split(",")) {
            String city = stop.trim();

            if (!city.isBlank()) {
                normalized.add(city);
            }
        }

        return String.join(", ", normalized);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
