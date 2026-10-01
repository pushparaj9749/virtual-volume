package dev.virtualvolume.app.core.audio

import dev.virtualvolume.app.core.data.VolumeStreamType

/**
 * Applies volume changes through [VolumeBackend], always clamped to the device range.
 *
 * Deliberately free of Android framework types other than the injected backend, so the
 * clamping behaviour is covered by plain JVM tests.
 */
class VolumeController(backend: VolumeBackend) {

    private val reader = SafeVolumeReader(backend)
    private val backend: VolumeBackend = backend

    fun snapshot(stream: VolumeStreamType): VolumeState {
        val max = reader.maxVolume(stream)
        val index = reader.currentVolume(stream)
        return if (max <= 0 || index < 0) {
            VolumeState.UNKNOWN.copy(stream = stream)
        } else {
            VolumeState(stream = stream, index = index.coerceAtMost(max), max = max)
        }
    }

    /** Sets an absolute index. Values outside the device range are pulled inside it. */
    fun setIndex(stream: VolumeStreamType, index: Int): VolumeResult {
        val max = reader.maxVolume(stream)
        if (max <= 0) return VolumeResult.Failure("Volume stream unavailable")
        val target = VolumeMath.clamp(index, max)
        return runCatching { backend.setVolume(stream, target) }
            .fold(
                onSuccess = { VolumeResult.Success(VolumeState(stream, target, max)) },
                onFailure = { VolumeResult.Failure(it.message ?: "Volume change was blocked") },
            )
    }

    /** Moves the volume by [deltaSteps] steps of [stepSize] indexes each. */
    fun changeBy(stream: VolumeStreamType, deltaSteps: Int, stepSize: Int): VolumeResult {
        if (deltaSteps == 0) return VolumeResult.Success(snapshot(stream))
        val current = snapshot(stream)
        if (current.max <= 0) return VolumeResult.Failure("Volume stream unavailable")
        val safeStep = stepSize.coerceAtLeast(1)
        val target = VolumeMath.applyStep(current.index, deltaSteps, safeStep, current.max)
        if (target == current.index) return VolumeResult.Success(current)
        return runCatching { backend.setVolume(stream, target) }
            .fold(
                onSuccess = { VolumeResult.Success(VolumeState(stream, target, current.max)) },
                onFailure = { VolumeResult.Failure(it.message ?: "Volume change was blocked") },
            )
    }
}
