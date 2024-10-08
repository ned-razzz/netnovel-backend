package com.ham.netnovel.commentLike;


import com.ham.netnovel.comment.Comment;
import com.ham.netnovel.commentLike.data.LikeType;
import com.ham.netnovel.member.Member;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class CommentLike {

    @EmbeddedId
    private CommentLikeId id;


    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("memberId") // 복합 키의 memberId 필드와 매핑
    @JoinColumn(name = "member_id")
    private Member member;


    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("commentId") // 복합 키의 commentId 필드와 매핑
    @JoinColumn(name = "comment_id")
    private Comment comment;

    //LIKE, DISLIKE 두가지 타입
    @Enumerated(EnumType.STRING)
    private LikeType likeType;

    @Builder
    public CommentLike(CommentLikeId id, Member member, Comment comment, LikeType likeType) {
        this.id = id;
        this.member = member;
        this.comment = comment;
        this.likeType = likeType;
    }
}
