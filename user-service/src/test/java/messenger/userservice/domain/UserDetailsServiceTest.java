package messenger.userservice.domain;

import messenger.userservice.api.dto.EditAvatarDto;
import messenger.userservice.api.dto.EditUserDto;
import messenger.userservice.external.AuthHttpClient;
import messenger.userservice.external.MediaHttpClient;
import messenger.userservice.kafka.UserKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceTest {
    @Mock
    private UserDetailsRepository userDetailsRepository;

    @Mock
    private UserKafkaProducer userKafkaProducer;

    @Mock
    private AuthHttpClient authHttpClient;

    @Mock
    private MediaHttpClient mediaHttpClient;

    private UserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsService(userDetailsRepository, userKafkaProducer, authHttpClient, mediaHttpClient);
    }

    @Test
    void editUserIgnoresTooShortTagButUpdatesOtherFields() {
        UserDetails user = user();
        when(userDetailsRepository.findByUserId(7L)).thenReturn(Optional.of(user));
        when(userDetailsRepository.save(user)).thenReturn(user);

        UserDetails result = service.editUser(7L, new EditUserDto("New Name", "ab", "new description"));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getTag()).isEqualTo("oldtag");
        assertThat(result.getDescription()).isEqualTo("new description");
    }

    @Test
    void editAvatarDeletesOldAvatarWhenNewUrlIsNull() {
        UserDetails user = user();
        user.setAvatarUrl("old-avatar");
        when(userDetailsRepository.findByUserId(7L)).thenReturn(Optional.of(user));
        when(userDetailsRepository.save(user)).thenReturn(user);

        UserDetails result = service.editAvatar(7L, new EditAvatarDto(null));

        assertThat(result.getAvatarUrl()).isNull();
        verify(mediaHttpClient).deleteByFileName("old-avatar");
    }

    private static UserDetails user() {
        UserDetails user = new UserDetails();
        user.setUserId(7L);
        user.setName("Old Name");
        user.setTag("oldtag");
        user.setDescription("old description");
        return user;
    }
}
