package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A League friend group: a name and the invite code that lets friends join it. */
@Entity
@Table(name = "lol_groups")
public class LolGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    protected LolGroup() {
    }

    public LolGroup(String name, String inviteCode) {
        this.name = name;
        this.inviteCode = inviteCode;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** The old code stops working when the transaction commits. */
    public void replaceInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }
}
