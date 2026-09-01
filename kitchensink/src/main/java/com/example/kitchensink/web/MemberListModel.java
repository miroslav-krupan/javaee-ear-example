package com.example.kitchensink.web;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegisteredEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MemberListModel {

    private final MemberRepository memberRepository;
    private List<Member> members;

    public MemberListModel(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @PostConstruct
    public void init() {
        members = memberRepository.findAllByOrderByNameAsc();
    }

    @EventListener
    public void onMemberRegistered(MemberRegisteredEvent event) {
        members = memberRepository.findAllByOrderByNameAsc();
    }

    public List<Member> getMembers() {
        return members;
    }
}
