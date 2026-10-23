package io.github.romanvht.byedpi.utility

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.coroutines.coroutineContext

data class SiteProbe(
    val site: String,
    val successCount: Int,
    val totalCount: Int,
    val avgLatencyMs: Long?,
    val httpCode: Int?
)

class SiteCheckUtils(
    private val proxyIp: String,
    private val proxyPort: Int
) {

    suspend fun checkSitesAsync(
        sites: List<String>,
        requestsCount: Int,
        requestTimeout: Long,
        concurrentRequests: Int = 20,
        fullLog: Boolean,
        onSiteChecked: (suspend (String, Int, Int) -> Unit)? = null,
        onSiteProbed: (suspend (SiteProbe) -> Unit)? = null
    ): List<Pair<String, Int>> {
        val semaphore = Semaphore(concurrentRequests)
        return withContext(Dispatchers.IO) {
            sites.map { site ->
                async {
                    semaphore.withPermit {
                        val probe = checkSiteAccess(site, requestsCount, requestTimeout)
                        val successCount = probe.successCount
                        coroutineContext.ensureActive()
                        if (fullLog) {
                            onSiteChecked?.invoke(site, successCount, requestsCount)
                            onSiteProbed?.invoke(probe.copy(totalCount = requestsCount))
                        }
                        site to successCount
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSiteAccess(
        site: String,
        requestsCount: Int,
        timeout: Long
    ): SiteProbe = withContext(Dispatchers.IO) {
        var responseCount = 0
        var latencySum = 0L
        var lastCode: Int? = null

        val formattedUrl = if (site.startsWith("http://") || site.startsWith("https://")) site
        else "https://$site"

        val url = try {
            URL(formattedUrl)
        } catch (_: Exception) {
            Log.e("SiteChecker", "Invalid URL: $formattedUrl")
            return@withContext SiteProbe(site, 0, requestsCount, null, null)
        }

        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))

        repeat(requestsCount) { attempt ->
            coroutineContext.ensureActive()
            Log.i("SiteChecker", "Attempt ${attempt + 1}/$requestsCount for $site")

            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = (timeout * 1000).toInt()
                connection.readTimeout = (timeout * 1000).toInt()
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Connection", "close")

                val startedAt = System.nanoTime()
                val responseCode = connection.responseCode
                val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
                val declaredLength = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }

                var actualLength = 0L
                try {
                    val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    if (inputStream != null) {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        val limit = if (declaredLength > 0) declaredLength else 1024L * 1024

                        while (actualLength < limit) {
                            coroutineContext.ensureActive()
                            val remaining = limit - actualLength
                            val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                            bytesRead = inputStream.read(buffer, 0, toRead)
                            if (bytesRead == -1) break
                            actualLength += bytesRead
                        }
                    }
                } catch (_: IOException) {
                    // Stream reading failed
                }

                if (declaredLength <= 0L || actualLength >= declaredLength) {
                    Log.i("SiteChecker", "Response for $site: $responseCode, Declared: $declaredLength, Actual: $actualLength")
                    responseCount++
                    latencySum += latencyMs
                    lastCode = responseCode
                } else {
                    Log.w("SiteChecker", "Block detected for $site, Declared: $declaredLength, Actual: $actualLength")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SiteChecker", "Error accessing $site: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        SiteProbe(
            site = site,
            successCount = responseCount,
            totalCount = requestsCount,
            avgLatencyMs = if (responseCount > 0) latencySum / responseCount else null,
            httpCode = lastCode
        )
    }
}
