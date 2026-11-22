package com.bw.jAncestryChart.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A family ({@code FAM} record): up to two partners and their children. */
public final class Family {

    private final String id;
    private final LifeEvent marriage;
    private final boolean divorced;
    Person husband;
    Person wife;
    final List<Person> children = new ArrayList<>();

    Family(String id, LifeEvent marriage, boolean divorced) {
        this.id = id;
        this.marriage = marriage;
        this.divorced = divorced;
    }

    public String id() {
        return id;
    }

    /** The partner recorded as {@code HUSB}, or {@code null}. */
    public Person husband() {
        return husband;
    }

    /** The partner recorded as {@code WIFE}, or {@code null}. */
    public Person wife() {
        return wife;
    }

    public List<Person> children() {
        return Collections.unmodifiableList(children);
    }

    /** The marriage event, or {@code null}. */
    public LifeEvent marriage() {
        return marriage;
    }

    public boolean isDivorced() {
        return divorced;
    }

    /** The other partner of {@code person} in this family, or {@code null}. */
    public Person partnerOf(Person person) {
        if (person == husband) {
            return wife;
        }
        if (person == wife) {
            return husband;
        }
        return null;
    }

    @Override
    public String toString() {
        return id + " (" + husband + " & " + wife + ")";
    }
}
