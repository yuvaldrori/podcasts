package com.yuval.podcasts.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface PodcastApi {
    @GET
    suspend fun fetchRss(@Url url: String): Response<ResponseBody>
}