package messenger.messageservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.messageservice.api.dto.CreateMessage;
import messenger.messageservice.api.dto.MessageEditDto;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.api.dto.MessageReadListDto;
import messenger.messageservice.api.mapper.MessageMapper;
import messenger.messageservice.domain.MessageService;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

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
        MessageDto dto = MessageDto
                .builder()
                .chatId(request.chatId())
                .userId(userId)
                .content(request.content())
                .photoLinks(request.photoLinks())
                .readStatus(false)
                .editStatus(false)
                .sendAt(Instant.now())
                .repliedMessageId(request.repliedMessageId() != null ? request.repliedMessageId() : "")
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageResponse(messageService.saveAndPublish(dto), Set.of()));
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Slice<MessageResponse>> paginationMessages(@RequestHeader(USER_ID_HEADER) Long userId,
                                                             @PathVariable @Positive Long chatId,
                                                             @RequestParam(defaultValue = "50") Integer limit,
                                                             @RequestParam(defaultValue = "0") Integer offset) {
        return ResponseEntity.ok(messageService.getSlice(userId, chatId, limit, offset));
    }

    @GetMapping("/{messageId}/exists/{userId}")
    public ResponseEntity<Boolean> isMessageOwner(@PathVariable String messageId,
                                                  @PathVariable Long userId) {
        return ResponseEntity.ok(messageService.isMessageOwner(userId, messageId));
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

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long userId,
                                       @PathVariable String messageId) {
        messageService.deleteById(userId, messageId);
        return ResponseEntity.noContent().build();
    }
}
