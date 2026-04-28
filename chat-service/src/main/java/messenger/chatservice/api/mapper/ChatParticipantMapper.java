package messenger.chatservice.api.mapper;

import messenger.chatservice.api.dto.ChatParticipantDto;
import messenger.chatservice.domain.ChatParticipant;
import messenger.commonlibs.mapper.CommonMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = CommonMapperConfig.class)
public interface ChatParticipantMapper {
    ChatParticipantDto toDto(ChatParticipant chatParticipant);
}
