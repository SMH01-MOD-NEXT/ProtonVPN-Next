package ru.protonmod.next.data.network.eventbypass

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface EventBypassApi {
    @GET
    suspend fun getEventBypassConfig(@Url url: String): Response<ResponseBody>
}
