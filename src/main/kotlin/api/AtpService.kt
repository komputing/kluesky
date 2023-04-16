package io.github.akiomik.seiun.api

import com.slack.eithernet.ApiResult
import com.slack.eithernet.ApiResultCallAdapterFactory
import com.slack.eithernet.DecodeErrorBody
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.akiomik.seiun.api.adapters.RecordJsonAdapterFactory
import io.github.akiomik.seiun.api.adapters.Union2JsonAdapter
import io.github.akiomik.seiun.api.adapters.Union4JsonAdapter
import io.github.akiomik.seiun.model.AtpError
import io.github.akiomik.seiun.model.app.bsky.actor.ProfileViewDetailed
import io.github.akiomik.seiun.model.app.bsky.feed.*
import io.github.akiomik.seiun.model.app.bsky.graph.*
import io.github.akiomik.seiun.model.app.bsky.notification.UpdateNotificationSeenInput
import io.github.akiomik.seiun.model.com.atproto.moderation.CreateReportInput
import io.github.akiomik.seiun.model.com.atproto.moderation.CreateReportOutput
import io.github.akiomik.seiun.model.com.atproto.repo.CreateRecordInput
import io.github.akiomik.seiun.model.com.atproto.repo.CreateRecordOutput
import io.github.akiomik.seiun.model.com.atproto.repo.DeleteRecordInput
import io.github.akiomik.seiun.model.com.atproto.repo.UploadBlobOutput
import io.github.akiomik.seiun.model.com.atproto.server.*
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.io.IOException
import java.util.*

const val GMT_ID = "GMT"
val TIMEZONE_Z: TimeZone = TimeZone.getTimeZone(GMT_ID)
fun format(date: Date?): String? {
    val calendar: Calendar = GregorianCalendar(TIMEZONE_Z, Locale.US)
    calendar.time = date

    // estimate capacity of buffer as close as we can (yeah, that's pedantic ;)
    val capacity = "yyyy-MM-ddThh:mm:ss.sssZ".length
    val formatted = StringBuilder(capacity)
    padInt(formatted, calendar[Calendar.YEAR], "yyyy".length)
    formatted.append('-')
    padInt(formatted, calendar[Calendar.MONTH] + 1, "MM".length)
    formatted.append('-')
    padInt(formatted, calendar[Calendar.DAY_OF_MONTH], "dd".length)
    formatted.append('T')
    padInt(formatted, calendar[Calendar.HOUR_OF_DAY], "hh".length)
    formatted.append(':')
    padInt(formatted, calendar[Calendar.MINUTE], "mm".length)
    formatted.append(':')
    padInt(formatted, calendar[Calendar.SECOND], "ss".length)
    formatted.append('.')
    padInt(formatted, calendar[Calendar.MILLISECOND], "sss".length)
    formatted.append('Z')
    return formatted.toString()
}

private fun padInt(buffer: java.lang.StringBuilder, value: Int, length: Int) {
    val strValue = Integer.toString(value)
    for (i in length - strValue.length downTo 1) {
        buffer.append('0')
    }
    buffer.append(strValue)
}


class DummyRfc3339DateJsonAdapter : JsonAdapter<Date?>() {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): Date? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        val string = reader.nextString()
        return Date()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun toJson(writer: JsonWriter, value: Date?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(format(value))
        }
    }
}
interface AtpService {
    companion object {
        private val moshi = Moshi.Builder()
            .add(Date::class.java, DummyRfc3339DateJsonAdapter())
            .add(Union2JsonAdapter.Factory)
            .add(Union4JsonAdapter.Factory)
            .add(RecordJsonAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()
        private val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)
        private val client = OkHttpClient.Builder().addInterceptor(logging).build()

        fun create(serviceProvider: String): AtpService {
            return Retrofit.Builder()
                .baseUrl("https://$serviceProvider/xrpc/")
                .client(client)
                .addConverterFactory(CustomApiResultConverterFactory)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .addCallAdapterFactory(ApiResultCallAdapterFactory)
                .build()
                .create()
        }
    }

