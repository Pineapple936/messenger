package messenger.authenticationservice.domain.security;

import lombok.RequiredArgsConstructor;
import messenger.authenticationservice.domain.UserCredentialRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserCredentialRepository userCredentialRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String id) throws UsernameNotFoundException {
        log.debug("Loading user by id={}", id);
        return userCredentialRepository.findById(Long.parseLong(id)).orElseThrow(
                () -> {
                    log.warn("User with id={} not found", id);
                    return new UsernameNotFoundException("User with id " + id + " not found");
                }
        );
    }
}
