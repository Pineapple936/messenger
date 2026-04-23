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
        description = dto.description();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = true, length = 50)
    private String description;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
