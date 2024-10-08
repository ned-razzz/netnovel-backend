package com.ham.netnovel.comment.dto;

import com.ham.netnovel.reComment.dto.ReCommentListDto;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CommentEpisodeListDto {//뷰에 반환할때 사용하는 DTO


    //댓글 PK
    private Long id;

    //댓글내용
    private String content;

    //작성자 닉네임
    private String nickName;

    //에피소드 제목
    private String episodeTitle;

    //생성시간
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Min(0)
    //좋아요 수
    private int likes;
    @Min(0)
    //싫어요 수
    private int disLikes;


    boolean isEditable;//수정/삭제 가능여부

    //댓글에 달린 대댓글 목록

    private List<ReCommentListDto> reCommentList;

}

