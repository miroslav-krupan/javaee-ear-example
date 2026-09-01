package com.example.kitchensink.service;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberRegistration {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistration.class);

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    public void register(Member member) {
        log.info("Registering {}", member.getName());
        memberRepository.save(member);
        eventPublisher.publishEvent(new MemberRegisteredEvent(this, member));
    }
}
