package messenger.messageservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.*;
import messenger.messageservice.api.dto.MessageEditDto;
import messenger.messageservice.api.dto.MessageListResponse;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.external.MediaHttpClient;
import messenger.messageservice.external.ReactionHttpClient;
import messenger.messageservice.kafka.MessageKafkaProducer;
import messenger.messageservice.external.ChatHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final int DEFAULT_MESSAGES_LIMIT = 50;

    private final MessageRepository messageRepository;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final ChatHttpClient chatHttpClient;
    private final ReactionHttpClient reactionHttpClient;
    private final MediaHttpClient mediaHttpClient;
    private final MessageKafkaProducer messageKafkaProducer;
    private final RedisTemplate<String, Boolean> redisTemplate;
    private final MessageMapper messageMapper;

    @Value("${redis.chat.user.ttl.minute}")
    private int chatUserKeyTtlMinutes;

    @Value("${redis.chat.user.pattern}")
    private String chatUserKeyPattern;

    @Transactional
    public MessageResponse saveAndPublish(MessageDto dto) {
        requireChatMember(dto.userId(), dto.chatId());

        Message forwardedMessage = null;
        if (dto.forwardedFromMessageId() != null) {
            Message source = findById(dto.forwardedFromMessageId());
            requireChatMember(dto.userId(), source.getChatId());
            forwardedMessage = source.getForwardedMessageId() == null ? source : findById(source.getForwardedMessageId());
        }

        Message repliedMessage = null;
        if (dto.repliedMessageId() != null && !dto.repliedMessageId().isEmpty()) {
            repliedMessage = findById(dto.repliedMessageId());
            if (!Objects.equals(repliedMessage.getChatId(), dto.chatId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replied message belongs to another chat");
            }
        }

        Message message = new Message(dto, repliedMessage, forwardedMessage);
        Message saved = messageRepository.save(message);
        messageKafkaProducer.sendMessageToKafka(messageMapper.toDto(saved, forwardedMessage));
        return messageMapper.toResponse(saved, Set.of(), repliedMessage, forwardedMessage);
    }

    @Transactional(readOnly = true)
    public MessageListResponse getMessages(
            Long userId,
            Long chatId,
            String messageId,
            Integer beforeLimit,
            Integer afterLimit
    ) {
        requireChatMember(userId, chatId);

        if (messageId == null && (beforeLimit != null || afterLimit != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageId is required when beforeLimit or afterLimit is used");
        }

        List<Message> messages;
        String anchorMessageId = null;
        boolean hasBefore;
        boolean hasAfter;

        if (messageId != null) {
            int normalizedBeforeLimit = normalizeCursorLimit(beforeLimit);
            int normalizedAfterLimit = normalizeCursorLimit(afterLimit);
            Message anchorMessage = findMessageInChat(chatId, messageId);
            anchorMessageId = anchorMessage.getId();

            Slice<Message> newer = findAfter(chatId, anchorMessage, normalizedAfterLimit);
            Slice<Message> older = findBefore(chatId, anchorMessage, normalizedBeforeLimit);

            if (beforeLimit != null && afterLimit != null) {
                messages = aroundMessages(anchorMessage, newer.getContent(), older.getContent());
            } else if (afterLimit != null) {
                messages = newer.getContent();
            } else if (beforeLimit != null) {
                messages = older.getContent();
            } else {
                messages = List.of(anchorMessage);
            }

            hasBefore = beforeLimit != null ? older.hasNext() : normalizedBeforeLimit == 0;
            hasAfter = afterLimit != null ? newer.hasNext() : normalizedAfterLimit == 0;
        } else {
            Slice<Message> latest = messageRepository.findByChatIdOrderBySendAtDescIdDesc(
                    chatId,
                    PageRequest.ofSize(DEFAULT_MESSAGES_LIMIT)
            );
            messages = latest.getContent();
            hasBefore = latest.hasNext();
            hasAfter = false;
        }

        if (messages.isEmpty()) {
            return new MessageListResponse(List.of(), anchorMessageId, false, false);
        }

        Map<String, Set<ReactionOnMessage>> reactions = reactionHttpClient.getReactions(
                new ReactionsOnMessageListRequest(userId, messages.stream().map(Message::getId).toList())
        ).reactions();

        return new MessageListResponse(messageMapper.toResponses(
                messages,
                reactions,
                findReferencedMessages(messages)
        ), anchorMessageId, hasBefore, hasAfter);
    }

    public Boolean isMessageOwner(Long userId, String messageId) {
        return messageRepository.existsByUserIdAndId(userId, messageId);
    }

    @Transactional
    public void pinMessageById(Long pinnedUserId, String messageId) {
        Message message = findById(messageId);
        requireChatMember(pinnedUserId, message.getChatId());

        if(pinnedMessageRepository.existsByMessageId(message.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message is already pinned");
        }

        pinnedMessageRepository.save(new PinnedMessage(message, pinnedUserId));
        messageKafkaProducer.sendPinEvent(messageMapper.toPinEvent(message, pinnedUserId));
    }

    @Transactional
    public void unpinnedMessageById(Long unpinnedUserId, String messageId) {
        PinnedMessage pinnedMessage = pinnedMessageRepository.findByMessageId(messageId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pinned message not found with id: " + messageId)
        );
        requireChatMember(unpinnedUserId, pinnedMessage.getChatId());

        pinnedMessageRepository.delete(pinnedMessage);
        messageKafkaProducer.sendUnpinEvent(messageMapper.toPinDelete(pinnedMessage, unpinnedUserId));
    }

    @Transactional(readOnly = true)
    public List<PinMessageInfoDto> getPinnedMessageByChatId(Long userId, Long chatId) {
        requireChatMember(userId, chatId);
        List<PinnedMessage> pinnedMessages = pinnedMessageRepository.findAllByChatIdOrderByMessageSendAtAsc(chatId);
        Map<String, Message> messages = messageRepository.findAllById(pinnedMessages.stream().map(PinnedMessage::getMessageId).toList()).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));
        return pinnedMessages.stream()
                .map(item -> {
                    Message message = messages.get(item.getMessageId());
                    if (message == null) {
                        return null;
                    }
                    return messageMapper.toPinDto(item, message.getContent());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageAccessInfoDto getMessageAccessInfo(Long userId, String messageId) {
        Message message = findById(messageId);
        requireChatMember(userId, message.getChatId());
        return messageMapper.toAccessInfo(message);
    }

    @Transactional
    public void readMessageById(Long userId, String messageId) {
        Message message = findById(messageId);
        requireChatMember(userId, message.getChatId());

        if (Objects.equals(message.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender cannot mark own message as read");
        }

        if (!message.getReadStatus()) {
            message.setReadStatus(true);
            messageRepository.save(message);
            messageKafkaProducer.sendReadEvent(messageMapper.toReadEvent(message, userId));
        }
    }

    @Transactional
    public void readMessageByList(Long userId, MessageReadListDto dto) {
        List<Message> messages = new ArrayList<>(messageRepository.findAllById(dto.ids()));

        Map<Long, List<Message>> byChatId = messages.stream()
                .collect(Collectors.groupingBy(Message::getChatId));

        List<Message> toUpdate = new ArrayList<>();
        for (Map.Entry<Long, List<Message>> entry : byChatId.entrySet()) {
            if (!isChatMember(userId, entry.getKey())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
            }
            for (Message message : entry.getValue()) {
                if (Objects.equals(message.getUserId(), userId) || message.getReadStatus()) {
                    continue;
                }
                message.setReadStatus(true);
                toUpdate.add(message);
            }
        }

        if (!toUpdate.isEmpty()) {
            messageRepository.saveAll(toUpdate);
            for (Message message : toUpdate) {
                messageKafkaProducer.sendReadEvent(messageMapper.toReadEvent(message, userId));
            }
        }
    }

    @Transactional
    public Message editMessageById(Long userId, MessageEditDto dto) {
        Message message = findById(dto.id());

        if(!Objects.equals(message.getUserId(), userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only message owner can edit this message"
            );
        }

        if (message.getForwardedMessageId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forwarded messages cannot be edited");
        }

        message.setContent(dto.content());
        message.setEditStatus(true);
        Message saved = messageRepository.save(message);
        messageKafkaProducer.sendEditEvent(messageMapper.toEditEvent(saved));
        return saved;
    }

    @Transactional
    public void deleteById(Long userId, String messageId) {
        Message message = findById(messageId);

        if (!Objects.equals(message.getUserId(), userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only message owner can delete this message"
            );
        }

        List<String> photoLinks = photoLinks(message);
        messageRepository.deleteById(messageId);
        unpinDeletedMessage(message, userId);
        deleteMediaIfUnreferenced(photoLinks);
        messageKafkaProducer.sendDeleteEvent(messageMapper.toDeleteEvent(message));
    }

    @Transactional
    public void deleteByChatId(Long chatId) {
        String key = chatUserKeyPattern.formatted(chatId, '*');
        Set<String> keys = redisTemplate.keys(key);

        if(keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        List<String> photoLinks = messageRepository.findByChatId(chatId).stream()
                .flatMap(message -> photoLinks(message).stream())
                .distinct()
                .toList();
        messageRepository.deleteByChatId(chatId);
        deleteMediaIfUnreferenced(photoLinks);
    }

    public void evictChatMemberCache(Long chatId, Long userId) {
        String key = chatUserKeyPattern.formatted(chatId, userId);
        redisTemplate.delete(key);
    }

    private Message findById(String id) {
        return messageRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found with id: " + id)
        );
    }

    private Message findMessageInChat(Long chatId, String messageId) {
        Message message = findById(messageId);
        if (!Objects.equals(message.getChatId(), chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message belongs to another chat");
        }
        return message;
    }

    private Slice<Message> findBefore(Long chatId, Message anchor, int limit) {
        if (limit == 0) {
            return new SliceImpl<>(List.of());
        }

        return messageRepository.findBefore(
                chatId,
                anchor.getSendAt(),
                anchor.getId(),
                PageRequest.ofSize(limit)
        );
    }

    private Slice<Message> findAfter(Long chatId, Message anchor, int limit) {
        if (limit == 0) {
            return new SliceImpl<>(List.of());
        }

        return messageRepository.findAfter(
                chatId,
                anchor.getSendAt(),
                anchor.getId(),
                PageRequest.ofSize(limit)
        );
    }

    private List<Message> aroundMessages(Message anchor, List<Message> newer, List<Message> older) {
        List<Message> messages = new ArrayList<>(newer.size() + 1 + older.size());
        messages.addAll(newer);
        messages.add(anchor);
        messages.addAll(older);
        return messages;
    }

    private int normalizeCursorLimit(Integer limit) {
        if (limit == null) {
            return 0;
        }

        if (limit < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor limits must be greater than or equal to 0");
        }

        return Math.min(limit, DEFAULT_MESSAGES_LIMIT);
    }

    private boolean isChatMember(Long userId, Long chatId) {
        String key = chatUserKeyPattern.formatted(chatId, userId);
        Boolean cache = redisTemplate.opsForValue().get(key);

        if(Boolean.TRUE.equals(cache)) {
            redisTemplate.expire(key, Duration.ofMinutes(chatUserKeyTtlMinutes));
            return true;
        }

        boolean hasUser = chatHttpClient.hasUser(chatId, userId);

        if(hasUser) redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(chatUserKeyTtlMinutes));

        return hasUser;
    }

    private void requireChatMember(Long userId, Long chatId) {
        if (!isChatMember(userId, chatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }
    }

    private Map<String, Message> findReferencedMessages(List<Message> messages) {
        Set<String> referenceIds = messages.stream()
                .flatMap(message -> java.util.stream.Stream.of(message.getRepliedMessageId(), message.getForwardedMessageId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (referenceIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findAllById(referenceIds).stream()
                .collect(Collectors.toMap(Message::getId, message -> message));
    }

    private List<String> photoLinks(Message message) {
        return message.getPhotoLinks() == null ? List.of() : message.getPhotoLinks();
    }

    private void deleteMediaIfUnreferenced(List<String> photoLinks) {
        if (photoLinks.isEmpty()) {
            return;
        }
        Set<String> referencedPhotoLinks = messageRepository.findByPhotoLinksIn(photoLinks).stream()
                .flatMap(message -> photoLinks(message).stream())
                .collect(Collectors.toSet());
        List<String> unreferencedPhotoLinks = photoLinks.stream()
                .filter(photoLink -> !referencedPhotoLinks.contains(photoLink))
                .distinct()
                .toList();
        if (unreferencedPhotoLinks.isEmpty()) {
            return;
        }
        mediaHttpClient.deleteByListName(unreferencedPhotoLinks);
    }

    private void unpinDeletedMessage(Message message, Long unpinnedUserId) {
        pinnedMessageRepository.findByMessageId(message.getId()).ifPresent(pinnedMessage -> {
            pinnedMessageRepository.delete(pinnedMessage);
            messageKafkaProducer.sendUnpinEvent(messageMapper.toPinDelete(pinnedMessage, unpinnedUserId));
        });
    }
}
