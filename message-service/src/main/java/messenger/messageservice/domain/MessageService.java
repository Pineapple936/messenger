package messenger.messageservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.*;
import messenger.messageservice.api.dto.MessageEditDto;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.mapper.MessageMapper;
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
    private final MessageKafkaProducer messageKafkaProducer;
    private final RedisTemplate<String, Boolean> redisTemplate;
    private final MessageMapper messageMapper;

    @Value("${redis.chat.user.ttl.minute}")
    private int chatUserKeyTtlMinutes;

    @Value("${redis.chat.user.pattern}")
    private String chatUserKeyPattern;

    @Transactional
    public Message saveAndPublish(MessageDto dto) {
        if (!isChatMember(dto.userId(), dto.chatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }

        Message message = new Message(dto, messageRepository.findById(dto.repliedMessageId()).orElse(null));
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
        List<Message> messages = new ArrayList<>();
        messageRepository.findAllById(dto.ids()).forEach(messages::add);

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

        messageRepository.deleteById(messageId);
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

        messageRepository.deleteByChatId(chatId);
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
}
