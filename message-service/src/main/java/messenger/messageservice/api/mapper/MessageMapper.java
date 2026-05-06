package messenger.messageservice.api.mapper;

import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.mapper.CommonMapperConfig;
import messenger.messageservice.domain.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CommonMapperConfig.class)
public interface MessageMapper {
    @Mapping(source = "repliedMessage.id", target = "repliedMessageId")
    MessageDto toDto(Message message);
}
