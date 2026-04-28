package messenger.userservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserDetailsRepository extends JpaRepository<UserDetails, Long> {
    Optional<UserDetails> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    List<UserDetails> findTop10ByTagIsContainingIgnoreCase(String tag);

    @Query("SELECT u.userId FROM UserDetails u WHERE u.userId IN :usersId")
    List<Long> findExistingIds(List<Long> usersId);

    Boolean existsByUserId(Long userId);
}
