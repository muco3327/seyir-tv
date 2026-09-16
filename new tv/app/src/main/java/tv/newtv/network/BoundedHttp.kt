package tv.newtv.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HttpSample(val code: Int, val bytes: ByteArray, val contentType: String)

/** Cancellation closes the actual socket, including while reading a streaming body. */
suspend fun OkHttpClient.readBounded(request: Request, maxBytes: Int): HttpSample =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val sample = response.use {
                        val output = ByteArrayOutputStream()
                        response.body?.byteStream()?.use { input ->
                            val buffer = ByteArray(8192)
                            while (output.size() < maxBytes && !call.isCanceled()) {
                                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        HttpSample(response.code, output.toByteArray(), response.header("Content-Type").orEmpty())
                    }
                    if (continuation.isActive) continuation.resume(sample)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
