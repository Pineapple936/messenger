package messenger.userservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.userservice.CreateUserDto;
import messenger.commonlibs.dto.userservice.DeleteUserDto;
import messenger.userservice.api.dto.EditAvatarDto;
import messenger.userservice.api.dto.EditUserDto;
import messenger.userservice.external.AuthHttpClient;
import messenger.userservice.external.MediaHttpClient;
import messenger.userservice.kafka.UserKafkaProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class UserDetailsService {
    private final UserDetailsRepository userDetailsRepository;
    private final UserKafkaProducer userKafkaProducer;
    private final AuthHttpClient authHttpClient;
    private final MediaHttpClient mediaHttpClient;

    @Transactional
    public UserDetails save(CreateUserDto dto) {
        return userDetailsRepository.save(new UserDetails(dto));
    }

    @Transactional(readOnly = true)
    public UserDetails findByUserId(Long userId) {
        return userDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public Boolean existsUserById(Long userId) {
        return userDetailsRepository.existsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Long> existsUsersById(List<Long> usersId) {
        return userDetailsRepository.findExistingIds(usersId);
    }

    @Transactional(readOnly = true)
    public List<UserDetails> searchUsersByTag(String tag) {
        return userDetailsRepository.findTop10ByTagIsContainingIgnoreCase(tag);
    }

    @Transactional
    public UserDetails editUser(Long userId, EditUserDto dto) {
        UserDetails userDetails = findByUserId(userId);

        if(dto.name() != null) {
            userDetails.setName(dto.name());
        }

        if(dto.tag() != null && dto.tag().length() >= 3) {
            userDetails.setTag(dto.tag());
        }

        if(dto.description() != null) {
            userDetails.setDescription(dto.description());
        }

        return userDetailsRepository.save(userDetails);
    }

    @Transactional
    public UserDetails editAvatar(Long userId, EditAvatarDto dto) {
        UserDetails userDetails = findByUserId(userId);

        if(dto.newUrl() == null) {
            mediaHttpClient.deleteByFileName(userDetails.getAvatarUrl());
        }

        userDetails.setAvatarUrl(dto.newUrl());
        return userDetailsRepository.save(userDetails);
    }

    @Transactional
    public void deleteById(Long userId, String authorizationHeader) {
        authHttpClient.deleteUserCredentialById(userId, authorizationHeader);
        userDetailsRepository.deleteByUserId(userId);
        userKafkaProducer.sendMessageToKafka(new DeleteUserDto(userId));
    }
}
