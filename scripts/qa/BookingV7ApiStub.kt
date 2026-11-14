// Test-only counterpart of the API error boundary; never packaged into Android.
package com.iumrah.beta.core.network
sealed class APIException : Exception() {
    class Status(val code: Int) : APIException()
    class Server(val code: Int, val serverMessage: String) : APIException()
    data object InvalidResponse : APIException()
}
