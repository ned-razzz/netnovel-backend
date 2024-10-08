package com.ham.netnovel.coinUseHistory.service;

import com.ham.netnovel.coinUseHistory.CoinUseHistory;
import com.ham.netnovel.coinUseHistory.CoinUseHistoryRepository;
import com.ham.netnovel.coinUseHistory.dto.CoinUseCreateDto;
import com.ham.netnovel.coinUseHistory.dto.NovelRevenueDto;
import com.ham.netnovel.common.exception.ServiceMethodException;
import com.ham.netnovel.common.utils.TypeValidationUtil;
import com.ham.netnovel.episode.Episode;
import com.ham.netnovel.episode.service.EpisodeService;
import com.ham.netnovel.member.Member;
import com.ham.netnovel.member.dto.MemberCoinUseHistoryDto;
import com.ham.netnovel.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Slf4j
public class CoinUseHistoryServiceImpl implements CoinUseHistoryService {

    private final CoinUseHistoryRepository coinUseHistoryRepository;

    private final MemberService memberService;

    private final EpisodeService episodeService;

    public CoinUseHistoryServiceImpl(CoinUseHistoryRepository coinUseHistoryRepository, MemberService memberService, EpisodeService episodeService) {
        this.coinUseHistoryRepository = coinUseHistoryRepository;
        this.memberService = memberService;
        this.episodeService = episodeService;
    }

    @Override
    @Transactional
    public void saveCoinUseHistory(CoinUseCreateDto coinUseCreateDto) {
        //유저 정보 유무 확인 확인
        Member member = memberService.getMember(coinUseCreateDto.getProviderId())
                .orElseThrow(() -> new NoSuchElementException("Member 정보가 없습니다. providerId: " + coinUseCreateDto.getProviderId()));

        //에피소드 정보 유무 확인
        Episode episode = episodeService.getEpisode(coinUseCreateDto.getEpisodeId())
                .orElseThrow(() -> new NoSuchElementException("Episode 정보가 없습니다. episodeId: " + coinUseCreateDto.getEpisodeId()));

        Integer usedCoins = coinUseCreateDto.getUsedCoins();

        //유효성검사, null이거나 음수면 예외로 던짐
        TypeValidationUtil.validateCoinAmount(usedCoins);

        //유저의 코인수 차감, 유저 코인수가 null 이거나 현재 코인수가 사용 코인수보다 작을경우 예외로 던짐
        memberService.deductMemberCoins(member.getProviderId(), usedCoins);
        try {
            //새로운 코인 사용 기록 엔티티 생성
            CoinUseHistory coinUseHistory = CoinUseHistory.builder()
                    .member(member)//유저 엔티티
                    .episode(episode)//에피소드 엔티티
                    .amount(usedCoins)//사용한 코인 수
                    .build();
            //DB에 저장
            coinUseHistoryRepository.save(coinUseHistory);

        } catch (Exception ex) {
            throw new ServiceMethodException("saveCoinUseHistory 메서드에서 오류 발생" + ex.getMessage());
        }

    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCoinUseHistoryDto> getMemberCoinUseHistory(String providerId, Pageable pageable) {
        try {
            return coinUseHistoryRepository.findByMemberProviderId(providerId, pageable)
                    .stream()//유저의 코인 사용 기록을 받아와 stream 생성
                    .map(coinUseHistory -> {//엔티티 정보 DTO에 바인딩
                                Episode episode = coinUseHistory.getEpisode();
                                return MemberCoinUseHistoryDto.builder()
                                        .episodeTitle(episode.getTitle())//에피소드 제목
                                        .createdAt(coinUseHistory.getCreatedAt())//결제 시간
                                        .usedCoin(coinUseHistory.getAmount())//사용한 코인수
                                        .episodeNumber(episode.getChapter())//에피소드 번호
                                        .novelTitle(episode.getNovel().getTitle())//소설 제목
                                        .build();
                            }
                    ).toList();

        } catch (Exception ex) {
            throw new ServiceMethodException("getMemberCoinUseHistory 메서드 에러 발생" + ex.getMessage());
        }


    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasMemberUsedCoinsForEpisode(String providerId, Long episodeId) {

        try {

            return coinUseHistoryRepository.findByMemberAndEpisode(providerId, episodeId)
                    .map(coinUseHistory -> {
                        Integer coinAmount = coinUseHistory.getAmount();
                        TypeValidationUtil.validateCoinAmount(coinAmount);//코인 수 유효성 검사,null이거나 음수면 예외로 던짐
                        return true;//유효성 검사 통과시 true 반환(코인 사용 기록 있음)
                    }).orElse(false);//DB에서 엔티티 검색에 실패한경우 false 반환(코인 사용 기록이 없음)


        } catch (Exception ex) {
            throw new ServiceMethodException("hasMemberUsedCoinsForEpisode 메서드 에러 발생" + ex.getMessage());

        }
    }


    @Override
    @Transactional(readOnly = true)
    public List<NovelRevenueDto> getCoinUseHistoryByNovelAndDate(
            List<Long> novelIds,
            LocalDate startDate,
            LocalDate endDate) {

        //반환을 위한 NovelRevenueDto List 객체 생성
        List<NovelRevenueDto> novelRevenueDtos = new ArrayList<>();

        //startDate 00시00분으로 객체 생성
        LocalDateTime startDateTime = startDate.atStartOfDay();

        //endDate 다음날 00시00분으로 객체 생성
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        try {

            //DB 에서 데이터 조회
            List<Object[]> byNovelAndDateTime = coinUseHistoryRepository.findByNovelAndDateTime(novelIds, startDateTime, endDateTime);

            for (Object[] result : byNovelAndDateTime) {

                Long novelId = (Long) result[0];
                String novelTitle = (String) result[1];
                int totalEarnCoins = ((Number) result[2]).intValue();
                String providerId = (String) result[3];

                //조호된 레코드로 DTO 생성
                NovelRevenueDto build = NovelRevenueDto.builder()
                        .novelId(novelId)
                        .novelTitle(novelTitle)
                        .providerId(providerId)
                        .totalCoins(totalEarnCoins)
                        .settlementStartDate(startDate)
                        .settlementEndDate(endDate)
                        .build();

                //반환용 List 객체에 DTO 추가
                novelRevenueDtos.add(build);

            }
            return novelRevenueDtos;
        } catch (Exception ex) {
            throw new ServiceMethodException("getCoinUseHistoryByNovelAndDate 메서드 에러" + ex + ex.getMessage());

        }

    }


}
