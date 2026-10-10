package com.personalticktick.app.binding

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.personalticktick.app.data.SyncApi
import com.personalticktick.app.data.SyncRequest
import com.personalticktick.app.data.TaskDatabase
import com.personalticktick.app.data.toDomain
import com.personalticktick.app.data.toEntity
import com.personalticktick.app.domain.SyncState
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.net.URI
import java.time.Instant

data class ServerBinding(val baseUrl: String, val accessToken: String = "")

data class BindingPayload(
    val version: Int,
    @SerializedName("base_url") val baseUrl: String,
    val token: String,
    @SerializedName("expires_at") val expiresAt: String
) {
    companion object {
        fun parse(raw: String, nowEpochSeconds: Long = Instant.now().epochSecond): BindingPayload {
            val decoded = try { Gson().fromJson(raw, BindingPayload::class.java) }
            catch (_: JsonParseException) { throw InvalidBindingCode() }
            catch (_: RuntimeException) { throw InvalidBindingCode() }
            if (decoded == null || decoded.version != 1 || decoded.token.isBlank()) throw InvalidBindingCode()
            val normalized = normalizeHttpUrl(decoded.baseUrl)
            val expiry = try { Instant.parse(decoded.expiresAt) } catch (_: Exception) { throw InvalidBindingCode() }
            if (expiry.epochSecond <= nowEpochSeconds) throw ExpiredBindingCode()
            return decoded.copy(baseUrl = normalized)
        }

        private fun normalizeHttpUrl(value: String): String {
            val uri = try { URI(value.trim()) } catch (_: Exception) { throw InvalidBindingCode() }
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
                throw InvalidBindingCode()
            }
            return value.trim().trimEnd('/') + "/"
        }
    }
}

class InvalidBindingCode : IllegalArgumentException("invalid binding code")
class ExpiredBindingCode : IllegalArgumentException("expired binding code")

enum class StartupDestination { BIND, TASKS }

object StartupPolicy {
    fun destination(configuredBaseUrl: String, savedBinding: ServerBinding?): StartupDestination =
        if (configuredBaseUrl.isNotBlank() || savedBinding != null) StartupDestination.TASKS else StartupDestination.BIND
}

class BindingStore(context: Context) {
    private val preferences = context.getSharedPreferences("server_binding", Context.MODE_PRIVATE)
    fun read(): ServerBinding? = preferences.getString("base_url", null)?.let {
        ServerBinding(it, preferences.getString("access_token", "").orEmpty())
    }
    fun save(binding: ServerBinding) {
        preferences.edit().putString("base_url", binding.baseUrl).putString("access_token", binding.accessToken).apply()
    }
}

data class ConsumeBindingRequest(val token: String)
data class ConsumeBindingResponse(val ok: Boolean, @SerializedName("access_token") val accessToken: String)

interface BindingApi {
    @POST("api/bind/consume")
    suspend fun consume(@Body request: ConsumeBindingRequest): ConsumeBindingResponse
}

data class ServerApis(val binding: BindingApi, val sync: SyncApi)

object ServerApiFactory {
    fun create(baseUrl: String, accessToken: String = ""): ServerApis {
        val client = OkHttpClient.Builder().apply {
            if (accessToken.isNotBlank()) addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $accessToken").build())
            }
        }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return ServerApis(retrofit.create(BindingApi::class.java), retrofit.create(SyncApi::class.java))
    }
}

class BindingCoordinator(
    private val database: TaskDatabase,
    private val store: BindingStore,
    private val apiFactory: (String) -> ServerApis = { ServerApiFactory.create(it) }
) {
    suspend fun bind(payload: BindingPayload): Int {
        val exchange = apiFactory(payload.baseUrl).binding.consume(ConsumeBindingRequest(payload.token))
        check(exchange.ok) { "binding rejected" }
        val count = ServerBootstrapper(database, ServerApiFactory.create(payload.baseUrl, exchange.accessToken).sync).replaceFromServer()
        store.save(ServerBinding(payload.baseUrl, exchange.accessToken))
        return count
    }
}

/** Pull-only bootstrap. It deliberately submits an empty upload set and replaces Room in one transaction. */
class ServerBootstrapper(private val database: TaskDatabase, private val api: SyncApi) {
    suspend fun replaceFromServer(): Int {
        val snapshot = api.sync(SyncRequest(emptyList())).tasks
        val entities = snapshot.map { it.toDomain().toEntity(SyncState.SYNCED) }
        database.taskDao().replaceAllFromServer(entities)
        return entities.size
    }
}
