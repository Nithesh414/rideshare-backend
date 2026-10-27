
package com.rideshare.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RouteService {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Point> cache = new ConcurrentHashMap<>();
    private final Map<String, String> reverseCache = new ConcurrentHashMap<>();

    private long lastRequestTime = 0;

    public Map<String, Object> calculateRoute(String from, String to) {
        if (blank(from) || blank(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pickup and destination are required"
            );
        }

        Point start = geocode(from.trim());
        Point end = geocode(to.trim());

        String url = "https://router.project-osrm.org/route/v1/driving/"
                + start.lon + "," + start.lat + ";"
                + end.lon + "," + end.lat
                + "?overview=full&geometries=geojson";

        try {
            JsonNode response = rest.getForObject(url, JsonNode.class);

            if (response == null ||
                    !"Ok".equals(response.path("code").asText())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Driving route not found"
                );
            }

            JsonNode route = response.path("routes").get(0);

            if (route == null || route.isMissingNode()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No route found"
                );
            }

            double distanceKm = route.path("distance").asDouble() / 1000.0;
            double durationMinutes = route.path("duration").asDouble() / 60.0;

            List<List<Double>> geometry = new ArrayList<>();
            JsonNode coordinates = route.path("geometry").path("coordinates");

            for (JsonNode point : coordinates) {
                if (point.size() >= 2) {
                    geometry.add(Arrays.asList(
                            point.get(0).asDouble(),
                            point.get(1).asDouble()
                    ));
                }
            }

            List<String> stops = discoverPlaces(geometry);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("from", from.trim());
            result.put("to", to.trim());
            result.put("startCoordinates", Map.of(
                    "latitude", start.lat,
                    "longitude", start.lon
            ));
            result.put("endCoordinates", Map.of(
                    "latitude", end.lat,
                    "longitude", end.lon
            ));
            result.put("distanceKm", Math.round(distanceKm * 10.0) / 10.0);
            result.put("durationMinutes", Math.round(durationMinutes));
            result.put("geometry", geometry);
            result.put("suggestedStops", stops);
            result.put("attribution", "© OpenStreetMap contributors");

            return result;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Routing service failed. Please try again."
            );
        }
    }

    private Point geocode(String place) {
        String key = place.toLowerCase(Locale.ROOT);

        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        URI uri = UriComponentsBuilder
                .fromUriString("https://nominatim.openstreetmap.org/search")
                .queryParam("q", place + ", India")
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .build()
                .encode()
                .toUri();

        try {
            JsonNode results = nominatim(uri);

            if (results == null || !results.isArray() || results.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Location not found: " + place
                );
            }

            JsonNode first = results.get(0);

            Point point = new Point(
                    first.path("lat").asDouble(),
                    first.path("lon").asDouble()
            );

            cache.put(key, point);
            return point;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Geocoding service failed"
            );
        }
    }

    private List<String> discoverPlaces(List<List<Double>> geometry) {
        if (geometry.size() < 4) {
            return Collections.emptyList();
        }

        Set<String> places = new LinkedHashSet<>();

        // Sample 3 positions on the calculated road route.
        for (int percent : new int[]{25, 50, 75}) {
            int index = (geometry.size() - 1) * percent / 100;

            double lon = geometry.get(index).get(0);
            double lat = geometry.get(index).get(1);

            String name = reverseGeocode(lat, lon);

            if (name != null && !name.isBlank()) {
                places.add(name);
            }
        }

        return new ArrayList<>(places);
    }

    private String reverseGeocode(double lat, double lon) {
        String key = String.format(Locale.ROOT, "%.3f,%.3f", lat, lon);

        if (reverseCache.containsKey(key)) {
            return reverseCache.get(key);
        }

        URI uri = UriComponentsBuilder
                .fromUriString("https://nominatim.openstreetmap.org/reverse")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("format", "jsonv2")
                .build()
                .encode()
                .toUri();

        try {
            JsonNode result = nominatim(uri);
            JsonNode address = result == null ? null : result.path("address");

            if (address == null || address.isMissingNode()) {
                return null;
            }

            String name = firstNonBlank(
                    address.path("city").asText(""),
                    address.path("town").asText(""),
                    address.path("village").asText(""),
                    address.path("municipality").asText(""),
                    address.path("county").asText(""),
                    address.path("state_district").asText("")
            );

            if (name != null) {
                reverseCache.put(key, name);
            }

            return name;

        } catch (Exception e) {
            return null;
        }
    }

    private synchronized JsonNode nominatim(URI uri) throws Exception {
        long elapsed = System.currentTimeMillis() - lastRequestTime;
        long wait = 1100 - elapsed;

        if (wait > 0) {
            Thread.sleep(wait);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(
                "User-Agent",
                "RideShareStudentApp/1.0 (contact: replace-with-your-email)"
        );

        ResponseEntity<String> response = rest.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        lastRequestTime = System.currentTimeMillis();

        return mapper.readTree(response.getBody());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static class Point {
        final double lat;
        final double lon;

        Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
}
