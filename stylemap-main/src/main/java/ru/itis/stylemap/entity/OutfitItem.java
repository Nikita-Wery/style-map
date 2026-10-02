package ru.itis.stylemap.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import ru.itis.stylemap.converter.LinkDtoConverter;
import ru.itis.stylemap.dto.LinkDto;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "outfit_items")
@Getter @Setter @NoArgsConstructor
public class OutfitItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String brand;

    @Convert(converter = LinkDtoConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, LinkDto> links = new HashMap<>();
}