#!/bin/bash
awk '
/if \(isPlayingDismissed\)/ {
    print $0
    print "                skipToNextEpisodeUseCase()"
    print "            }"
    print "        }"
    print "    }"
    print ""
    print "}"
    exit
}
{ print }
' app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt > app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt.tmp && mv app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt.tmp app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt
