/*
 * Copyright 2025 Pera Wallet, LDA
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.algorand.android.network

import com.algorand.android.models.Result
import com.algorand.android.exceptions.RetrofitErrorHandler
import java.io.IOException
import retrofit2.Response
import kotlin.Result as KotlinResult // Alias to avoid clash with local Result

/**
 * Wrap a suspending API [call] in try/catch. In case an exception is thrown, a [Result.Error] is
 * created based on the [errorMessage].
 */
suspend fun <T : Any> safeApiCall(call: suspend () -> Result<T>): Result<T> {
    return try {
        call()
    } catch (e: Exception) {
        // An exception was thrown when calling the API so we're converting this to an IOException
        Result.Error(IOException(null, e))
    }
}

suspend fun <T : Any> request(
    onFailed: ((Response<T>) -> Result<T>)? = null,
    doRequest: suspend () -> Response<T>
): Result<T> {
    return safeApiCall {
        with(doRequest()) {
            if (isSuccessful && body() != null) {
                Result.Success(body() as T)
            } else {
                onFailed?.invoke(this) ?: Result.Error(Exception(errorBody().toString()), code())
            }
        }
    }
}

suspend fun <T : Any> requestWithHipoErrorHandler(
    hipoApiErrorHandler: RetrofitErrorHandler,
    doRequest: suspend () -> Response<T>
): Result<T> {
    return request(
        doRequest = doRequest,
        onFailed = { errorResponse -> hipoApiErrorHandler.getMessageAsResultError(errorResponse) }
    )
}

fun <T> RetrofitErrorHandler.getMessageAsResultError(response: Response<T>): Result.Error {
    return Result.Error(Exception(parse(response).message), response.code())
}

/**
 * Helper function to handle API calls with cursor-based pagination.
 *
 * @param R The type of the successful Response body.
 * @param T The type of the pagination token (e.g., String, Long).
 * @param request A suspending lambda that takes the current token (or null for the first call)
 *                and returns the Retrofit Response.
 * @param onResult A lambda to process the successful response body (e.g., add items to a list).
 * @param getNextToken A lambda to extract the next pagination token from the response body.
 * @return A Kotlin Result indicating success (Unit) or failure (Exception).
 */
suspend inline fun <R : Any, T> requestWithPagination(
    crossinline request: suspend (nextToken: T?) -> Response<R>,
    crossinline onResult: (response: R) -> Unit,
    crossinline getNextToken: (response: R) -> T?
): KotlinResult<Unit> {
    var nextToken: T? = null
    var isFirstRequest = true
    return try {
        while (nextToken != null || isFirstRequest) {
            isFirstRequest = false
            val response = request(nextToken)
            if (!response.isSuccessful || response.body() == null) {
                // TODO: Improve error creation/propagation from Response
                val errorBody = response.errorBody()?.string() ?: "Unknown pagination error"
                return KotlinResult.failure(IOException("Pagination request failed: code=${response.code()}, $errorBody"))
            }
            val responseBody = response.body()!!
            onResult(responseBody)
            nextToken = getNextToken(responseBody)
        }
        KotlinResult.success(Unit)
    } catch (e: Exception) {
        KotlinResult.failure(e)
    }
}
