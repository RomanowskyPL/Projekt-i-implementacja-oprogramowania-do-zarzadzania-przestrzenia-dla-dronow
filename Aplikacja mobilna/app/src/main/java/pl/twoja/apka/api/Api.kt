package pl.twoja.apka.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pl.twoja.apka.model.FlightDetailItem
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class TableInfo(
    val table_name: String,
    val approx_row_count: Long?
)

data class NoteDto(
    val id: Long? = null,
    val title: String,
    val content: String
)

data class OperatorCreateDto(
    val imie: String,
    val nazwisko: String,
    val data_urodzenia: String? = null,
    val obywatelstwo: String? = null,
    val e_mail: String? = null,
    val status: String? = null
)

data class StartFlightRequest(
    val id_operatora: Int,
    val id_drona: Int,
    val id_trasy: Int,
    val id_typ: Int
)

data class StartFlightResponse(
    val id_lotu: Int,
    val czas_startu: String? = null,
    val status: String? = null,
    val id_operatora: Int? = null,
    val id_drona: Int? = null,
    val id_trasy: Int? = null,
    val id_typ: Int? = null
)

data class TelemetryCreateRequest(
    val lat: Double,
    val lon: Double,
    val wysokosc_m: Double? = null,
    val czas_ms: Long? = null
)

interface ApiService {

    @GET("metadata/tables")
    suspend fun listTables(): List<TableInfo>
    @GET("notes")
    suspend fun listNotes(): List<NoteDto>
    @POST("notes")
    suspend fun addNote(@Body n: NoteDto): NoteDto
    @GET("operator")
    suspend fun listOperator(): List<Map<String, Any?>>
    @GET("operator/{id}")
    suspend fun getOperator(@Path("id") id: Int): Map<String, Any?>
    @GET("operator/{id}/certyfikaty")
    suspend fun getOperatorCertificates(@Path("id") id: Int): List<Map<String, Any?>>
    @GET("operator/{id}/adres")
    suspend fun getOperatorAddress(@Path("id") id: Int): Map<String, Any?>
    @Multipart
    @POST("operator/{id}/avatar")
    suspend fun uploadOperatorAvatar(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part
    ): Map<String, Any?>
    @GET("drony/model")
    suspend fun listDroneModels(): List<Map<String, Any?>>
    @GET("drony/model/{id}")
    suspend fun getDroneModel(@Path("id") id: Int): Map<String, Any?>
    @GET("drony/model/{id}/egzemplarze")
    suspend fun getDroneInstances(@Path("id") id: Int): List<Map<String, Any?>>
    @GET("trasy")
    suspend fun getRoutes(): List<Map<String, Any?>>
    @GET("trasy/{id}")
    suspend fun getRoute(@Path("id") id: Int): Map<String, Any?>
    @GET("trasy/{id}/punkty")
    suspend fun getRoutePoints(@Path("id") id: Int): List<Map<String, Any?>>
    @GET("typ_lotu")
    suspend fun listFlightTypes(): List<Map<String, Any?>>
    @GET("lot")
    suspend fun getFlights(): List<Map<String, Any?>>
    @GET("lot/{id}")
    suspend fun getFlight(@Path("id") id: Int): Map<String, Any?>
    @GET("lot/{id}")
    suspend fun getDetails(@Path("id") id: Int): FlightDetailItem
    @GET("lot/{id}/route-points")
    suspend fun getFlightRoutePoints(@Path("id") id: Int): List<Map<String, Any?>>
    @GET("lot/{id}/telemetria")
    suspend fun getFlightTelemetry(@Path("id") id: Int): List<Map<String, Any?>>
    @POST("lot/start")
    suspend fun startFlight(@Body req: StartFlightRequest): StartFlightResponse
    @POST("lot/{id}/finish")
    suspend fun finishFlight(@Path("id") id: Int): Map<String, Any?>
    @POST("lot/{id}/abort")
    suspend fun abortFlight(@Path("id") id: Int): Map<String, Any?>
    @POST("lot/{id}/telemetria")
    suspend fun addFlightTelemetry(
        @Path("id") id: Int,
        @Body req: TelemetryCreateRequest
    ): Map<String, Any?>
}

object ApiClient {
    const val BASE_HOST = "" //dane do uzupełnienia
    private const val BASE_URL = "$BASE_HOST/api/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val log = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val ok = OkHttpClient.Builder()
        .addInterceptor(log)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ok)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ApiService::class.java)
}
