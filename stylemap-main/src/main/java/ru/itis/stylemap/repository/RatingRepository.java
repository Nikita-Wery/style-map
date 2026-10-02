package ru.itis.stylemap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.stylemap.entity.Rating;
import ru.itis.stylemap.entity.RatingId;

public interface RatingRepository extends JpaRepository<Rating, RatingId> {
}
