package com.ham.netnovel.novelRanking;

import com.ham.netnovel.novel.Novel;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "novel_ranking",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "novel_Id",
                "ranking_date",
                "ranking_period"}))//랭킹, 랭킹생성날짜, 랭킹 주기 3개는 compoiste key로 사용
public class NovelRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    //랭킹
    private Integer ranking;

    //랭킹 생성 날짜, 2024-08-08 형식
    @Column(nullable = false)
    private LocalDate rankingDate;

    //랭킹 주기, 일간 주간 월간 전체 4가지 타입
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RankingPeriod rankingPeriod;

    //랭킹 점수
    private Long score;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public NovelRanking(Novel novel, Integer ranking, LocalDate rankingDate, RankingPeriod rankingPeriod, Long score) {
        this.novel = novel;
        this.ranking = ranking;
        this.rankingDate = rankingDate;
        this.rankingPeriod = rankingPeriod;
        this.score = score;
    }

    public void updateNovelRanking(Integer ranking, Long score){
        this.ranking = ranking;
        this.score = score;


    }




}
