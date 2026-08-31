package com.example.kitchensink.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.kitchensink.model.Member;

// Source: ejb/src/main/java/.../data/MemberRepository.java
// CDI @ApplicationScoped -> Spring @Repository / JpaRepository.
// findByEmailOptional: Optional return — used by REST service emailAlreadyExists check.
// findByEmail: non-Optional @Query — Spring Data calls getSingleResult() internally;
//   throws EmptyResultDataAccessException on no result (Spring equivalent of NoResultException),
//   throws IncorrectResultSizeDataAccessException on multiple rows (equivalent of NonUniqueResultException).
// findAllOrderedByName: JPQL ORDER BY equivalent to Criteria API sort.
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailOptional(String email);

    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Member findByEmail(String email);

    @Query("SELECT m FROM Member m ORDER BY m.name ASC")
    List<Member> findAllOrderedByName();
}
