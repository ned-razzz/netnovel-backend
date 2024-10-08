package com.ham.netnovel.episode.service;

import com.ham.netnovel.coinUseHistory.service.CoinUseHistoryService;
import com.ham.netnovel.common.exception.EpisodeNotPurchasedException;
import com.ham.netnovel.common.exception.ServiceMethodException;
import com.ham.netnovel.common.utils.TypeValidationUtil;
import com.ham.netnovel.episode.Episode;
import com.ham.netnovel.episode.repository.EpisodeRepository;
import com.ham.netnovel.episode.data.EpisodeStatus;
import com.ham.netnovel.episode.data.IndexDirection;
import com.ham.netnovel.episode.dto.EpisodeDetailDto;
import com.ham.netnovel.episode.dto.EpisodePaymentDto;
import com.ham.netnovel.episodeViewCount.ViewCountIncreaseDto;
import com.ham.netnovel.episodeViewCount.service.EpisodeViewCountService;
import com.ham.netnovel.novel.Novel;
import com.ham.netnovel.recentRead.service.RecentReadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EpisodeManagementServiceImpl implements EpisodeManagementService {

    private final EpisodeService episodeService;
    private final CoinUseHistoryService coinUseHistoryService;
    private final EpisodeViewCountService episodeViewCountService;

    private final EpisodeRepository episodeRepository;


    private final RecentReadService recentReadService;

    public EpisodeManagementServiceImpl(EpisodeService episodeService, CoinUseHistoryService coinUseHistoryService, EpisodeViewCountService episodeViewCountService, EpisodeRepository episodeRepository, RecentReadService recentReadService) {
        this.episodeService = episodeService;
        this.coinUseHistoryService = coinUseHistoryService;
        this.episodeViewCountService = episodeViewCountService;
        this.episodeRepository = episodeRepository;
        this.recentReadService = recentReadService;
    }


    @Override
    @Transactional
    public EpisodeDetailDto getEpisodeDetail(String providerId, Long episodeId) {


        //episode 엔티티 유무 확인, 없을경우 예외로 던짐
        Episode episode = episodeService.getEpisode(episodeId)
                .orElseThrow(() -> new NoSuchElementException("Episode 정보 없음"));

        //에피소드의 coinCost 객체에 저장
        Integer coinCost = episode.getCostPolicy().getCoinCost();
        //coinCost 검증, null 이거나 음수면 예외로 던짐
        TypeValidationUtil.validateCoinAmount(coinCost);


        //무료 에피소드일 경우 처리 로직
        if (coinCost == 0) {
            //유저 정보가 있으면, 최근 조회 목록 업데이트, 없을경우 에피소드 정보 반환
            if (!providerId.equals("NON_LOGIN")) {
                //유저의 최근 조회 기록 업데이트
                recentReadService.updateRecentRead(providerId, episodeId);
            }

            //유료 에피소드일 경우 처리 로직
        } else {

            //로그인 정보가 없을경우, 예외로 던짐
            if (providerId.equals("NON_LOGIN")){
               throw new AuthenticationCredentialsNotFoundException("User is not authenticated");
            }
            //유저의 결제 기록 확인, 결제 기록이 없으면 예외로 던지고 결제 유도 메시지 전송
            validCoinPurchase(providerId,episode, coinCost);
            //유저의 최근 조회 기록 업데이트
            recentReadService.updateRecentRead(providerId, episodeId);
        }

        //레디스에 저장된 에피소드 조회수 1 증가
        episodeViewCountService.incrementEpisodeViewCountInRedis(episodeId);
        //에피소드 정보 DTO로 변환하여 반환
        return EpisodeDetailDto.builder()
                .episodeId(episodeId)
                .content(episode.getContent())
                .title(episode.getTitle())
                .build();
    }

    private void validCoinPurchase(String providerId,
                                   Episode episode,
                                   Integer coinCost) {
        //유저의 에피소드 결제 내역을 확인, 있을경우 true 없을경우 false 반환
        boolean result = coinUseHistoryService.hasMemberUsedCoinsForEpisode(providerId, episode.getId());
        //결제 내역이 없을경우 EpisodeNotPurchasedException 로 던짐
        if (!result) {
            //EpisodeNotPurchasedException 에 coinCost 정보도 함께 전달
            throw new EpisodeNotPurchasedException(
                    "에피소드 결제 내역 없음, providerId = " + providerId + ", episodeId = " + episode.getId(),
                    EpisodePaymentDto.builder().episodeId(episode.getId())
                            .title(episode.getTitle())
                            .coinCost(coinCost)
                            .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EpisodeDetailDto getBesideEpisode(String providerId, Long episodeId, IndexDirection direction) {
        //episode 엔티티 유무 확인, 없을경우 예외로 던짐
        Episode episode = episodeService.getEpisode(episodeId)
                .orElseThrow(() -> new NoSuchElementException("Episode 정보 없음"));

        //조건에 따라서 대상 에피소드의 다음 챕터 또는 이전 챕터를 가져온다.
        Integer besideChapter = episode.getChapter() + (direction == IndexDirection.NEXT ? 1 : -1);

        log.info("id={}, next={}", episode.getChapter(), besideChapter);
        Novel novel = episode.getNovel();

        do {
            Episode besideEpisode = episodeRepository.findByNovelAndChapter(novel.getId(), besideChapter)
                    .orElseThrow(() -> new IndexOutOfBoundsException("이미 맨 앞, 맨 뒤의 챕터입니다."));

            //DB에서 찾아온 에피소드가 ACTIVE 상태일경우 에피소드 반환.
            if (besideEpisode.getStatus().equals(EpisodeStatus.ACTIVE)) {
                return getEpisodeDetail(providerId, besideEpisode.getId());
            }
            besideChapter++;
        } while (true);

    }

    @Override
    @Transactional
    public void updateEpisodeViewCountFromRedis() {
        try {
            //Redis 에 캐싱된 에피소드별 조회수 정보를 List로 받아옴
            List<ViewCountIncreaseDto> viewCountFromRedis = episodeViewCountService.getEpisodeViewCountFromRedis();

            // Redis에서 받아온 정보가 없을 경우 메서드 종료
            if (viewCountFromRedis.isEmpty()) {
                log.info("updateEpisodeViewCountFromRedis 메서드 종료, Redis에 갱신된 조회수 정보 없음");
                return;
            }

            //Episode ID를 List 객체에 담음
            List<Long> episodeIds = viewCountFromRedis.stream()
                    .map(ViewCountIncreaseDto::getEpisodeId)
                    .collect(Collectors.toList());

            //episode ID 값으로 , DB 에서 Episode 엔티티 찾아  Map 객체에 저장
            //key 값은 episodeId,  value 는 엔티티
            Map<Long, Episode> episodes = episodeService.getEpisodeList(episodeIds)
                    .stream()
                    .collect(Collectors.toMap(Episode::getId, episode -> episode));


            //Episode 엔티티 총조회수(view 컬럼) 갱신, 갱신성공(true) 일경우에만 EpisodeViewCount 엔티티 데이터 갱신
            boolean result = updateEpisodeEntityViewColumn(episodes, viewCountFromRedis);
            if (result) {
                //EpisodeViewCount 테이블에 조회수 갱신
                episodeViewCountService.updateEpisodeViewCountEntity(episodes, viewCountFromRedis);
            } else {
                log.warn("조회수 갱신에 실패했습니다.");
            }
        } catch (Exception ex) {
            throw new ServiceMethodException("updateEpisodeViewCountFromRedis 메서드 조회수 갱신 실패", ex);
        }


    }

    //EpisodeViewCount 를 업데이트하는 메서드와 트랜잭션으로 묶어 사용!!!(무결성 훼손 방지)
    @Override
    @Transactional
    public boolean updateEpisodeEntityViewColumn(Map<Long, Episode> episodes, List<ViewCountIncreaseDto> viewCountIncreaseDtos) {
        try {
            List<Episode> updatedEpisodes = new ArrayList<>();
            for (ViewCountIncreaseDto dto : viewCountIncreaseDtos) {
                Episode episode = episodes.get(dto.getEpisodeId());
                if (episode == null) {
                    log.error("Episode 정보 없음, episodeId={}", dto.getEpisodeId());
                    continue; // 해당 ID를 건너뜀
                }
                episode.updateTotalView(dto.getViewCount());
                updatedEpisodes.add(episode);
            }
            //수정된 Episode 엔티티 저장
            episodeRepository.saveAll(updatedEpisodes);
            return true;
        } catch (Exception ex) {
            throw new ServiceMethodException("increaseEpisodeEntityViewFiled 에러발생, ", ex);
        }


    }
}
