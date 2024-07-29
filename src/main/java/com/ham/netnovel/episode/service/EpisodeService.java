package com.ham.netnovel.episode.service;

import com.ham.netnovel.episode.Episode;
import com.ham.netnovel.episode.dto.EpisodeCreateDto;
import com.ham.netnovel.episode.dto.EpisodeDataDto;
import com.ham.netnovel.episode.dto.EpisodeDeleteDto;
import com.ham.netnovel.episode.dto.EpisodeUpdateDto;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface EpisodeService {

    EpisodeDataDto getEpisode(Long episodeId);
    Optional<Episode> getEpisodeEntity(Long episodeId);
    EpisodeDataDto createEpisode(EpisodeCreateDto episodeCreateDto);
    EpisodeDataDto updateEpisode(EpisodeUpdateDto episodeUpdateDto);
    EpisodeDataDto deleteEpisode(EpisodeDeleteDto episodeDeleteDto);

    List<EpisodeDataDto> getEpisodesByNovel(Long novelId);
}