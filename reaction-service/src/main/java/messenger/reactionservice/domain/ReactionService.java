package messenger.reactionservice.domain;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.ReactionOnMessage;
import messenger.commonlibs.dto.reactionservice.GatewayReactionEventDto;
import messenger.reactionservice.api.dto.CreateReactionDto;
import messenger.reactionservice.external.MessageHttpClient;
import messenger.reactionservice.kafka.ReactionKafkaProducer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {
    private final ReactionRepository reactionRepository;
    private final ReactionDetailsRepository detailsRepository;
    private final MessageHttpClient messageHttpClient;
    private final ReactionKafkaProducer reactionKafkaProducer;

    @Transactional
    public Reaction add(Long userId, CreateReactionDto dto) {
        List<ReactionDetails> existing = detailsRepository.findByUserIdAndMessageIdForUpdate(userId, dto.messageId());
        if (existing.size() >= 3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Max 3 reactions userId=" + userId + " messageId=" + dto.messageId());
        }
        boolean alreadyReacted = existing.stream()
                .anyMatch(r -> r.getReactionType() == dto.reactionType());
        if (alreadyReacted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists");
        }

        try {
            detailsRepository.save(new ReactionDetails(userId, dto));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists", e);
        }

        Reaction reaction = reactionRepository.findByMessageIdAndReactionType(
                dto.messageId(), dto.reactionType()).orElse(null);

        if(reaction != null) {
            reaction.setCount(reaction.getCount() + 1);
            Reaction updated = reactionRepository.save(reaction);
            reactionKafkaProducer.sendReactionEvent(
                    GatewayReactionEventDto.reactionAdded(dto.chatId(), dto.messageId(), userId, dto.reactionType().name())
            );
            return updated;
        }

        Reaction saved = reactionRepository.save(new Reaction(dto));
        reactionKafkaProducer.sendReactionEvent(
                GatewayReactionEventDto.reactionAdded(dto.chatId(), dto.messageId(), userId, dto.reactionType().name())
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Set<ReactionOnMessage>> batchReactionsByUserIdAndMessageIds(Long userId, List<String> messageIds) {
        List<Reaction> reactions = reactionRepository.findByMessageIdIn(messageIds);

        Set<String> userReactionKeys = detailsRepository.findByUserIdAndMessageIdIn(userId, messageIds)
                .stream()
                .map(d -> d.getMessageId() + ":" + d.getReactionType().name())
                .collect(Collectors.toSet());

        Map<String, Set<ReactionOnMessage>> result = reactions.stream()
                .collect(Collectors.groupingBy(
                        Reaction::getMessageId,
                        Collectors.mapping(
                                r -> new ReactionOnMessage(
                                        r.getReactionType(),
                                        r.getCount(),
                                        userReactionKeys.contains(r.getMessageId() + ":" + r.getReactionType().name())
                                ),
                                Collectors.toSet()
                        )
                ));

        messageIds.forEach(id -> result.putIfAbsent(id, Set.of()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Reaction> getByMessageId(String messageId) {
        return reactionRepository.findByMessageId(messageId);
    }

    @Transactional(readOnly = true)
    public Set<messenger.commonlibs.dto.reactionservice.ReactionType> getReactionTypesByUserIdAndMessageId(Long userId, @NotBlank String messageId) {
        return detailsRepository.findByUserIdAndMessageId(userId, messageId)
                .stream()
                .map(ReactionDetails::getReactionType)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void deleteByMessageIdAndReactionType(Long userId, String messageId, messenger.commonlibs.dto.reactionservice.ReactionType reactionType) {
        ReactionDetails details = detailsRepository.findByUserIdAndMessageIdAndReactionType(userId, messageId, reactionType)
                .orElseThrow(() -> new EntityNotFoundException("Reaction details with userId=" + userId + " messageId=" + messageId + " reactionType=" + reactionType));
        detailsRepository.delete(details);
        Reaction reaction = reactionRepository.findByMessageIdAndReactionType(messageId, reactionType)
                .orElseThrow(() -> new EntityNotFoundException("Reaction with messageId=" + messageId + " reactionType=" + reactionType));
        if(reaction.getCount() == 1) {
            reactionRepository.delete(reaction);
        } else {
            reaction.setCount(reaction.getCount() - 1);
            reactionRepository.save(reaction);
        }
        reactionKafkaProducer.sendReactionEvent(
                GatewayReactionEventDto.reactionDeleted(reaction.getChatId(), messageId, userId, reactionType.name())
        );
    }

    @Transactional
    public void deleteMessageByIdWithoutCheckOwnerUser(String messageId) {
        detailsRepository.deleteByMessageId(messageId);
        reactionRepository.deleteByMessageId(messageId);
    }

    @Transactional
    public void deleteByChatId(Long chatId) {
        detailsRepository.deleteByChatId(chatId);
        reactionRepository.deleteByChatId(chatId);
    }

    @Transactional
    public void deleteMessageById(Long userId, String messageId) {
        if(!messageHttpClient.isMessageOwner(userId, messageId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only user can delete this message");
        }
        deleteMessageByIdWithoutCheckOwnerUser(messageId);
    }
}
