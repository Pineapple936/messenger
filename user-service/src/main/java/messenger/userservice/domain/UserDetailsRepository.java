package messenger.userservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDetailsRepository extends JpaRepository<UserDetails, Long> {
    Optional<UserDetails> findByUserId(Long userId);

    Boolean existsUserDetailsByUserId(Long userId);

    void deleteByUserId(Long userId);

    List<UserDetails> findTop10ByTagIsContainingIgnoreCase(String tag);
}
