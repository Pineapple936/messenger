package messenger.messageservice.domain;

import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageAccessInfoDto;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListRequest;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListResponse;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.external.ChatHttpClient;
import messenger.messageservice.external.MediaHttpClient;
import messenger.messageservice.external.ReactionHttpClient;
import messenger.messageservice.kafka.MessageKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private PinnedMessageRepository pinnedMessageRepository;

    @Mock
    private ChatHttpClient chatHttpClient;

    @Mock
    private ReactionHttpClient reactionHttpClient;

    @Mock
    private MediaHttpClient mediaHttpClient;

    @Mock
    private MessageKafkaProducer messageKafkaProducer;

    @Mock
    private RedisTemplate<String, Boolean> redisTemplate;

    @Mock
    private ValueOperations<String, Boolean> valueOperations;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(
                messageRepository,
                pinnedMessageRepository,
                chatHttpClient,
                reactionHttpClient,
                mediaHttpClient,
                messageKafkaProducer,
                redisTemplate,
                new MessageMapper()
        );
        ReflectionTestUtils.setField(service, "chatUserKeyPattern", "chat:%s:user:%s");
        ReflectionTestUtils.setField(service, "chatUserKeyTtlMinutes", 5);
    }

    @Test
    void saveAndPublishForwardedMessageReferencesOriginalSource() {
        Message original = message("original", 10L, 1L, "original text", List.of("photo-a"));
        Message forwarded = message("forwarded", 20L, 2L, null, null);
        forwarded.setForwardedMessageId(original.getId());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:30:user:3")).thenReturn(true);
        when(valueOperations.get("chat:20:user:3")).thenReturn(true);
        when(messageRepository.findById(forwarded.getId())).thenReturn(Optional.of(forwarded));
        when(messageRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId("saved");
            return saved;
        });

        MessageResponse response = service.saveAndPublish(MessageDto.builder()
                .chatId(30L)
                .userId(3L)
                .readStatus(false)
                .editStatus(false)
                .sendAt(NOW)
                .forwardedFromMessageId(forwarded.getId())
                .build());

        ArgumentCaptor<Message> savedCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getForwardedMessageId()).isEqualTo(original.getId());
        assertThat(savedCaptor.getValue().getContent()).isNull();
        assertThat(savedCaptor.getValue().getPhotoLinks()).isNull();
        assertThat(response.content()).isEqualTo(original.getContent());
        assertThat(response.photoLinks()).containsExactly("photo-a");
        assertThat(response.forwardedMessage().userId()).isEqualTo(original.getUserId());
    }

    @Test
    void getSliceLoadsReplyAndForwardReferencesInOneBatch() {
        Message reply = message("reply", 10L, 2L, "reply text", null);
        Message source = message("source", 20L, 3L, "source text", List.of("source-photo"));
        Message item = message("item", 10L, 1L, null, null);
        item.setRepliedMessageId(reply.getId());
        item.setForwardedMessageId(source.getId());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);
        when(messageRepository.findByChatIdOrderBySendAtDescIdDesc(10L, PageRequest.of(0, 50)))
                .thenReturn(new SliceImpl<>(List.of(item), PageRequest.of(0, 50), false));
        when(messageRepository.findAllById(Set.of(reply.getId(), source.getId()))).thenReturn(List.of(reply, source));
        when(reactionHttpClient.getReactions(any(ReactionsOnMessageListRequest.class)))
                .thenReturn(new ReactionsOnMessageListResponse(Map.of()));

        Slice<MessageResponse> result = service.getSlice(1L, 10L, 50, 0);

        MessageResponse response = result.getContent().getFirst();
        assertThat(response.content()).isEqualTo(source.getContent());
        assertThat(response.photoLinks()).containsExactly("source-photo");
        assertThat(response.repliedMessage().id()).isEqualTo(reply.getId());
        assertThat(response.forwardedMessage().userId()).isEqualTo(source.getUserId());
        verify(messageRepository).findAllById(Set.of(reply.getId(), source.getId()));
    }

    @Test
    void getSliceHandlesMessagesWithoutReplyOrForwardReferences() {
        Message item = message("item", 10L, 1L, "plain text", null);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);
        when(messageRepository.findByChatIdOrderBySendAtDescIdDesc(10L, PageRequest.of(0, 50)))
                .thenReturn(new SliceImpl<>(List.of(item), PageRequest.of(0, 50), false));
        when(reactionHttpClient.getReactions(any(ReactionsOnMessageListRequest.class)))
                .thenReturn(new ReactionsOnMessageListResponse(Map.of()));

        Slice<MessageResponse> result = service.getSlice(1L, 10L, 50, 0);

        MessageResponse response = result.getContent().getFirst();
        assertThat(response.content()).isEqualTo("plain text");
        assertThat(response.repliedMessage()).isNull();
        assertThat(response.forwardedMessage()).isNull();
        verify(messageRepository, never()).findAllById(any());
    }

    @Test
    void getMessageAccessInfoReturnsChatIdWhenUserIsChatMember() {
        Message message = message("message-1", 10L, 2L, "text", null);

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);

        MessageAccessInfoDto result = service.getMessageAccessInfo(1L, "message-1");

        assertThat(result.messageId()).isEqualTo("message-1");
        assertThat(result.chatId()).isEqualTo(10L);
    }

    @Test
    void getMessageAccessInfoRejectsUserOutsideChat() {
        Message message = message("message-1", 10L, 2L, "text", null);

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(false);
        when(chatHttpClient.hasUser(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMessageAccessInfo(1L, "message-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chat not found or user is not a member");
    }

    @Test
    void pinMessageSavesPinAndPublishesGatewayEvent() {
        Message message = message("message-1", 10L, 2L, "text", null);

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);
        service.pinMessageById(1L, "message-1");

        verify(pinnedMessageRepository).save(any(PinnedMessage.class));
        verify(messageKafkaProducer).sendPinEvent(argThat(event ->
                event.chatId().equals(10L)
                        && event.messageId().equals("message-1")
                        && event.messageSendAt().equals(message.getSendAt())
                        && event.pinnedByUserId().equals(1L)
        ));
    }

    @Test
    void pinMessageRejectsAlreadyPinnedMessage() {
        Message message = message("message-1", 10L, 2L, "text", null);

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);
        when(pinnedMessageRepository.existsByMessageId("message-1")).thenReturn(true);

        assertThatThrownBy(() -> service.pinMessageById(1L, "message-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("Message is already pinned");

        verify(pinnedMessageRepository, never()).save(any(PinnedMessage.class));
        verify(messageKafkaProducer, never()).sendPinEvent(any());
    }

    @Test
    void unpinMessageDeletesPinAndPublishesGatewayEvent() {
        Message pinnedSource = message("message-1", 10L, 2L, "text", null);
        PinnedMessage pinnedMessage = new PinnedMessage(pinnedSource, 2L);

        when(pinnedMessageRepository.findByMessageId("message-1")).thenReturn(Optional.of(pinnedMessage));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:10:user:1")).thenReturn(true);

        service.unpinnedMessageById(1L, "message-1");

        verify(pinnedMessageRepository).delete(pinnedMessage);
        verify(messageKafkaProducer).sendUnpinEvent(argThat(event ->
                event.chatId().equals(10L)
                        && event.messageId().equals("message-1")
                        && event.messageSendAt().equals(pinnedSource.getSendAt())
                        && event.pinnedByUserId().equals(1L)
        ));
    }

    @Test
    void deleteByIdDeletesOnlyMediaLinksThatAreNoLongerReferenced() {
        Message deleted = message("deleted", 10L, 1L, "with media", List.of("keep", "remove"));
        Message remaining = message("remaining", 10L, 2L, "still uses media", List.of("keep"));

        when(messageRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(messageRepository.findByPhotoLinksIn(List.of("keep", "remove"))).thenReturn(List.of(remaining));
        when(pinnedMessageRepository.findByMessageId(deleted.getId())).thenReturn(Optional.empty());

        service.deleteById(1L, deleted.getId());

        verify(messageRepository).deleteById(deleted.getId());
        verify(mediaHttpClient).deleteByListName(List.of("remove"));
    }

    @Test
    void deleteByIdDoesNotDeleteMediaForForwardedMessage() {
        Message forwarded = message("forwarded", 10L, 1L, null, null);
        forwarded.setForwardedMessageId("source");

        when(messageRepository.findById(forwarded.getId())).thenReturn(Optional.of(forwarded));
        when(pinnedMessageRepository.findByMessageId(forwarded.getId())).thenReturn(Optional.empty());

        service.deleteById(1L, forwarded.getId());

        verify(messageRepository).deleteById(forwarded.getId());
        verify(messageRepository, never()).findByPhotoLinksIn(any());
        verify(mediaHttpClient, never()).deleteByListName(any());
    }

    @Test
    void deleteByIdUnpinsDeletedMessage() {
        Message deleted = message("deleted", 10L, 1L, "text", null);
        PinnedMessage pinnedMessage = new PinnedMessage(deleted, 1L);

        when(messageRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(pinnedMessageRepository.findByMessageId(deleted.getId())).thenReturn(Optional.of(pinnedMessage));

        service.deleteById(1L, deleted.getId());

        verify(pinnedMessageRepository).delete(pinnedMessage);
        verify(messageKafkaProducer).sendUnpinEvent(argThat(event ->
                event.chatId().equals(10L)
                        && event.messageId().equals("deleted")
                        && event.messageSendAt().equals(deleted.getSendAt())
                        && event.pinnedByUserId().equals(1L)
        ));
    }

    private static Message message(String id, Long chatId, Long userId, String content, List<String> photoLinks) {
        Message message = new Message();
        message.setId(id);
        message.setChatId(chatId);
        message.setUserId(userId);
        message.setContent(content);
        message.setPhotoLinks(photoLinks);
        message.setReadStatus(false);
        message.setEditStatus(false);
        message.setSendAt(NOW);
        return message;
    }
}
