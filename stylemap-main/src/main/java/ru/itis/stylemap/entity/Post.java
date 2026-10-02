package ru.itis.stylemap.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "posts")
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(columnDefinition = "TEXT")
    private String description;

    @DecimalMin("0.00")
    @DecimalMax("10.00")
    @Column(name = "average_rating", precision = 4, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Min(0)
    @Column(name = "ratings_count")
    private Integer ratingsCount = 0;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostMedia> mediaFiles = new ArrayList<>();

    // Вещи на фото. Каскадное сохранение и удаление.
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutfitItem> items = new ArrayList<>();

    // Связь с хештегами
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_hashtags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "hashtag_id")
    )
    private List<Hashtag> hashtags = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public void addItem(OutfitItem item) {
        items.add(item);
        item.setPost(this);
    }

    public void addMedia(PostMedia media) {
        mediaFiles.add(media);
        media.setPost(this);
    }

    @Builder
    public Post(User author, String description) {
        this.author = author;
        this.description = description;
    }
}
