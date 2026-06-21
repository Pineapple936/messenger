package messenger.messageservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.*;
import messenger.messageservice.api.dto.MessageEditDto;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.external.MediaHttpClient;
import messenger.messageservice.external.ReactionHttpClient;
import messenger.messageservice.kafka.MessageKafkaProducer;
import messenger.messageservice.external.ChatHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
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
    public Message saveAndPublish(MessageDto dto, String forwardedFromMessageId) {
        if (!isChatMember(dto.userId(), dto.chatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }

        if (forwardedFromMessageId != null && dto.repliedMessageId() != null && !dto.repliedMessageId().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A message cannot be a reply and a forwarded message at the same time");
        }

        ForwardedMessage forwardedMessage = null;
        if (forwardedFromMessageId != null) {
            Message source = findById(forwardedFromMessageId);
            if (!isChatMember(dto.userId(), source.getChatId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User cannot access the source message");
            }
            forwardedMessage = new ForwardedMessage(source);
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
        messageKafkaProducer.sendMessageToKafka(messageMapper.toDto(saved));
        return saved;
    }

    public Message findById(String id) {
        return messageRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found with id: " + id)
        );
    }

    public Slice<MessageResponse> getSlice(Long userId, Long chatId, Integer limit, Integer offset) {
        if (!isChatMember(userId, chatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }

        Pageable page = PageRequest.of(offset, limit);
        Slice<Message> messageSlice = messageRepository.findByChatIdOrderBySendAtDescIdDesc(chatId, page);
        Map<String, Set<ReactionOnMessage>> reactions = reactionHttpClient.getReactions(new ReactionsOnMessageListRequest(
                userId,
                messageSlice.stream().map(Message::getId).toList()
        )).reactions();

        return messageSlice.map(
                item -> new MessageResponse(item, reactions.getOrDefault(item.getId(), Set.of()))
        );
    }

    public Boolean isMessageOwner(Long userId, String messageId) {
        return messageRepository.existsByUserIdAndId(userId, messageId);
    }

    @Transactional
    public void readMessageById(Long userId, String messageId) {
        Message message = findById(messageId);

        if(isChatMember(userId, message.getChatId())) {
            if (Objects.equals(message.getUserId(), userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender cannot mark own message as read");
            }

            if (!message.getReadStatus()) {
                message.setReadStatus(true);
                messageRepository.save(message);
                messageKafkaProducer.sendReadEvent(new MessageReadEventDto(
                        message.getId(),
                        message.getChatId(),
                        userId,
                        true
                ));
            }
        }
        else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
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
                messageKafkaProducer.sendReadEvent(new MessageReadEventDto(
                        message.getId(), message.getChatId(), userId, true));
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

        if (message.getForwardedMessage() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forwarded messages cannot be edited");
        }

        message.setContent(dto.content());
        message.setEditStatus(true);
        Message saved = messageRepository.save(message);
        messageKafkaProducer.sendEditEvent(new MessageEditEventDto(
                saved.getId(),
                saved.getChatId(),
                saved.getContent(),
                saved.getEditStatus()
        ));
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

        String mediaSourceId = mediaSourceId(message);
        List<String> photoLinks = message.getPhotoLinks();
        messageRepository.deleteById(messageId);
        deleteMediaIfUnreferenced(mediaSourceId, photoLinks);
        messageKafkaProducer.sendDeleteEvent(new MessageDeleteEventDto(
                message.getId(),
                message.getChatId(),
                message.getUserId()
        ));
    }

    @Transactional
    public void deleteByChatId(Long chatId) {
        String key = chatUserKeyPattern.formatted(chatId, '*');
        Set<String> keys = redisTemplate.keys(key);

        if(keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        Map<String, List<String>> mediaBySource = messageRepository.findByChatId(chatId).stream()
                .filter(message -> message.getPhotoLinks() != null && !message.getPhotoLinks().isEmpty())
                .collect(Collectors.toMap(
                        this::mediaSourceId,
                        Message::getPhotoLinks,
                        (left, right) -> left
                ));
        messageRepository.deleteByChatId(chatId);
        mediaBySource.forEach(this::deleteMediaIfUnreferenced);
    }

    public void evictChatMemberCache(Long chatId, Long userId) {
        String key = chatUserKeyPattern.formatted(chatId, userId);
        redisTemplate.delete(key);
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

    private String mediaSourceId(Message message) {
        return message.getForwardedMessage() == null
                ? message.getId()
                : message.getForwardedMessage().getOriginalMessageId();
    }

    private void deleteMediaIfUnreferenced(String sourceMessageId, List<String> photoLinks) {
        if (photoLinks == null || photoLinks.isEmpty()) {
            return;
        }
        if (messageRepository.existsById(sourceMessageId)
                || messageRepository.existsByForwardedMessageOriginalMessageId(sourceMessageId)) {
            return;
        }
        mediaHttpClient.deleteByListName(photoLinks);
    }
}
