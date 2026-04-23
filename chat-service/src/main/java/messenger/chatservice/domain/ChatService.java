package messenger.chatservice.domain;

import lombok.RequiredArgsConstructor;
import messenger.chatservice.api.dto.ChatResponse;
import messenger.chatservice.api.dto.ChatUsersDto;
import messenger.chatservice.api.dto.CreateChatDto;
import messenger.chatservice.api.mapper.ChatMapper;
import messenger.chatservice.external.UserHttpClient;
import messenger.chatservice.kafka.ChatKafkaProducer;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final UserHttpClient userHttpClient;
    private final ChatMapper chatMapper;
    private final ChatKafkaProducer chatKafkaProducer;

    @Transactional
    public Chat create(Long createUserId, CreateChatDto dto) {
        if(!userHttpClient.existsUserByUserId(dto.toUserId())) {
            throw new ResponseStatusException(NOT_FOUND, "User with id " + dto.toUserId() + " not found");
        }

        if (chatRepository.existsByUsers(createUserId, dto.toUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat already exists");
        }

        return chatRepository.save(new Chat(createUserId, dto.toUserId()));
    }

    @Transactional(readOnly = true)
    public boolean hasUser(Long chatId, Long userId) {
        return chatRepository.hasUser(chatId, userId);
    }

    @Transactional(readOnly = true)
    public ChatUsersDto getUsers(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
        return new ChatUsersDto(chat.getUserId1(), chat.getUserId2());
    }

    @Transactional(readOnly = true)
    public Slice<ChatResponse> getSlice(Long userId, Integer limit, Integer offset) {
        Pageable page = PageRequest.of(offset, limit);
        return chatRepository.findByUserIdOrderByLastMessageAtDesc(userId, page)
                .map(chatMapper::toDto);
    }

    @Transactional
    public void touchChatLastMessage(Long chatId, LocalDateTime lastMessageAt) {
        int updated = chatRepository.touchLastMessageAt(chatId, lastMessageAt);
        if (updated == 0 && !chatRepository.existsById(chatId)) {
            throw new IllegalArgumentException("Chat not found");
        }
    }

    @Transactional
    public void deleteById(Long userId, Long chatId) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found")
        );

        if(!Objects.equals(chat.getUserId1(), userId) && !Objects.equals(chat.getUserId2(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this chat");
        }

        chatRepository.delete(chat);
        chatKafkaProducer.sendMessageToKafka(new DeleteChatDto(chatId));
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        List<Chat> chats = chatRepository.findAllByUserId(userId);
        for(Chat chat: chats) {
            chatRepository.delete(chat);
            chatKafkaProducer.sendMessageToKafka(new DeleteChatDto(chat.getId()));
        }
    }
}
