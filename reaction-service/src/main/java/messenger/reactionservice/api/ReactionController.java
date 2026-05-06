package messenger.reactionservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListRequest;
import messenger.commonlibs.dto.messageservice.ReactionsOnMessageListResponse;
import messenger.reactionservice.api.dto.CreateReactionDto;
import messenger.reactionservice.api.dto.ReactionDto;
import messenger.commonlibs.dto.messageservice.ReactionOnMessage;
import messenger.reactionservice.api.mapper.ReactionMapper;
import messenger.reactionservice.domain.Reaction;
import messenger.reactionservice.domain.ReactionService;
import messenger.commonlibs.dto.reactionservice.ReactionType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@RestController
@RequestMapping("/reaction")
@RequiredArgsConstructor
public class ReactionController {
    private final ReactionService reactionService;
    private final ReactionMapper reactionMapper;

    @PostMapping("/add")
    public ResponseEntity<ReactionDto> add(@RequestHeader(USER_ID_HEADER) Long userId,
                                           @Valid @RequestBody CreateReactionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                reactionMapper.toDto(reactionService.add(userId, dto))
        );
    }

    @PostMapping("/message/batchByUser")
    public ResponseEntity<ReactionsOnMessageListResponse> batchReactionsByUserIdAndMessageIds(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long headerUserId,
            @Valid @RequestBody ReactionsOnMessageListRequest dto) {
        Long userId = headerUserId != null ? headerUserId : dto.userId();
        return ResponseEntity.ok(
                new ReactionsOnMessageListResponse(reactionService.batchReactionsByUserIdAndMessageIds(userId, dto.messageIds()))
        );
    }

    @GetMapping("/message/{messageId}")
    public ResponseEntity<List<ReactionOnMessage>> getByMessageId(@RequestHeader(USER_ID_HEADER) Long userId,
                                                                  @NotBlank @PathVariable String messageId) {
        List<Reaction> reactions = reactionService.getByMessageId(messageId);
        Set<ReactionType> userReaction = reactionService.getReactionTypesByUserIdAndMessageId(userId, messageId);

        return ResponseEntity.ok(
                reactions
                        .stream()
                        .map(item -> new ReactionOnMessage(item.getReactionType(), item.getCount(), userReaction.contains(item.getReactionType())))
                        .toList()
        );
    }

    @DeleteMapping("/message/{messageId}/{reactionType}")
    public ResponseEntity<Void> deleteByMessageId(@RequestHeader(USER_ID_HEADER) Long userId,
                                                  @PathVariable String messageId,
                                                  @PathVariable ReactionType reactionType) {
        reactionService.deleteByMessageIdAndReactionType(userId, messageId, reactionType);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/message/{messageId}")
    public ResponseEntity<Void> deleteMessageById(@RequestHeader(USER_ID_HEADER) Long userId,
                                                  @PathVariable String messageId) {
        reactionService.deleteMessageById(userId, messageId);
        return ResponseEntity.noContent().build();
    }
}
