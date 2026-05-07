package com.grabmyseat.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attendees")
public class Attendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false)
    private String email;

    protected Attendee() {
    }

    public Attendee(Long ownerUserId, String name, Integer age, String mobile, String email) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.age = age;
        this.mobile = mobile;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
