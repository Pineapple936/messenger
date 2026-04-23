package messenger.messageservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.kafka.MessageKafkaProducer;
import messenger.messageservice.external.ChatHttpClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatHttpClient chatHttpClient;
    private final MessageKafkaProducer messageKafkaProducer;
    private final Map<ChatUserKey, Boolean> membershipCache = new ConcurrentHashMap<>();
    private final MessageMapper messageMapper;

    @Transactional
    public Message saveAndPublish(MessageDto dto) {
        if (!isChatMember(dto.userId(), dto.chatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }

        Message message = new Message(dto);
        Message saved = messageRepository.save(message);
        messageKafkaProducer.sendMessageToKafka(messageMapper.toDto(saved));
        return saved;
    }

    public Slice<MessageResponse> getSlice(Long userId, Long chatId, Integer limit, Integer offset) {
        if (!isChatMember(userId, chatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }

        Pageable page = PageRequest.of(offset, limit);
        return messageRepository.findByChatIdOrderBySendAtDescIdDesc(chatId, page)
                .map(messageMapper::toResponse);
    }

    @Transactional
    public void readMessageById(Long userId, String messageId) {
        Message message = messageRepository.findById(messageId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found with id: " + messageId)
        );

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
        for(var id: dto.ids()) {
            readMessageById(userId, id);
        }
    }

    @Transactional
    public void deleteById(Long userId, String messageId) {
        Message message = messageRepository.findById(messageId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message with id " + messageId + " not found")
        );
        if(isChatMember(userId, message.getChatId())) {
            messageRepository.deleteById(messageId);
            messageKafkaProducer.sendDeleteEvent(new MessageDeleteEventDto(
                    message.getId(),
                    message.getChatId(),
                    userId
            ));
        }
        else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat not found or user is not a member");
        }
    }

    public void deleteByChatId(Long chatId) {
        messageRepository.deleteByChatId(chatId);
    }

    private boolean isChatMember(Long userId, Long chatId) {
        ChatUserKey key = new ChatUserKey(chatId, userId);
        if (membershipCache.containsKey(key)) {
            return true;
        }

        boolean hasUser = chatHttpClient.hasUser(chatId, userId);
        if (hasUser) {
            membershipCache.put(key, true);
        }
        return hasUser;
    }

    private record ChatUserKey(Long chatId, Long userId) {
    }
}
