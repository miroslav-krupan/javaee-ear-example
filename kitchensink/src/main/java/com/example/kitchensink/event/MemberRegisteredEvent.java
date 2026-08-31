package com.example.kitchensink.event;

import com.example.kitchensink.model.Member;

// Replaces CDI Event<Member> fired in MemberRegistration.register()
public class MemberRegisteredEvent {

    private final Member member;

    public MemberRegisteredEvent(Member member) {
        this.member = member;
    }

    public Member getMember() {
        return member;
    }
}