    @DecodeErrorBody
    @POST("com.atproto.server.createAccount")
    suspend fun createAccount(
        @Body body: CreateAccountInput
    ): ApiResult<CreateAccountOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.server.createSession")
    suspend fun createSession(
        @Body body: CreateSessionInput
    ): ApiResult<CreateSessionOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.server.refreshSession")
    suspend fun refreshSession(
        @Header("Authorization") authorization: String
    ): ApiResult<RefreshSessionOutput, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.actor.getProfile")
    suspend fun getProfile(
        @Header("Authorization") authorization: String,
        @Query("actor") actor: String
    ): ApiResult<ProfileViewDetailed, AtpError>

//    @DecodeErrorBody
//    @POST("com.atproto.repo.putRecord")
//    suspend fun updateProfile(
//        @Header("Authorization") authorization: String,
//        @Body body: UpdateProfileInput
//    ): ApiResult<UpdateProfileOutput, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.feed.getTimeline")
    suspend fun getTimeline(
        @Header("Authorization") authorization: String,
        @Query("algorithm") algorithm: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): ApiResult<Timeline, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.feed.getAuthorFeed")
    suspend fun getAuthorFeed(
        @Header("Authorization") authorization: String,
        @Query("actor") actor: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): ApiResult<AuthorFeed, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.createRecord")
    suspend fun createPost(
        @Header("Authorization") authorization: String,
        @Body body: CreateRecordInput<Post>
    ): ApiResult<CreateRecordOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.createRecord")
    suspend fun repost(
        @Header("Authorization") authorization: String,
        @Body body: CreateRecordInput<Repost>
    ): ApiResult<CreateRecordOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.deleteRecord")
    suspend fun deleteRecord(
        @Header("Authorization") authorization: String,
        @Body body: DeleteRecordInput
    ): ApiResult<Unit, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.createRecord")
    suspend fun like(
        @Header("Authorization") authorization: String,
        @Body body: CreateRecordInput<Like>
    ): ApiResult<CreateRecordOutput, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.notification.listNotifications")
    suspend fun listNotifications(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): ApiResult<io.github.akiomik.seiun.model.app.bsky.notification.Notifications, AtpError>

    @DecodeErrorBody
    @POST("app.bsky.notification.updateSeen")
    suspend fun updateNotificationSeen(
        @Header("Authorization") authorization: String,
        @Body body: UpdateNotificationSeenInput
    ): ApiResult<Unit, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.uploadBlob")
    suspend fun uploadBlob(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): ApiResult<UploadBlobOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.moderation.createReport")
    suspend fun createReport(
        @Header("Authorization") authorization: String,
        @Body body: CreateReportInput
    ): ApiResult<CreateReportOutput, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.graph.getFollows")
    suspend fun getFollows(
        @Header("Authorization") authorization: String,
        @Query("actor") actor: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): ApiResult<Follows, AtpError>

    @DecodeErrorBody
    @GET("app.bsky.graph.getFollowers")
    suspend fun getFollowers(
        @Header("Authorization") authorization: String,
        @Query("actor") actor: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null
    ): ApiResult<Followers, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.createRecord")
    suspend fun follow(
        @Header("Authorization") authorization: String,
        @Body body: CreateRecordInput<Follow>
    ): ApiResult<CreateRecordOutput, AtpError>

    @DecodeErrorBody
    @POST("com.atproto.repo.deleteRecord")
    suspend fun unfollow(
        @Header("Authorization") authorization: String,
        @Body body: DeleteRecordInput
    ): ApiResult<Unit, AtpError>

    @DecodeErrorBody
    @POST("app.bsky.graph.muteActor")
    suspend fun muteActor(
        @Header("Authorization") authorization: String,
        @Body body: MuteActorInput
    ): ApiResult<Unit, AtpError>

    @DecodeErrorBody
    @POST("app.bsky.graph.unmuteActor")
    suspend fun unmuteActor(
        @Header("Authorization") authorization: String,
        @Body body: UnmuteActorInput
    ): ApiResult<Unit, AtpError>
}
