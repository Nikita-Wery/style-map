package ru.itis.stylemap.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Table(name = "user_follows")
@NoArgsConstructor
public class UserFollows {

    @EmbeddedId
    private UserFollowsId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("followingId")
    @JoinColumn(name = "following_id")
    private User following;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("followerId")
    @JoinColumn(name = "follower_id")
    private User follower;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @Builder
    public UserFollows(User follower, User following) {
        this.follower = follower;
        this.following = following;
        this.id = new UserFollowsId(follower.getId(), following.getId());
    }
}
