package com.example.kitchensink.service;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrated from legacy @Stateless EJB MemberRegistration with container-managed transaction (CMT REQUIRED).
 * CMT REQUIRED → @Transactional(propagation = REQUIRED) which is the Spring default.
 *
 * CDI Event&lt;Member&gt; → ApplicationEventPublisher.publishEvent(MemberRegisteredEvent).
 * Reception.IF_EXISTS semantics: the legacy observer only fired when MemberListProducer was
 * already instantiated in the request context (JSF requests). Spring @EventListener fires
 * unconditionally — the Frontend/Thymeleaf controller guards its own list refresh scope.
 */
@Service
@Transactional
public class MemberRegistration {

    private static final Logger logger = LoggerFactory.getLogger(MemberRegistration.class);

    @PersistenceContext
    private EntityManager em;

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(MemberRepository memberRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persists the member and publishes a MemberRegisteredEvent.
     * Throws EmailAlreadyExistsException if the email is already taken.
     */
    public void register(Member member) throws Exception {
        logger.info("Registering {}", member.getName());

        try {
            memberRepository.findByEmail(member.getEmail());
            throw new EmailAlreadyExistsException(member.getEmail());
        } catch (NoResultException e) {
            // email not yet taken — proceed
        }

        em.persist(member);
        eventPublisher.publishEvent(new MemberRegisteredEvent(this, member));
    }
}
