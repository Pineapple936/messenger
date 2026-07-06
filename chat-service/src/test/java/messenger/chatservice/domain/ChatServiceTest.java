package messenger.chatservice.domain;

import messenger.chatservice.api.dto.AddUserInChatDto;
import messenger.chatservice.api.dto.CreateChatDto;
import messenger.chatservice.external.UserHttpClient;
import messenger.chatservice.kafka.ChatKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @Mock
    private UserHttpClient userHttpClient;

    @Mock
    private ChatKafkaProducer chatKafkaProducer;

    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(chatRepository, chatParticipantRepository, userHttpClient, chatKafkaProducer);
    }

    @Test
    void createPrivateChatAddsCreatorAndPeerAsOwners() {
        when(userHttpClient.existsUsersById(List.of(2L))).thenReturn(List.of(2L));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Chat chat = service.create(1L, new CreateChatDto("direct", ChatType.PRIVATE, List.of(2L)));

        assertThat(chat.getType()).isEqualTo(ChatType.PRIVATE);
        assertThat(chat.getParticipants()).hasSize(2);
        assertThat(chat.getParticipants())
                .extracting(ChatParticipant::getUserId)
                .containsExactly(1L, 2L);
        assertThat(chat.getParticipants())
                .extracting(ChatParticipant::getRole)
                .containsExactly(ChatRole.OWNER, ChatRole.OWNER);
        assertThat(chat.getParticipants()).allSatisfy(participant -> assertThat(participant.getChat()).isSameAs(chat));
    }

    @Test
    void createPrivateChatRejectsMissingOnlyParticipant() {
        when(userHttpClient.existsUsersById(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(1L, new CreateChatDto("direct", ChatType.PRIVATE, List.of(999L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Participants not found");
    }

    @Test
    void createPrivateChatRejectsMoreThanOneRequestedParticipant() {
        assertThatThrownBy(() -> service.create(1L, new CreateChatDto("direct", ChatType.PRIVATE, List.of(999L, 2L))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Private chat can only have one participant");
    }

    @Test
    void addUserInChatRejectsMemberInviter() {
        Chat chat = new Chat();
        chat.setId(10L);
        chat.setType(ChatType.GROUP);

        ChatParticipant inviter = new ChatParticipant();
        inviter.setUserId(1L);
        inviter.setRole(ChatRole.MEMBER);

        when(userHttpClient.existsUserById(3L)).thenReturn(true);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(chatParticipantRepository.findByChatIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(inviter));

        assertThatThrownBy(() -> service.addUserInChat(1L, new AddUserInChatDto(3L, ChatRole.MEMBER, 10L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Member can not add user");
    }
}
