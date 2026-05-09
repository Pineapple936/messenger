package messenger.userservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import messenger.commonlibs.dto.userservice.CreateUserDto;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_details")
@NoArgsConstructor
public class UserDetails {
    UserDetails(CreateUserDto dto) {
        userId = dto.id();
        name = dto.name();
        tag = dto.tag();
        description = dto.description();
        if(dto.avatarUrl() != null) {
            avatarUrl = dto.avatarUrl();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "tag", nullable = false, unique = true, length = 15)
    private String tag;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = true, length = 50)
    private String description;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
