package com.rideshare.backend.repository;

import com.rideshare.backend.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {
}