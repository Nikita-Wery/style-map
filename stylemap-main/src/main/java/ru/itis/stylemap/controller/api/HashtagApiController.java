package ru.itis.stylemap.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itis.stylemap.entity.Hashtag;
import ru.itis.stylemap.repository.HashtagRepository;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hashtags")
@RequiredArgsConstructor
public class HashtagApiController {

    private final HashtagRepository hashtagRepository;

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchHashtags(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        Slice<Hashtag> hashtagPage = hashtagRepository.searchAutocomplete(query, PageRequest.of(page, size));

        List<Map<String, String>> tags = hashtagPage.getContent().stream()
                .map(tag -> Map.of("name", tag.getName(), "path", tag.getPath()))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("tags", tags);
        response.put("currentPage", hashtagPage.getNumber());
        response.put("hasNext", hashtagPage.hasNext());

        return ResponseEntity.ok(response);
    }
}