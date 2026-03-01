#!/bin/bash
sed -i '/val currentIndex = currentQueueState.indexOfFirst { it.episode.id == episodeId }/,/}/c\                skipToNextEpisodeUseCase()' app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt
