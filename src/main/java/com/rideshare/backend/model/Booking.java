package com.rideshare.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(nullable = false)
    private int seats;

    @Column(name = "total_price", nullable = false)
    private double totalPrice;

    public Booking() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}