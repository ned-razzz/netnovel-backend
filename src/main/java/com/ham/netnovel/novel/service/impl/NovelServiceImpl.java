package com.ham.netnovel.novel.service.impl;

import com.ham.netnovel.common.exception.ServiceMethodException;
import com.ham.netnovel.episode.Episode;
import com.ham.netnovel.novel.Novel;
import com.ham.netnovel.novel.repository.NovelRepository;
import com.ham.netnovel.novel.data.NovelStatus;
import com.ham.netnovel.novel.dto.*;
import com.ham.netnovel.novel.service.NovelService;
import com.ham.netnovel.novelAverageRating.NovelAverageRating;
import com.ham.netnovel.s3.S3Service;
import com.ham.netnovel.tag.dto.TagDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
public class NovelServiceImpl implements NovelService {
    private final NovelRepository novelRepository;
    private final S3Service s3Service;


    @Autowired
    public NovelServiceImpl(NovelRepository novelRepository, S3Service s3Service) {
        this.novelRepository = novelRepository;
        this.s3Service = s3Service;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Novel> getNovel(Long novelId) {
        return novelRepository.findById(novelId);
    }

    @Override
    public List<NovelInfoDto> getNovelsByAuthor(String providerId) {

        if (providerId ==null || providerId.isEmpty()){
            throw new IllegalArgumentException("getNovelIdsByAuthor 에러, providerId 값이 Null 이거나 비었습니다.");
        }

        return novelRepository.findNovelsByMember(providerId)
                .stream()
                .map(this::convertEntityToInfoDto)
                .toList();
    }

    //정산 계산시 사용
    @Override
    public List<Long> getNovelIdsByAuthor(String providerId) {
        if (providerId ==null || providerId.isEmpty()){
            throw new IllegalArgumentException("getNovelIdsByAuthor 에러, providerId 값이 Null 이거나 비었습니다.");
        }
        //novelIds List 객체 반환
        return novelRepository.findNovelsByMember(providerId)
                .stream()
                .map(Novel::getId)
                .toList();
    }


    @Override
    @Transactional
    public void deleteNovel(NovelDeleteDto novelDeleteDto) {
        log.info("Novel 삭제 = {}", novelDeleteDto.toString());

        //Novel DB 데이터 검증
        Novel targetNovel = novelRepository.findById(novelDeleteDto.getNovelId())
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 Novel 입니다."));

        try {
            //Novel 변경 권한 검증
            boolean isAuthor = novelDeleteDto.getAccessorProviderId().equals(targetNovel.getAuthor().getProviderId());
            if (!isAuthor) {
                throw new AccessDeniedException("해당 Novel에 접근 권한이 업습니다.");
            }
        } catch (Exception ex) {
            throw new ServiceMethodException("deleteNovel 메서드 에러 발생", ex.getCause());
        }

        //Novel 삭제 처리
        targetNovel.changeStatus(NovelStatus.DELETED_BY_USER);
        novelRepository.save(targetNovel);
    }

    @Override
    @Transactional(readOnly = true)
    public NovelInfoDto getNovelInfo(Long novelId) {
        //Novel DB 데이터 검증
        Novel targetNovel = novelRepository.findById(novelId)
                .orElseThrow(() -> new NoSuchElementException("getNovelInfo 에러, Novel 정보가 없습니다 novel id="+novelId));
        try {
            return convertEntityToInfoDto(targetNovel);
        } catch (Exception ex) {
            throw new ServiceMethodException("getNovelInfo 메서드 에러 발생: " + ex.getMessage());
        }
    }


    @Override
    @Transactional(readOnly = true)
    public List<Long> getRatedNovelIds() {
        try {
            return novelRepository.findByNovelRating()
                    .stream()
                    .map(Novel::getId)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            throw new ServiceMethodException("getRatedNovelIds 메서드 에러 발생", ex.getCause());

        }
    }



    @Override
    @Transactional
    public boolean updateNovelThumbnail(MultipartFile file, Long novelId, java.lang.String providerId) {
        //파라미터 null 체크
        if (file.isEmpty() || novelId == null || providerId == null) {
            throw new IllegalArgumentException("saveNovelThumbnail 메서드 에러, 파라미터가 null 입니다.");
        }
        /*
        Novel 엔티티를 DB에서 찾아옴
        만약 Novel 엔티티가 Null 이거나, 작가정보와 섬네일 업로드 요청자 정보가 일치하지 않는경우 예외로 던짐
         */
        Novel novel = getNovel(novelId)
                .filter(foundNovel -> foundNovel.getAuthor().getProviderId().equals(providerId))
                .orElseThrow(() -> new IllegalArgumentException("saveNovelThumbnail 메서드 에러, 섬네일 업로드 요청자와, 소설 작가가 다릅니다."));

        try {
            //AWS S3에 파일 업로드 후 파일명 반환 , S3 업로드 실패시 예외로 던져짐
            String fileName = s3Service.uploadFileToS3(file);
            //Novel 엔티티의 섬네일 필드값 수정
            novel.updateThumbnailFileName(fileName);
            //수정된 Novel 엔티티 DB에 저장
            novelRepository.save(novel);
            //true 반환
            return true;

        } catch (Exception ex) {
            throw new ServiceMethodException("saveNovelThumbnail 메서드 에러, 섬네일 변경에 실패했습니다." + ex + ex.getMessage());
        }

    }


    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getNovelWithTotalViews(Pageable pageable) {
        try {
            return novelRepository.findNovelTotalViews(pageable)
                    .stream()
                    .collect(Collectors.toMap(
                            object -> (Long) object[0],//NovelId key로 바인딩
                            object -> (Long) object[1])//totalViews value로 바인딩
                    );
        } catch (Exception ex) {
            throw new ServiceMethodException("getNovelWithTotalViews 메서드 에러" + ex + ex.getMessage());
        }
    }

    @Override
    public Map<Long, Integer> getNovelWithTotalFavorites(Pageable pageable) {
        try {
            return novelRepository.findNovelTotalFavorite(pageable)
                    .stream()
                    .collect(Collectors.toMap(
                                    object -> (Long) object[0],
                                    object -> ((Number) object[1]).intValue()
                            )
                    );
        } catch (Exception ex) {
            throw new ServiceMethodException("getNovelWithTotalFavorites 메서드 에러" + ex + ex.getMessage());
        }

    }

    @Override
    public Map<Long, LocalDateTime> getNovelWithLatestEpisodeCreateTime(Pageable pageable) {

        try {
            return novelRepository.findNovelLatestUpdatedEpisode(pageable)
                    .stream()
                    .collect(Collectors.toMap(
                                    object -> (Long) object[0],//NovelId key로 바인딩
                                    object -> (LocalDateTime) object[1]//가장 최근에 업로드된 에피소드의 LocalDateTime 값
                            )
                    );
        } catch (Exception ex) {
            throw new ServiceMethodException("getNovelWithLatestEpisodeCreateTime 메서드 에러" + ex + ex.getMessage());
        }
    }

    NovelInfoDto convertEntityToInfoDto(Novel novel) {
        //평균 별점 레코드가 없으면 0점짜리 새로 생성
        NovelAverageRating averageRating = Optional.ofNullable(novel.getNovelAverageRating())
                .orElse(NovelAverageRating.builder()
                        .novel(novel)
                        .averageRating(BigDecimal.valueOf(0))
                        .ratingCount(0)
                        .build());

        //작품의 태그들 가져오기
        List<TagDataDto> dataDtoList = novel.getNovelTags().stream()
                .map(novelTag -> novelTag.getTag().getData())
                .toList();

        //AWS cloud front 섬네일 이미지 URL 객체 반환
        String thumbnailUrl = s3Service.generateCloudFrontUrl(novel.getThumbnailFileName(), "original");

        //작품의 모든 에피소드 조회수 총합
        int viewsSum = novel.getEpisodes().stream().mapToInt(Episode::getView).sum();

        return NovelInfoDto.builder()
                .id(novel.getId())
                .title(novel.getTitle())
                .desc(novel.getDescription())
                .authorName(novel.getAuthor().getNickName())
                .views(viewsSum)
                .averageRating(averageRating.getAverageRating())
                .episodeCount(novel.getEpisodes().size())
                .favoriteCount(novel.getFavorites().size())
                .tags(dataDtoList)
                .thumbnailUrl(thumbnailUrl)//섬네일
                .build();
    }


}