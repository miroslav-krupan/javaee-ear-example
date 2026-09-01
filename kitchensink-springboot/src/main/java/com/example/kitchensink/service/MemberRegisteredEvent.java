package com.example.kitchensink.service;

import com.example.kitchensink.model.Member;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent published after a Member is successfully persisted.
 * Replaces the CDI Event&lt;Member&gt; fired by the legacy @Stateless MemberRegistration EJB.
 */
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
