package com.example.kitchensink.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.NoResultException;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.kitchensink.model.Member;

// Source: ejb/src/main/java/.../data/MemberRepository.java
// CDI @ApplicationScoped -> Spring @Repository / JpaRepository;
// findByEmail preserves NoResultException semantics via Optional + explicit throw;
// findAllOrderedByName uses JPQL ORDER BY preserving the Criteria API sort.
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailOptional(String email);

    default Member findByEmail(String email) {
        return findByEmailOptional(email).orElseThrow(NoResultException::new);
    }

    @Query("SELECT m FROM Member m ORDER BY m.name ASC")
    List<Member> findAllOrderedByName();
}
