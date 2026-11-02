package fr.retroengine

/**
 * Loads the packaged Nestopia libretro shared library.
 *
 * Loading the shared library does not create a libretro frontend and does not
 * make ROM execution available by itself.
 */
data class NativeCoreLoadResult(
    val loaded: Boolean,
    val message: String
)

object NestopiaCoreLoader {
    @Volatile
    private var cachedResult: NativeCoreLoadResult? = null

    fun load(): NativeCoreLoadResult {
        cachedResult?.let { return it }

        val result = try {
            System.loadLibrary("nestopia_libretro")
            NativeCoreLoadResult(
                loaded = true,
                message = "Cœur NES Nestopia : bibliothèque native chargée. Frontend libretro encore requis."
            )
        } catch (error: UnsatisfiedLinkError) {
            NativeCoreLoadResult(
                loaded = false,
                message = "Cœur NES Nestopia : bibliothèque native indisponible pour cette architecture Android."
            )
        } catch (error: SecurityException) {
            NativeCoreLoadResult(
                loaded = false,
                message = "Cœur NES Nestopia : Android a refusé le chargement de la bibliothèque native."
            )
        }

        cachedResult = result
        return result
    }
}
