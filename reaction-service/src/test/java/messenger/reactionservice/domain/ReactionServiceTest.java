package messenger.reactionservice.domain;

import messenger.commonlibs.dto.messageservice.MessageAccessInfoDto;
import messenger.commonlibs.dto.reactionservice.ReactionType;
import messenger.reactionservice.api.dto.CreateReactionDto;
import messenger.reactionservice.external.MessageHttpClient;
import messenger.reactionservice.kafka.ReactionKafkaProducer;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {
    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private ReactionDetailsRepository detailsRepository;

    @Mock
    private MessageHttpClient messageHttpClient;

    @Mock
    private ReactionKafkaProducer reactionKafkaProducer;

    private ReactionService service;

    @BeforeEach
    void setUp() {
        service = new ReactionService(reactionRepository, detailsRepository, messageHttpClient, reactionKafkaProducer);
    }

    @Test
    void addIncrementsExistingReactionCounter() {
        CreateReactionDto dto = new CreateReactionDto("message-1", ReactionType.LIKE);
        Reaction existing = reaction(10L, "message-1", ReactionType.LIKE, 3L);

        when(messageHttpClient.getMessageAccessInfo("message-1", 1L))
                .thenReturn(new MessageAccessInfoDto("message-1", 10L));
        when(detailsRepository.findByUserIdAndMessageIdForUpdate(1L, "message-1")).thenReturn(List.of());
        when(reactionRepository.upsertAndIncrementReturningId(10L, "message-1", ReactionType.LIKE.name()))
                .thenReturn(100L);
        when(reactionRepository.findById(100L)).thenReturn(Optional.of(existing));

        Reaction result = service.add(1L, dto);

        assertThat(result.getCount()).isEqualTo(3L);
        verify(detailsRepository).save(any(ReactionDetails.class));
        verify(reactionKafkaProducer).sendReactionEvent(any());
    }

    @Test
    void addRejectsMoreThanThreeUserReactionsForMessage() {
        CreateReactionDto dto = new CreateReactionDto("message-1", ReactionType.LIKE);

        when(messageHttpClient.getMessageAccessInfo("message-1", 1L))
                .thenReturn(new MessageAccessInfoDto("message-1", 10L));
        when(detailsRepository.findByUserIdAndMessageIdForUpdate(1L, "message-1"))
                .thenReturn(List.of(
                        details(ReactionType.LIKE),
                        details(ReactionType.LOVE),
                        details(ReactionType.LAUGH)
                ));

        assertThatThrownBy(() -> service.add(1L, dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Max 3 reactions");
    }

    @Test
    void addDoesNotCreateReactionWhenUserCannotAccessMessage() {
        CreateReactionDto dto = new CreateReactionDto("message-1", ReactionType.LIKE);

        when(messageHttpClient.getMessageAccessInfo("message-1", 1L))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.add(1L, dto))
                .isInstanceOf(ResponseStatusException.class);

        verify(detailsRepository, never()).save(any());
        verify(reactionRepository, never()).save(any());
        verify(reactionRepository, never()).upsertAndIncrementReturningId(any(), any(), any());
        verify(reactionKafkaProducer, never()).sendReactionEvent(any());
    }

    private static Reaction reaction(Long chatId, String messageId, ReactionType type, Long count) {
        Reaction reaction = new Reaction();
        reaction.setChatId(chatId);
        reaction.setMessageId(messageId);
        reaction.setReactionType(type);
        reaction.setCount(count);
        return reaction;
    }

    private static ReactionDetails details(ReactionType type) {
        ReactionDetails details = new ReactionDetails();
        details.setReactionType(type);
        details.setMessageId("message-1");
        details.setUserId(1L);
        details.setChatId(10L);
        return details;
    }
}
