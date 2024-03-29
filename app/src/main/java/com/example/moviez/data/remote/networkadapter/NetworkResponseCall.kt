package com.example.moviez.data.remote.networkadapter

import android.util.Log
import com.example.moviez.data.models.ErrorsModel
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response

internal class NetworkResponseCall<S : Any, E : Any>(
        private val backingCall: Call<S>,
        private val errorConverter: Converter<ResponseBody, E>
) : Call<NetworkResponse<S, E>> {
    // REVIEW : 16/03/2020 FIXED :Change the default error message
    override fun enqueue(callback: Callback<NetworkResponse<S, E>>) = synchronized(this) {
        backingCall.enqueue(object : Callback<S> {
            override fun onResponse(call: Call<S>, response: Response<S>) {
                val body = response.body()
                val headers = response.headers()
                val code = response.code()
                val errorBody = response.errorBody()

                if (response.isSuccessful) {
                    if (body != null) {
                        callback.onResponse(this@NetworkResponseCall, Response.success(NetworkResponse.success(body, headers)))
                    } else {
                        // Response is successful but the body is null, so there's probably a server error here
                        callback.onResponse(this@NetworkResponseCall, Response.success(NetworkResponse.serverError(null, code, headers)))
                    }
                } else {
                    val convertedErrorBody = try { errorConverter.convert(errorBody) } catch (ex: Exception) { null }
                    var errorMessage = "Try again later. Contact support if problem persists"
                    if (convertedErrorBody is ErrorsModel){
                        convertedErrorBody.let { error ->
                            errorMessage = error.statusMessage.toString()
                        }
                    }
                    Log.e("Response Call Error ",response.toString())
                    Log.e("Response Call Error ",""+code)

                    if (code == 500){
                        errorMessage = "Try again later. Contact support if problem persists"
                    }
                    callback.onResponse(this@NetworkResponseCall, Response.success(NetworkResponse.serverError(convertedErrorBody,code,headers,errorMessage)))
                }
            }

            /**
             * Handles IOException or any other HTTPException
             */
            override fun onFailure(call: Call<S>, throwable: Throwable) {
                val networkResponse = throwable.extractNetworkResponse<S, E>(errorConverter)
                callback.onResponse(this@NetworkResponseCall, Response.success(networkResponse))
            }
        })
    }

    override fun isExecuted(): Boolean = synchronized(this) {
        backingCall.isExecuted
    }

    override fun clone(): Call<NetworkResponse<S, E>> = NetworkResponseCall(backingCall.clone(), errorConverter)

    override fun isCanceled(): Boolean = synchronized(this) {
        backingCall.isCanceled
    }

    override fun cancel() = synchronized(this) {
        backingCall.cancel()
    }

    override fun execute(): Response<NetworkResponse<S, E>> {
        throw UnsupportedOperationException("Network Response call does not support synchronous execution")
    }

    override fun request(): Request = backingCall.request()

    override fun timeout(): Timeout = backingCall.timeout()
}