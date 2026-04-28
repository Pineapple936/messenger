package messenger.chatservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import messenger.chatservice.api.dto.*;
import messenger.chatservice.api.mapper.ChatMapper;
import messenger.chatservice.api.mapper.ChatParticipantMapper;
import messenger.chatservice.domain.ChatService;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@Validated
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatMapper chatMapper;
    private final ChatParticipantMapper chatParticipantMapper;

    @PostMapping
    public ResponseEntity<ChatResponse> create(@RequestHeader(USER_ID_HEADER) Long fromUserId,
                                               @Valid @RequestBody CreateChatDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatMapper.toResponse(chatService.create(fromUserId, dto)));
    }

    @PostMapping("/users")
    public ResponseEntity<ChatParticipantDto> addUserInChat(@RequestHeader(USER_ID_HEADER) Long fromUserId,
                                                            @RequestBody @Valid AddUserInChatDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatParticipantMapper.toDto(
                chatService.addUserInChat(fromUserId, dto)
        ));
    }

    @GetMapping
    public ResponseEntity<Slice<ChatResponse>> paginationChats(@RequestHeader(USER_ID_HEADER) Long userId,
                                                               @RequestParam(defaultValue = "50") @Positive Integer limit,
                                                               @RequestParam(defaultValue = "0") @PositiveOrZero Integer offset) {
        return ResponseEntity.ok(chatService.getSlice(userId, limit, offset));
    }

    @PutMapping("{chatId}/name/{newName}")
    public ResponseEntity<Void> changeName(@RequestHeader(USER_ID_HEADER) Long promoterId,
                                                   @PathVariable @Positive Long chatId,
                                                   @PathVariable @NotBlank String newName) {
        chatService.updateName(promoterId, chatId, newName);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/role")
    public ResponseEntity<Void> updateRole(@RequestHeader(USER_ID_HEADER) Long promoterId,
                                           @RequestBody @Valid UpdateRoleUserDto dto) {
        chatService.updateRole(promoterId, dto);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/{chatId}/users/{userId}/exists")
    public ResponseEntity<Boolean> hasUser(@PathVariable Long chatId,
                                           @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.hasUser(chatId, userId));
    }

    @GetMapping("/{chatId}/users")
    public ResponseEntity<List<ChatParticipantDto>> getUsers(@PathVariable Long chatId) {
        return ResponseEntity.ok(chatService.getUsers(chatId).stream().map(chatParticipantMapper::toDto).toList());
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long userId,
                                       @PathVariable Long chatId) {
        chatService.deleteByChatId(userId, chatId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/leave/{chatId}")
    public ResponseEntity<Void> leave(@RequestHeader(USER_ID_HEADER) Long userId,
                                      @PathVariable Long chatId) {
        chatService.leaveByChatId(userId, chatId);
        return ResponseEntity.noContent().build();
    }
}
