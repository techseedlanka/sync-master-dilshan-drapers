package com.dilshandrapers.syncmaster.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_logs")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "EmpId")
    private String empId;

    @Column(name = "AuthDateAndTime")
    private LocalDateTime authDateAndTime;

    @Column(name = "AuthDate")
    private LocalDate authDate;

    @Column(name = "AuthTime")
    private LocalTime authTime;

    @Column(name = "Direction")
    private String direction;

    @Column(name = "Device")
    private String device;

    @Column(name = "Serial")
    private String serial;

    @Column(name = "Person")
    private String person;

    @Column(name = "Card")
    private String card;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmpId() {
        return empId;
    }

    public void setEmpId(String empId) {
        this.empId = empId;
    }

    public LocalDateTime getAuthDateAndTime() {
        return authDateAndTime;
    }

    public void setAuthDateAndTime(LocalDateTime authDateAndTime) {
        this.authDateAndTime = authDateAndTime;
    }

    public LocalDate getAuthDate() {
        return authDate;
    }

    public void setAuthDate(LocalDate authDate) {
        this.authDate = authDate;
    }

    public LocalTime getAuthTime() {
        return authTime;
    }

    public void setAuthTime(LocalTime authTime) {
        this.authTime = authTime;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public String getCard() {
        return card;
    }

    public void setCard(String card) {
        this.card = card;
    }
}
