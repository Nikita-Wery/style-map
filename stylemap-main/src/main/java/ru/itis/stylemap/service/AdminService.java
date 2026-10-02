package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.entity.Hashtag;
import ru.itis.stylemap.entity.Post;
import ru.itis.stylemap.entity.Role;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.HashtagRepository;
import ru.itis.stylemap.repository.PostRepository;
import ru.itis.stylemap.repository.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final HashtagRepository hashtagRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Hashtag> getAllHashtags() {
        return hashtagRepository.findAll();
    }


    @Transactional
    public void createUser(NewUserPayload newUserPayload) {
        User user = new User();
        user.setUsername(newUserPayload.username());
        user.setEmail(newUserPayload.email());
        user.setPassword(passwordEncoder.encode(newUserPayload.password()));
        user.setRole(Role.USER);
        user.setExternalAuthId(UUID.randomUUID().toString());
        userRepository.save(user);
    }

    @Transactional
    public void banUser(UUID userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoSuchElementException("такого юзера не существует")
                );
        user.setActive(isActive);
    }

    @Transactional
    public void createPost(String authorId, String description){
        User author = userRepository.findByEmail(authorId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "такого юзера не существует"
                        )
                );
        Post post = new Post(author, description);
        postRepository.save(post);
    }

    @Transactional
    public void updatePost(UUID postId, String newDescription) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException(
                        "нет такого поста")
                );
        post.setDescription(newDescription);
    }


    @Transactional
    public void  createHashtag(String name, String path) {
        Hashtag hashtag = new Hashtag();
        hashtag.setName(name);
        hashtag.setPath(path);
        hashtagRepository.insertHashtag(name, path);
    }


    @Transactional
    public void updateHashtag(Long hashtagId, String newName){
        Hashtag hashtag = hashtagRepository.findById(hashtagId)
                .orElseThrow(() -> new NoSuchElementException("такого хештега нету"));
        hashtag.setName(newName);
    }


    @Transactional
    public void deleteUser(UUID userId){
        userRepository.deleteById(userId);
    }

    @Transactional
    public void deleteHashtag(Long hashtagId) {
        hashtagRepository.deleteById(hashtagId);
    }

    @Transactional
    public void deletePost(UUID postId) {
        postRepository.deleteById(postId);
    }
}
