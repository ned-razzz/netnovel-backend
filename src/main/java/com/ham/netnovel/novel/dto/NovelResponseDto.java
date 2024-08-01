package com.ham.netnovel.novel.dto;

import com.ham.netnovel.novel.data.NovelType;
import com.ham.netnovel.tag.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelResponseDto {

    @NotNull
    private Long novelId;

    @NotBlank
    @Size(max = 30)
    private String title;

    @Size(max = 300)
    private String description;

    private NovelType type;

    private String authorName;

    //메타 데이터
    private Integer view;

    private Integer favoriteAmount;

    private Integer episodeAmount;

    private List<Tag> tags;

}
