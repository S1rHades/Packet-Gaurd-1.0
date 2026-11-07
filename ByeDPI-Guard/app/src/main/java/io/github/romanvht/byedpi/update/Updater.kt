package io.github.romanvht.byedpi.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.content.edit
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.utility.getPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

private const val TAG = "Updates"
private const val REPO = "S1rHades/Packet-Gaurd-1.0"
private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
private const val USER_AGENT = "PacketGuard-Updater"

/** A failure with the plain-language reason to show for it. */
class UpdateException(val problem: UpdateProblem, message: String? = null) : Exception(message ?: problem.name)

private fun problemOf(e: Throwable): UpdateProblem = when (e) {
    is UpdateException -> e.problem
    is UnknownHostException -> UpdateProblem.Offline
    is SocketTimeoutException -> UpdateProblem.Timeout
    is SSLException -> UpdateProblem.Secure
    else -> UpdateProblem.Download
}

/**
 * Finds, downloads and installs a newer Packet Guard from the repository's GitHub releases.
 * Android's installer asks the user to confirm; releases are signed with the same key, so the
 * update installs over this copy and keeps its settings.
 */
class Updater(private val context: Context) {

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (c.responseCode != 200) throw UpdateException(UpdateProblem.Server, "GitHub answered ${c.responseCode}")
            parseRelease(c.inputStream.bufferedReader().readText()) ?: throw UpdateException(UpdateProblem.NoApk)
        } finally {
            c.disconnect()
        }
    }

    /** Whether Android lets Packet Guard install apps; the first update asks for it. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where Packet Guard can be allowed to install apps. */
    @android.annotation.SuppressLint("InlinedApi")
    fun askToInstall(): Boolean = runCatching {
        context.startActivity(
            // Only reached on Android 8+, where canInstall() can be false.
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    /** Downloads [release]'s APK into the cache, reporting 0 to 1 as it goes. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = updatesDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "PacketGuard-${release.version}.apk")
        val links = listOf(
            release.apkUrl,
            "https://github.com/$REPO/releases/download/v${release.version}/PacketGuard-v${release.version}-universal.apk"
        ).distinct()
        var problem: Exception? = null
        for (link in links) {
            try {
                fetch(link, file, release.sizeBytes, onProgress)
                if (file.length() > 0) return@withContext file
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download from $link failed", e)
                problem = e
            }
        }
        throw problem ?: UpdateException(UpdateProblem.Download, "The download was empty")
    }

    private suspend fun fetch(link: String, file: File, expected: Long, onProgress: (Float) -> Unit) {
        var url = link
        // GitHub sends the file from another host; follow redirects by hand so none is skipped.
        repeat(6) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = c.responseCode
                when {
                    code in 300..399 -> {
                        val next = c.getHeaderField("Location") ?: throw UpdateException(UpdateProblem.Server, "No download link")
                        url = URL(URL(url), next).toString()
                    }
                    code == 200 -> {
                        // contentLengthLong needs Android 7; the header works everywhere.
                        val total = c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: expected
                        var read = 0L
                        c.inputStream.use { input ->
                            file.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    read += n
                                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                        if (total > 0 && read < total) throw UpdateException(UpdateProblem.Download, "The download was cut short")
                        return
                    }
                    else -> throw UpdateException(UpdateProblem.Server, "GitHub answered $code")
                }
            } finally {
                c.disconnect()
            }
        }
        throw UpdateException(UpdateProblem.Server, "Too many redirects")
    }

    /** Hands the APK to Android's installer, which shows its own confirmation. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("packet-guard.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
    }

    /** Opens Android's own install screen for [apk], for phones whose session installer refuses. */
    fun installWithSystemScreen(apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    companion object {
        fun updatesDir(context: Context) = File(context.cacheDir, "updates")
    }
}

/**
 * Android's answer to an install: it needs the user to confirm (its screen is opened), or it
 * failed, in which case the system install screen is tried before giving up.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Android 10+ silently blocks activity starts from the background, so when the
                // user has left the app during the download, show it once they come back.
                if (!AppUpdates.inForeground) {
                    AppUpdates.pendingConfirm = confirm
                    return
                }
                runCatching { context.startActivity(confirm) }
                    .onFailure { AppUpdates.installFailed(UpdateProblem.Install) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts as the new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdates.installFailed(UpdateProblem.Cancelled)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> AppUpdates.installFailed(UpdateProblem.Conflict)
            PackageInstaller.STATUS_FAILURE_STORAGE -> AppUpdates.installFailed(UpdateProblem.Storage)
            else -> {
                Log.w(TAG, "Install failed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                val apk = Updater.updatesDir(context).listFiles()?.maxByOrNull { it.lastModified() }
                if (apk == null || !Updater(context).installWithSystemScreen(apk)) AppUpdates.installFailed(UpdateProblem.Install)
            }
        }
    }
}

/**
 * The app's update state, shared by the Connect banner and Settings → About. Checks a little
 * after the app opens and every 6 hours while it stays open, unless turned off.
 */
object AppUpdates {
    const val PREF_AUTO = "updates_auto_check"
    private const val PREF_LAST_VERSION = "updates_last_version"
    /** The notes of the release being installed: its version on the first line, then one note per line. */
    private const val PREF_PENDING_NOTES = "updates_pending_notes"
    private const val FIRST_CHECK_MS = 4_000L
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Whether the app's screen is showing; set by the activity. */
    @Volatile var inForeground = false

    /** Android's install confirmation, waiting for the app to come back to the foreground. */
    @Volatile var pendingConfirm: Intent? = null

    /** Called when the app's screen comes back: shows a waiting install confirmation. */
    fun onAppResumed(ctx: Context) {
        inForeground = true
        val confirm = pendingConfirm ?: return
        pendingConfirm = null
        runCatching { ctx.startActivity(confirm) }.onFailure { installFailed(UpdateProblem.Install) }
    }
    private val mutable = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = mutable.asStateFlow()
    private var loop: Job? = null
    private var work: Job? = null

    /** Set once after the app was updated, for the Connect screen's "What's new" card. */
    data class JustUpdated(val version: String, val notes: List<String>)

    private val justUpdatedState = MutableStateFlow<JustUpdated?>(null)
    val justUpdated: StateFlow<JustUpdated?> = justUpdatedState.asStateFlow()

    fun dismissWhatsNew() {
        justUpdatedState.value = null
    }

    val currentVersion: String get() = versionCore(BuildConfig.VERSION_NAME)

    fun autoCheck(ctx: Context): Boolean = ctx.getPreferences().getBoolean(PREF_AUTO, true)

    /** Starts the background checks for this app session (does nothing if they're off or running). */
    fun startAutoChecks(ctx: Context) {
        if (loop?.isActive == true || !autoCheck(ctx)) return
        val app = ctx.applicationContext
        loop = scope.launch {
            delay(FIRST_CHECK_MS)
            while (isActive) {
                if (!mutable.value.busy) check(app, manual = false)
                delay(CHECK_EVERY_MS)
            }
        }
    }

    fun setAutoCheck(ctx: Context, on: Boolean) {
        ctx.getPreferences().edit { putBoolean(PREF_AUTO, on) }
        if (on) startAutoChecks(ctx) else { loop?.cancel(); loop = null }
    }

    /**
     * The version this copy was updated to since it last ran, once; null otherwise. Also fills
     * [justUpdated] with that release's notes, saved before the install (or, when updated
     * from a version that didn't save them, from the next check).
     */
    fun consumeJustUpdated(ctx: Context): String? {
        val prefs = ctx.getPreferences()
        val last = prefs.getString(PREF_LAST_VERSION, null)
        val pending = prefs.getString(PREF_PENDING_NOTES, null)?.lines().orEmpty()
        prefs.edit {
            putString(PREF_LAST_VERSION, currentVersion)
            remove(PREF_PENDING_NOTES)
        }
        val updated = last != null && isNewer(currentVersion, last)
        if (!updated) return null
        val notes = if (pending.firstOrNull() == currentVersion) pending.drop(1).filter { it.isNotBlank() } else emptyList()
        justUpdatedState.value = JustUpdated(currentVersion, notes)
        return currentVersion
    }

    fun checkNow(ctx: Context) {
        if (mutable.value.busy) return
        val app = ctx.applicationContext
        work = scope.launch { check(app, manual = true) }
    }

    private suspend fun check(app: Context, manual: Boolean) {
        val previous = mutable.value
        mutable.value = previous.copy(phase = UpdatePhase.Checking, problem = null)
        try {
            val r = Updater(app).latest()
            justUpdatedState.update { j ->
                if (j != null && j.notes.isEmpty() && r.version == j.version) j.copy(notes = r.notes) else j
            }
            mutable.value = if (isNewer(r.version, currentVersion)) UpdateState(UpdatePhase.Available, r)
            else UpdateState(UpdatePhase.UpToDate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            // A quiet background check keeps what it knew; a tap shows why it failed.
            mutable.value = when {
                previous.release != null -> previous.copy(phase = UpdatePhase.Available, problem = if (manual) problemOf(e) else null)
                manual -> UpdateState(UpdatePhase.Failed, problem = problemOf(e))
                else -> previous.copy(phase = if (previous.phase == UpdatePhase.Checking) UpdatePhase.Idle else previous.phase)
            }
        }
    }

    /** Downloads and installs the available release; asks for install permission first if needed. */
    fun update(ctx: Context) {
        val release = mutable.value.release ?: return
        if (work?.isActive == true && mutable.value.busy) return
        val app = ctx.applicationContext
        val updater = Updater(app)
        if (!updater.canInstall()) {
            updater.askToInstall()
            mutable.update { it.copy(phase = UpdatePhase.Available, problem = UpdateProblem.NeedsPermission) }
            return
        }
        // Kept for the "What's new" card the new version shows when it first opens.
        app.getPreferences().edit {
            putString(PREF_PENDING_NOTES, (listOf(release.version) + release.notes.map { it.replace('\n', ' ') }).joinToString("\n"))
        }
        work = scope.launch {
            mutable.value = UpdateState(UpdatePhase.Downloading, release)
            try {
                val apk = updater.download(release) { p -> mutable.update { it.copy(progress = p) } }
                mutable.update { it.copy(phase = UpdatePhase.Installing, progress = 1f) }
                withContext(Dispatchers.IO) { updater.install(apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update failed", e)
                mutable.value = UpdateState(UpdatePhase.Available, release, problem = problemOf(e))
            }
        }
    }

    /** Called by [InstallResultReceiver] when Android's installer gave up. */
    fun installFailed(problem: UpdateProblem) {
        mutable.update { it.copy(phase = if (it.release != null) UpdatePhase.Available else UpdatePhase.Failed, problem = problem) }
    }
}
