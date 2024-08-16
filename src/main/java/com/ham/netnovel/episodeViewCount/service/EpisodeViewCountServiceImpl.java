package com.ham.netnovel.episodeViewCount.service;

import com.ham.netnovel.common.exception.ServiceMethodException;
import com.ham.netnovel.common.utils.TypeValidationUtil;
import com.ham.netnovel.episode.Episode;
import com.ham.netnovel.episodeViewCount.EpisodeViewCount;
import com.ham.netnovel.episodeViewCount.EpisodeViewCountRepository;
import com.ham.netnovel.episodeViewCount.ViewCountIncreaseDto;
import com.ham.netnovel.novel.Novel;
import com.ham.netnovel.novelRanking.dto.NovelRankingUpdateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EpisodeViewCountServiceImpl implements EpisodeViewCountService {

    private static final String VIEW_HASH_KEY = "episode:views";

    // 가중치 상수 정의
    private static final int TODAY_VIEW_WEIGHT = 3;
    private static final int YESTERDAY_VIEW_WEIGHT = 2;


    private final EpisodeViewCountRepository episodeViewCountRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public EpisodeViewCountServiceImpl(EpisodeViewCountRepository episodeViewCountRepository, RedisTemplate<String, String> redisTemplate) {
        this.episodeViewCountRepository = episodeViewCountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public void updateEpisodeViewCountEntity(Map<Long, Episode> episodes, List<ViewCountIncreaseDto> viewCountIncreaseDtos) {

        try {
            //엔티티를 DB에 한번에 저장하기 위한 엔티티 List 객체 생성
            List<EpisodeViewCount> episodeViewCounts = new ArrayList<>();

            LocalDate today = LocalDate.now();//메서드 실행 날짜, DB 검색을 위해 생성

            for (ViewCountIncreaseDto dto : viewCountIncreaseDtos) {
                //DTO의 episodId를 key 값으로, Map 자료형에서 Episode 엔티티 꺼내옴
                Episode episode = episodes.get(dto.getEpisodeId());
                if (episode == null) {
                    log.error("Episode 정보 없음, episodeId={}", dto.getEpisodeId());
                    continue; // 해당 ID를 건너뜀

                }
                EpisodeViewCount episodeViewCount = episodeViewCountRepository
                        .findByEpisodeIdAndViewDate(episode.getId(), today)//DB 에서 EpisodeViewCount 엔티티가 있는지 조회(오늘 날짜로 에피소드 조회 기록이 있는지 확인)
                        .map(EpisodeViewCount -> EpisodeViewCount.increaseViewCount(dto.getViewCount()))//DB에 엔티티가 있으면, 파라미터로 받은 만큼 조회수를 증가시킴
                        .orElseGet(() -> {//DB에 엔티티가 없으면 엔티티를 새로 생성
                                    return EpisodeViewCount.builder()
                                            .viewDate(today)//오늘날짜
                                            .episode(episode)//조회수가 기록될 에피소드
                                            .viewCount(dto.getViewCount())//조회수 저장
                                            .build();

                                }
                        );
                episodeViewCounts.add(episodeViewCount);
                log.info("에피소드 조회수 갱신, episodeId = {}", episode.getId());
            }


            //엔티티 List DB에 저장
            episodeViewCountRepository.saveAll(episodeViewCounts);
            log.info("에피소드 조회수 갱신 완료, 총 에피소드 수 ={} ", episodeViewCounts.size());
            //ToDo 초기화 실패시 예외처리, 후처리
            redisTemplate.delete(VIEW_HASH_KEY);//Redis 조회수 정보 초기화


        } catch (Exception ex) {
            throw new ServiceMethodException("updateEpisodeViewCountEntity 메서드 에러" + ex);
        }
    }

    //ToDo 소설별점, 댓글 가중치 추가하여 알고리즘 완성
    @Override
    @Transactional
    public List<NovelRankingUpdateDto> getDailyRanking(LocalDate todayDate) {

        try {
            LocalDate yesterdayDate = todayDate.minusDays(1);


            /*
            DB에서 어제, 오늘 소설 조회수 정보 받아옴
            인덱스 0번은 Novel 엔티티, 1번은 조회수, 2번은 기록된 날짜
             */
            List<Object[]> novelTotalViews = episodeViewCountRepository.findNovelTotalViews(yesterdayDate, todayDate);
            //자료를 저장할 Map 객체 생성
            Map<Novel, Long> scoresOfNovel = new HashMap<>();

            //반복문을 돌려 scoresOfNovel 객체에 Novel 엔티티와 점수 저장
            for (Object[] novelTotalView : novelTotalViews) {
                Novel novel = (Novel) novelTotalView[0];//인덱스 0번은 Novel 엔티티
                Long views = (((Number) novelTotalView[1]).longValue()); // 총 조회수 (Long으로 처리)
                LocalDate viewDate = (LocalDate) novelTotalView[2];//인덱스 2번은 저장날짜

                //날짜 가중치 객체 생성, 오늘은 3 어제는 2 가중치를 가짐
                int weight = viewDate.equals(todayDate) ? TODAY_VIEW_WEIGHT : YESTERDAY_VIEW_WEIGHT;
                //가중치와 조회수를 곱하여 점수 계산
                long score = views * weight;
                // Map 자료형에 할당, Novel 값이 있는 경우 기존 value에 score를 더하여 저장
                scoresOfNovel.merge(novel, score, Long::sum);
            }

            //List 자료형으로 변환
            List<Map.Entry<Novel, Long>> entryList = new ArrayList<>(scoresOfNovel.entrySet());
            //점수 순서대로 내림차순 정렬
            entryList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            //DTO로 변환하여 List 객체에 담음
            List<NovelRankingUpdateDto> rankingUpdateDtos = entryList.stream()
                    .map(entry -> NovelRankingUpdateDto.builder()
                            .novel(entry.getKey())//0번인덱스 Novel 엔티티
                            .score(entry.getValue())//1번 인덱스 총 조회수
                            .build()).toList();

            //점수 순서대로 정렬된 DTO에 랭킹 정보 추가
            for (int i = 0; i < rankingUpdateDtos.size(); i++) {
                rankingUpdateDtos.get(i).setRanking(i + 1);
            }
            //반환
            return rankingUpdateDtos;

        } catch (Exception ex) {
            throw new ServiceMethodException("getDailyRanking 메서드 에러 발생: " + ex.getMessage(), ex);        }

    }

    @Override
    public void incrementEpisodeViewCountInRedis(Long episodeId) {
        //Hash 형태로 Redis에 저장
        redisTemplate.opsForHash().increment(VIEW_HASH_KEY, episodeId.toString(), 1);
    }

    @Override
    public List<ViewCountIncreaseDto> getEpisodeViewCountFromRedis() {
        //Redis로 부터 에피소드 조회수 자료 받아옴
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(VIEW_HASH_KEY);
        //DTO 리스트 반환
        return entries.entrySet()//Map 자료형을 Set 으로 변환
                .stream()
                .map(entry -> {
                    String episodeIdStr = (String) entry.getKey();//episodeId 값 String 으로 변환
                    String viewCountStr = (String) entry.getValue();//viewCount 값 String 으로 변환
                    try {
                        //String으로 변환된 episodeId 값 검증, null 이거나 long 이 아니면 예외로 던짐, 문제없으면  Long 으로 변환
                        Long episodeId = TypeValidationUtil.validateLong(episodeIdStr);
                        //viewCount 값 Integer 로 변환
                        Integer viewCount = Integer.parseInt(viewCountStr);
                        //viewCount 검증 로직 실행, null 이거나 int 타입 범위 초과시 예외로 던짐
                        TypeValidationUtil.validateViewCount(viewCount);

                        return ViewCountIncreaseDto
                                .builder()//DTO로 변환
                                .episodeId(episodeId)//episode Id 값 할당
                                .viewCount(viewCount)//viewCount 값 할당
                                .build();
                    } catch (Exception ex) {
                        throw new ServiceMethodException("getEpisodeViewCountFromRedis 메서드 에러 발생 내용 = " + ex);
                    }

                })


                .collect(Collectors.toList());
    }

};
