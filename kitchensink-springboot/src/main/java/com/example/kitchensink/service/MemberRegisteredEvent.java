package com.example.kitchensink.service;

import com.example.kitchensink.domain.Member;
import org.springframework.context.ApplicationEvent;

public class MemberRegisteredEvent extends ApplicationEvent {

    private final Member member;

    public MemberRegisteredEvent(Object source, Member member) {
        super(source);
        this.member = member;
    }

    public Member getMember() {
        return member;
    }
}
