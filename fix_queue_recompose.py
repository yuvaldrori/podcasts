with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()""", """""")

content = content.replace("""        PlaybackControls(
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = { viewModel.playPause() },
            onSeekBackward = { viewModel.seekBackward() },
            onSeekForward = { viewModel.seekForward() },
            onToggleSpeed = { viewModel.toggleSpeed() },
            onSeekTo = { viewModel.seekTo(it) }
        )""", """        PlaybackControls(viewModel = viewModel)""")

content = content.replace("""@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit
) {""", """@Composable
fun PlaybackControls(
    viewModel: QueueViewModel
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    
    val onPlayPause = { viewModel.playPause() }
    val onSeekBackward = { viewModel.seekBackward() }
    val onSeekForward = { viewModel.seekForward() }
    val onToggleSpeed = { viewModel.toggleSpeed() }
    val onSeekTo: (Long) -> Unit = { viewModel.seekTo(it) }
""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
