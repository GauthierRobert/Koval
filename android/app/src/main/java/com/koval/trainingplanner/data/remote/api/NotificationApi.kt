package com.koval.trainingplanner.data.remote.api

import com.koval.trainingplanner.data.remote.dto.NotificationTokenRequest
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

interface NotificationApi {

    @POST("api/notifications/register-token")
    suspend fun registerToken(@Body request: NotificationTokenRequest)

    @HTTP(method = "DELETE", path = "api/notifications/unregister-token", hasBody = true)
    suspend fun unregisterToken(@Body request: NotificationTokenRequest)
}
