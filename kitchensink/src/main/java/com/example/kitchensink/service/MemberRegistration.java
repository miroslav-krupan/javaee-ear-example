package com.example.kitchensink.service;

import com.example.kitchensink.event.MemberRegisteredEvent;
import com.example.kitchensink.model.Member;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Source: ejb/src/main/java/.../service/MemberRegistration.java
// Changes: @Stateless → @Service, JTA/CMT → @Transactional, CDI Event<Member>.fire() →
//          ApplicationEventPublisher.publishEvent(MemberRegisteredEvent), constructor injection
@Service
@Transactional
public class MemberRegistration {

    private static final Logger logger = LoggerFactory.getLogger(MemberRegistration.class);

    private final EntityManager em;
    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(EntityManager em, ApplicationEventPublisher eventPublisher) {
        this.em = em;
        this.eventPublisher = eventPublisher;
    }

    public void register(Member member) throws Exception {
        logger.info("Registering " + member.getName());
        em.persist(member);
        eventPublisher.publishEvent(new MemberRegisteredEvent(member));
    }
}
