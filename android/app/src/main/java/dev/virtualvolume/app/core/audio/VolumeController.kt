package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Reads back every write, including Android's safe-volume and fixed-output restrictions. */
class VolumeController(private val backend: VolumeBackend) {
    private val _changes = MutableSharedFlow<VolumeState>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes = _changes.asSharedFlow()

    fun snapshot(stream: VolumeStreamType): VolumeState = runCatching {
        val min = backend.minVolume(stream)
        val max = backend.maxVolume(stream)
        val index = backend.currentVolume(stream)
        if (min < 0 || max <= 0 || max < min || index !in min..max) VolumeState.UNKNOWN.copy(stream = stream)
        else VolumeState(stream, index, max, min)
    }.getOrElse { VolumeState.UNKNOWN.copy(stream = stream) }

    fun setIndex(stream: VolumeStreamType, index: Int): VolumeResult {
        val before = snapshot(stream)
        if (!before.available) return VolumeResult.Failure("The audio service is unavailable. Try again or reconnect your audio device.")
        return write(stream, VolumeMath.clamp(index, before.max, before.min), before)
    }

    fun changeBy(stream: VolumeStreamType, deltaSteps: Int, stepSize: Int): VolumeResult {
        val before = snapshot(stream)
        if (!before.available) return VolumeResult.Failure("The audio service is unavailable. Open the app to retry.")
        val target = VolumeMath.applyStep(before.index, deltaSteps, stepSize.coerceAtLeast(1), before.max, before.min)
        return write(stream, target, before)
    }

    private fun write(stream: VolumeStreamType, target: Int, before: VolumeState): VolumeResult = runCatching {
        if (target == before.index) return@runCatching VolumeResult.Success(before, changed = false)
        if (backend.isFixedVolume()) return@runCatching VolumeResult.Failure("This output uses fixed volume. Adjust the connected speaker or TV instead.")
        backend.setVolume(stream, target)
        val actual = snapshot(stream)
        if (!actual.available) return@runCatching VolumeResult.Failure("The audio service did not return a volume reading. Please retry.")
        _changes.tryEmit(actual)
        if (actual.index == before.index) return@runCatching VolumeResult.Failure("Android kept the current level. Check safe-volume limits, Do Not Disturb, or your connected audio device.")
        VolumeResult.Success(actual, changed = actual.index != before.index)
    }.getOrElse {
        VolumeResult.Failure("Android blocked the volume change. Check Do Not Disturb or the connected audio device, then retry.")
    }
}
