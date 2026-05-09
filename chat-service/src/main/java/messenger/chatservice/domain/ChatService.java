package messenger.chatservice.domain;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import messenger.chatservice.api.dto.AddUserInChatDto;
import messenger.chatservice.api.dto.ChatResponse;
import messenger.chatservice.api.dto.CreateChatDto;
import messenger.chatservice.api.dto.UpdateRoleUserDto;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserHttpClient userHttpClient;
    private final ChatKafkaProducer chatKafkaProducer;

    @Transactional
    public Chat create(Long createUserId, CreateChatDto dto) {
        List<Long> correctParticipantsId = userHttpClient.existsUsersById(dto.participantIds());

        if (correctParticipantsId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Participants not found");
        }

        if (correctParticipantsId.size() > 1 && dto.chatType() == ChatType.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Private chat can only have one participant");
        }

        Instant joinedAt = Instant.now();

        ChatParticipant creator = new ChatParticipant();
        creator.setUserId(createUserId);
        creator.setRole(ChatRole.OWNER);
        creator.setJoinedAt(joinedAt);

        Chat chat = new Chat();
        chat.setType(dto.chatType());
        chat.setName(dto.name());
        chat.setParticipants(new ArrayList<>());
        chat.setCreatedUserId(createUserId);
        chat.getParticipants().add(creator);
        chat.setLastMessageAt(joinedAt);
        creator.setChat(chat);

        if (dto.chatType() == ChatType.PRIVATE) {
            ChatParticipant participant = new ChatParticipant();
            participant.setChat(chat);
            participant.setUserId(dto.participantIds().getFirst());
            participant.setRole(ChatRole.OWNER);
            participant.setJoinedAt(joinedAt);
            chat.getParticipants().add(participant);
        } else {
            correctParticipantsId.forEach(id -> {
                ChatParticipant participant = new ChatParticipant();
                participant.setChat(chat);
                participant.setUserId(id);
                participant.setRole(ChatRole.MEMBER);
                participant.setJoinedAt(joinedAt);
                chat.getParticipants().add(participant);
            });
        }

        return chatRepository.save(chat);
    }

    @Transactional
    public ChatParticipant addUserInChat(Long inviterId, AddUserInChatDto dto) {
        if (!userHttpClient.existsUserById(dto.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found user with id=" + dto.userId());
        }

        Chat chat = chatRepository.findById(dto.chatId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found chat with id=" + dto.chatId())
        );

        if (chat.getType() == ChatType.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can not add users in PRIVATE chat");
        }

        ChatParticipant inviter = findActiveParticipant(inviterId, dto.chatId());

        if (dto.role() == ChatRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can not add user with role OWNER");
        } else if (inviter.getRole() == ChatRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member can not add user in chat");
        }

        chatParticipantRepository.findByChatIdAndUserIdAndLeftAtIsNull(dto.chatId(), dto.userId())
                .ifPresent(p -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "User already in chat"); });

        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        participant.setUserId(dto.userId());
        participant.setRole(dto.role());
        participant.setJoinedAt(Instant.now());

        return chatParticipantRepository.save(participant);
    }

    @Transactional(readOnly = true)
    public boolean hasUser(Long chatId, Long userId) {
        return chatParticipantRepository.existsByChatIdAndUserIdAndLeftAtIsNull(chatId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatParticipant> getUsers(Long chatId) {
        return chatParticipantRepository.findByChatId(chatId).stream()
                .filter(p -> p.getLeftAt() == null)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatParticipant findActiveParticipant(Long userId, Long chatId) {
        return chatParticipantRepository
                .findByChatIdAndUserIdAndLeftAtIsNull(chatId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not found user with id=" + userId + " in chatId=" + chatId));
    }

    @Transactional(readOnly = true)
    public Slice<ChatResponse> getSlice(Long userId, Integer limit, Integer offset) {
        Pageable page = PageRequest.of(offset, limit);
        return chatRepository.findChatPreviews(userId, page);
    }

    @Transactional
    public void updateRole(Long promoterId, @Valid UpdateRoleUserDto dto) {
        if (dto.role() == ChatRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can not set role OWNER");
        }

        ChatParticipant user = findActiveParticipant(dto.userId(), dto.chatId());
        ChatParticipant promoter = findActiveParticipant(promoterId, dto.chatId());

        if (promoter.getRole() == ChatRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member can not change role");
        }

        user.setRole(dto.role());
        chatParticipantRepository.save(user);
    }

    @Transactional
    public void updateName(Long promoterId, Long chatId, String newName) {
        ChatParticipant requester = findActiveParticipant(promoterId, chatId);
        Chat chat = chatRepository.findById(chatId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found chat with id=" + chatId)
        );

        if (chat.getType() == ChatType.PRIVATE) {
            requester.setCustomChatName(newName);
            chatParticipantRepository.save(requester);
        } else if (chat.getType() == ChatType.GROUP) {
            if (requester.getRole() == ChatRole.MEMBER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MEMBER can not change chat name");
            }
            chat.setName(newName);
            chatRepository.save(chat);
        }
    }

    @Transactional
    public void updateChatAvatar(Long requesterId, Long chatId, String avatarUrl) {
        ChatParticipant participant = findActiveParticipant(requesterId, chatId);
        if (participant.getRole() == ChatRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin or owner can change group avatar");
        }
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
        if (chat.getType() != ChatType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only group chats can have avatars");
        }
        chat.setAvatarUrl(avatarUrl);
        chatRepository.save(chat);
    }

    @Transactional
    public void touchChatLastMessage(Long chatId, Instant lastMessageAt, String content, Long userId, boolean hasMedia) {
        String preview = content != null && !content.isBlank()
                ? (content.length() > 100 ? content.substring(0, 100) : content)
                : null;
        int updated = chatRepository.touchLastMessage(chatId, lastMessageAt, preview, userId, hasMedia);
        if (updated == 0 && !chatRepository.existsById(chatId)) {
            throw new IllegalArgumentException("Chat not found");
        }
    }

    @Transactional
    public void leaveByChatId(Long userId, Long chatId) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found")
        );

        ChatParticipant participant = chatParticipantRepository
                .findByChatIdAndUserIdAndLeftAtIsNull(chatId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this chat"));

        participant.setLeftAt(Instant.now());
        chatParticipantRepository.save(participant);

        if (chatParticipantRepository.findByChatId(chatId).stream().allMatch(p -> p.getLeftAt() != null)) {
            chatRepository.delete(chat);
            chatKafkaProducer.sendMessageToKafka(new DeleteChatDto(chatId));
        }
    }

    @Transactional
    public void deleteByChatId(Long userId, Long chatId) {
        if (findActiveParticipant(userId, chatId).getRole() != ChatRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER can delete chat");
        }

        chatRepository.deleteById(chatId);
        chatKafkaProducer.sendMessageToKafka(new DeleteChatDto(chatId));
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        List<ChatParticipant> participants = chatParticipantRepository.findByUserIdAndLeftAtIsNull(userId);

        if (participants.isEmpty()) {
            return;
        }

        List<Long> chatIdsToDelete = new ArrayList<>();

        for (ChatParticipant participant : participants) {
            Chat chat = participant.getChat();

            if (chat.getType() == ChatType.PRIVATE) {
                chatIdsToDelete.add(chat.getId());
            } else {
                participant.setLeftAt(Instant.now());
                chatParticipantRepository.save(participant);

                if (chatParticipantRepository.findByChatId(chat.getId()).stream()
                        .allMatch(p -> p.getLeftAt() != null)) {
                    chatIdsToDelete.add(chat.getId());
                }
            }
        }

        if (!chatIdsToDelete.isEmpty()) {
            List<Chat> chatsToDelete = chatRepository.findAllById(chatIdsToDelete);
            chatRepository.deleteAllInBatch(chatsToDelete);
            chatIdsToDelete.forEach(chatId ->
                    chatKafkaProducer.sendMessageToKafka(new DeleteChatDto(chatId)));
        }
    }
}
