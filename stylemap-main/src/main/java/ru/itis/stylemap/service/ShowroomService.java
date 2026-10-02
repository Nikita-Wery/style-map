package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.stylemap.entity.MediaType;
import ru.itis.stylemap.entity.Post;
import ru.itis.stylemap.entity.PostMedia;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.repository.PostRepository;
import ru.itis.stylemap.repository.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ShowroomService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public List<Post> getPosts(String request) {
        if (request != null && !request.isBlank()) {
            return postRepository.findAllByDescriptionContaining(request);
        }
        return postRepository.findAll();
    }
}