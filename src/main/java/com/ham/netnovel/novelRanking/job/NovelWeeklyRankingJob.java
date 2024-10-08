package com.ham.netnovel.novelRanking.job;

import com.ham.netnovel.novelRanking.RankingPeriod;
import com.ham.netnovel.novelRanking.service.NovelRankingService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NovelWeeklyRankingJob implements Job {
    private final NovelRankingService novelRankingService;
    @Autowired
    public NovelWeeklyRankingJob(NovelRankingService novelRankingService) {
        this.novelRankingService = novelRankingService;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.info("주간 조회수 랭킹 업데이트 시작");
        // 1. 주간 랭킹 업데이트
        novelRankingService.updateWeeklyNovelRankings();

        // 2. Redis에 랭킹 저장
        novelRankingService.saveNovelRankingToRedis(RankingPeriod.WEEKLY);

        log.info("주간 조회수 랭킹 업데이트 완료 및 Redis 저장 완료");

    }
}
