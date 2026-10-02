package ru.itis.stylemap.repository;

import org.hibernate.boot.models.JpaAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.itis.stylemap.entity.Post;
import ru.itis.stylemap.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findAllByDescriptionContaining(String filter);

    Page<Post> findAll(Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM posts p " +
                   "JOIN post_hashtags ph ON p.id = ph.post_id " +
                   "JOIN hashtags h ON ph.hashtag_id = h.id " +
                   "WHERE h.path ~ CAST('*.' || :tagPath || '.*' AS lquery)",
            countQuery = "SELECT count(DISTINCT p.id) FROM posts p " +
                         "JOIN post_hashtags ph ON p.id = ph.post_id " +
                         "JOIN hashtags h ON ph.hashtag_id = h.id " +
                         "WHERE h.path ~ CAST('*.' || :tagPath || '.*' AS lquery)",
            nativeQuery = true)
    Page<Post> findAllByHashtagPath(@Param("tagPath") String tagPath, Pageable pageable);


    Page<Post> findAllByAuthor_Id(UUID authorId, Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET " +
           "p.ratingsCount = (SELECT COUNT(r) FROM Rating r WHERE r.post.id = :postId), " +
           "p.averageRating = (SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.post.id = :postId) " +
           "WHERE p.id = :postId")
    void recalculateRatingStats(@Param("postId") UUID postId);

    // Fetch only one bag; other collections load inside the service transaction.
    @EntityGraph(attributePaths = {"mediaFiles"})
    Optional<Post> findById(UUID id);

    Page<Post> findByAuthorIn(Collection<User> authors, Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM posts p " +
                   "JOIN post_hashtags ph ON p.id = ph.post_id " +
                   "JOIN hashtags h ON ph.hashtag_id = h.id " +
                   "JOIN users u on p.author_id = u.id WHERE h.path ~ CAST('*.' || :hashtag || '.*' AS lquery) AND u.username IN :users",
            countQuery = "SELECT count(DISTINCT p.id) FROM posts p " +
                         "JOIN post_hashtags ph ON p.id = ph.post_id " +
                         "JOIN hashtags h ON ph.hashtag_id = h.id " +
                         "JOIN users u on p.author_id = u.id WHERE h.path ~ CAST('*.' || :hashtag || '.*' AS lquery) AND u.username IN :users",
            nativeQuery = true)
    Page<Post> findByHashtagAndUserIn(@Param("hashtag") String hashtag, @Param("users") List<String> users, Pageable pageable);
}
