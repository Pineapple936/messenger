package messenger.reactionservice.api.mapper;

import messenger.commonlibs.mapper.CommonMapperConfig;
import messenger.reactionservice.api.dto.ReactionDto;
import messenger.reactionservice.domain.Reaction;
import org.mapstruct.Mapper;

@Mapper(config = CommonMapperConfig.class)
public interface ReactionMapper {
    ReactionDto toDto(Reaction reaction);
}
