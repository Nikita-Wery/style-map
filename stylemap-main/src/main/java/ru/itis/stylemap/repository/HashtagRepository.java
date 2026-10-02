package ru.itis.stylemap.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.stylemap.entity.Hashtag;

import java.util.List;
import java.util.Optional;

@Repository
public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    @Modifying
    @Query(value = "INSERT INTO hashtags (name, path) VALUES (:name, CAST(:path AS ltree))", nativeQuery = true)
    void insertHashtag(@Param("name") String name, @Param("path") String path);

    @Query(value = "SELECT * FROM hashtags WHERE path = CAST(:path AS ltree)", nativeQuery = true)
    Optional<Hashtag> findByPath(@Param("path") String path);

    @Query(value = "SELECT * FROM hashtags WHERE name ILIKE :query || '%'", nativeQuery = true)
    Slice<Hashtag> searchAutocomplete(@Param("query") String query, Pageable pageable);
}
