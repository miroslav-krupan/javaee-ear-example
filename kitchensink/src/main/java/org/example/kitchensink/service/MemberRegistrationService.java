package org.example.kitchensink.service;

import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationService.class);

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher publisher;

    public MemberRegistrationService(MemberRepository memberRepository, ApplicationEventPublisher publisher) {
        this.memberRepository = memberRepository;
        this.publisher = publisher;
    }

    @Transactional
    public void register(Member member) {
        log.info("Registering {}", member.getName());
        memberRepository.save(member);
        publisher.publishEvent(new MemberRegisteredEvent(member));
    }
}
