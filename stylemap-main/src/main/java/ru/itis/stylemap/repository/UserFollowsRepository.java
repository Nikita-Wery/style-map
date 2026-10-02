package ru.itis.stylemap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.entity.UserFollows;
import ru.itis.stylemap.entity.UserFollowsId;

import java.util.List;


public interface UserFollowsRepository extends JpaRepository<UserFollows, UserFollowsId> {

    boolean existsByFollowerAndFollowing(User currentUser, User author);

    long countByFollowing(User targetUser);

    long countByFollower(User targetUser);

    @Query("SELECT f.following FROM UserFollows f WHERE f.follower = :follower")
    List<User> findFollowingByFollower(@Param("follower") User follower);
}
