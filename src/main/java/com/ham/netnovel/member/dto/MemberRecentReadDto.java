package com.ham.netnovel.member.dto;


import com.ham.netnovel.novel.data.NovelType;
import com.ham.netnovel.tag.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MemberRecentReadDto {


    private Long id;

    private String title;

    //연재정보
    private NovelType novelType;

    //작가명
    private String authorName;

    //최근본 에피소드 제목
    private String episodeTitle;

    //최근본 에피소드 id, 리다이렉트시 사용
    private Long episodeId;

    //업데이트시간
    private LocalDateTime updatedAt;


    private String thumbnailUrl;//섬네일 URL




}
