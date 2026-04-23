package messenger.chatservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import messenger.chatservice.api.dto.ChatResponse;
import messenger.chatservice.api.dto.ChatUsersDto;
import messenger.chatservice.api.dto.CreateChatDto;
import messenger.chatservice.api.mapper.ChatMapper;
import messenger.chatservice.domain.ChatService;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@Validated
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatMapper chatMapper;

    @PostMapping
    public ResponseEntity<ChatResponse> create(@RequestHeader(USER_ID_HEADER) Long fromUserId,
                                               @Valid @RequestBody CreateChatDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatMapper.toDto(chatService.create(fromUserId, dto)));
    }

    @GetMapping
    public ResponseEntity<Slice<ChatResponse>> paginationChats(@RequestHeader(USER_ID_HEADER) Long userId,
                                                               @RequestParam(defaultValue = "50") @Positive Integer limit,
                                                               @RequestParam(defaultValue = "0") @PositiveOrZero Integer offset) {
        return ResponseEntity.ok(chatService.getSlice(userId, limit, offset));
    }

    @GetMapping("/{chatId}/users/{userId}/exists")
    public ResponseEntity<Boolean> hasUser(@PathVariable Long chatId,
                                           @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.hasUser(chatId, userId));
    }

    @GetMapping("/{chatId}/users")
    public ResponseEntity<ChatUsersDto> getUsers(@PathVariable Long chatId) {
        return ResponseEntity.ok(chatService.getUsers(chatId));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long userId,
                                       @PathVariable Long chatId) {
        chatService.deleteById(userId, chatId);
        return ResponseEntity.noContent().build();
    }
}
