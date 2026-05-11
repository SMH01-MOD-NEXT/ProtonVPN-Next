package ru.protonmod.next.data.network.ota

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface UpdateApi {
    @GET
    suspend fun getUpdateMetadata(@Url url: String): Response<ResponseBody>
}
