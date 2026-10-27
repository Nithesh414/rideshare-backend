
package com.rideshare.backend.controller;

import com.rideshare.backend.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestParam String from,
            @RequestParam String to
    ) {
        return ResponseEntity.ok(
                routeService.calculateRoute(from, to)
        );
    }
}
