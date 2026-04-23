package messenger.userservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.userservice.CreateUserDto;
import messenger.commonlibs.dto.userservice.DeleteUserDto;
import messenger.userservice.api.dto.EditUserDto;
import messenger.userservice.external.AuthHttpClient;
import messenger.userservice.kafka.UserKafkaProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class UserDetailsService {
    private final UserDetailsRepository userDetailsRepository;
    private final UserKafkaProducer userKafkaProducer;
    private final AuthHttpClient authHttpClient;

    @Transactional
    public UserDetails save(CreateUserDto dto) {
        return userDetailsRepository.save(new UserDetails(dto));
    }

    @Transactional(readOnly = true)
    public UserDetails getByUserId(Long userId) {
        return userDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public Boolean existsUserById(Long userId) {
        return userDetailsRepository.existsUserDetailsByUserId(userId);
    }

    @Transactional
    public UserDetails editUser(Long userId, EditUserDto dto) {
        UserDetails userDetails = getByUserId(userId);

        if(dto.name() != null) {
            userDetails.setName(dto.name());
        }

        if(dto.description() != null) {
            userDetails.setDescription(dto.description());
        }

        return userDetailsRepository.save(userDetails);
    }

    @Transactional
    public void deleteById(Long userId, String authorizationHeader) {
        authHttpClient.deleteUserCredentialById(userId, authorizationHeader);
        userDetailsRepository.deleteByUserId(userId);
        userKafkaProducer.sendMessageToKafka(new DeleteUserDto(userId));
    }
}
