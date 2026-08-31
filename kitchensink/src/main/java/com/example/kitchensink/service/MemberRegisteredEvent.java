package com.example.kitchensink.service;

import com.example.kitchensink.domain.Member;

public class MemberRegisteredEvent {

    private final Member member;

    public MemberRegisteredEvent(Member member) {
        this.member = member;
    }

    public Member getMember() {
        return member;
    }
}
