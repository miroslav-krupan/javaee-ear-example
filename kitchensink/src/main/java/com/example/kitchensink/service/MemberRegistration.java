package com.example.kitchensink.service;

import com.example.kitchensink.event.MemberRegisteredEvent;
import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Source: ejb/src/main/java/.../service/MemberRegistration.java
// Changes: @Stateless → @Service, JTA/CMT → @Transactional, em.persist() → repository.save(),
//          CDI Event<Member>.fire() → ApplicationEventPublisher.publishEvent(MemberRegisteredEvent),
//          constructor injection; no EJB/CDI imports remain.
@Service
@Transactional
public class MemberRegistration {

    private static final Logger logger = LoggerFactory.getLogger(MemberRegistration.class);

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    public void register(Member member) throws Exception {
        logger.info("Registering " + member.getName());
        memberRepository.save(member);
        eventPublisher.publishEvent(new MemberRegisteredEvent(member));
    }
}
