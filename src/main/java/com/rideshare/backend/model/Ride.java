
package com.rideshare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rides")
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_city", nullable = false)
    private String from;

    @Column(name = "to_city", nullable = false)
    private String to;

    @Column(name = "route_stops", length = 1000)
    private String routeStops = "";

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private String time;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private double price;

    @Column(name = "driver_name", nullable = false)
    private String driverName;

    public Ride() {
    }

    public Ride(
            String from,
            String to,
            String routeStops,
            String date,
            String time,
            int seats,
            double price,
            String driverName
    ) {
        this.from = from;
        this.to = to;
        this.routeStops = routeStops == null ? "" : routeStops;
        this.date = date;
        this.time = time;
        this.seats = seats;
        this.price = price;
        this.driverName = driverName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getRouteStops() {
        return routeStops;
    }

    public void setRouteStops(String routeStops) {
        this.routeStops = routeStops == null ? "" : routeStops.trim();
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getSeats() {
        return seats;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }
}
