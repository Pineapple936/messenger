package messenger.messageservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageAccessInfoDto;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.PinMessageDto;
import messenger.messageservice.api.dto.CreateMessage;
import messenger.messageservice.api.dto.MessageEditDto;
import messenger.messageservice.api.dto.MessageListResponse;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.domain.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@Validated
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;
    private final MessageMapper messageMapper;

    @PostMapping
    public ResponseEntity<MessageResponse> addMessage(@RequestHeader(USER_ID_HEADER) Long userId,
                                                      @RequestBody @Valid CreateMessage request) {
        MessageDto dto = messageMapper.toDto(userId, request, Instant.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(messageService.saveAndPublish(dto));
    }

    @PostMapping("/pin/{messageId}")
    public ResponseEntity<Void> pinMessage(@RequestHeader(USER_ID_HEADER) Long userId,
                                           @PathVariable @NotBlank String messageId) {
        messageService.pinMessageById(userId, messageId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<MessageListResponse> getMessages(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable @Positive Long chatId,
            @RequestParam(defaultValue = "50") @Positive int limit,
            @RequestParam(required = false) @NotBlank String beforeMessageId,
            @RequestParam(required = false) @NotBlank String aroundMessageId
    ) {
        if(limit > 50) limit = 50;
        return ResponseEntity.ok(messageService.getMessages(userId, chatId, limit, beforeMessageId, aroundMessageId));
    }

    @GetMapping("/{messageId}/exists/{userId}")
    public ResponseEntity<Boolean> isMessageOwner(@PathVariable String messageId,
                                                  @PathVariable Long userId) {
        return ResponseEntity.ok(messageService.isMessageOwner(userId, messageId));
    }

    @GetMapping("/{messageId}/access")
    public ResponseEntity<MessageAccessInfoDto> getMessageAccessInfo(@PathVariable String messageId,
                                                                     @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(messageService.getMessageAccessInfo(userId, messageId));
    }

    @GetMapping("/chat/{chatId}/pins")
    public ResponseEntity<List<PinMessageDto>> getPinnedMessages(@RequestHeader(USER_ID_HEADER) Long userId,
                                                                 @PathVariable Long chatId) {
        return ResponseEntity.ok(messageService.getPinnedMessageByChatId(userId, chatId).stream()
                .map(messageMapper::toPinDto)
                .toList());
    }

    @PutMapping("/edit")
    public ResponseEntity<MessageDto> editMessageById(@RequestHeader(USER_ID_HEADER) Long userId,
                                                           @Valid @RequestBody MessageEditDto dto) {
        return ResponseEntity.ok(messageMapper.toDto(messageService.editMessageById(userId, dto)));
    }

    @PutMapping("/read/{messageId}")
    public ResponseEntity<Void> readMessageById(@RequestHeader(USER_ID_HEADER) Long userId,
                                                @PathVariable String messageId) {
        messageService.readMessageById(userId, messageId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read")
    public ResponseEntity<Void> readMessageByList(@RequestHeader(USER_ID_HEADER) Long userId,
                                                  @Valid @RequestBody MessageReadListDto dto) {
        messageService.readMessageByList(userId, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/unpin/{messageId}")
    public ResponseEntity<Void> unpinMessage(@RequestHeader(USER_ID_HEADER) Long userId,
                                             @PathVariable @NotBlank String messageId) {
        messageService.unpinnedMessageById(userId, messageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long userId,
                                       @PathVariable String messageId) {
        messageService.deleteById(userId, messageId);
        return ResponseEntity.noContent().build();
    }
}
