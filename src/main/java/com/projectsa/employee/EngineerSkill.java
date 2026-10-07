package com.projectsa.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A Tech Engineer's skill/certificate, e.g. "CCNA". */
@Entity
@Table(name = "engineer_skill")
public class EngineerSkill {

    /** Column length of {@code engineer_skill.skill}. */
    public static final int MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, length = 100)
    private String skill;

    protected EngineerSkill() {
    }

    EngineerSkill(Employee employee, String skill) {
        this.employee = employee;
        this.skill = skill;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getSkill() {
        return skill;
    }
}
