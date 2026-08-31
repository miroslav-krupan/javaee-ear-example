package com.example.kitchensink.data;

import com.example.kitchensink.event.MemberRegisteredEvent;
import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.List;

// Source: ejb/src/main/java/.../data/MemberListProducer.java
// Changes: @RequestScoped + @Observes → @Component + @EventListener, @Produces/@Named removed
//          (JSF EL gone; controllers query service directly), constructor injection
@Component
public class MemberListProducer {

    private final MemberRepository memberRepository;
    private List<Member> members;

    public MemberListProducer(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> getMembers() {
        return members;
    }

    // Replaces @Observes(notifyObserver = Reception.IF_EXISTS) — refreshes the cached member list
    @EventListener
    public void onMemberListChanged(MemberRegisteredEvent event) {
        members = memberRepository.findAllOrderedByName();
    }
}
