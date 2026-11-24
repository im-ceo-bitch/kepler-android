package com.iumrah.beta.core.security
class SecureJsonStore(var rows: List<com.iumrah.beta.models.booking.StoredBookingSession>) {
 var readFailure = false
 var writes = 0
 fun <T> readStrict(key:String, serializer:T):T? { check(!readFailure);return read(key,serializer) }
 @Suppress("UNCHECKED_CAST") fun <T> read(key:String, serializer:T):T? = rows as T
 fun <T> write(key:String,value:T,serializer:T) { @Suppress("UNCHECKED_CAST") val v=value as List<com.iumrah.beta.models.booking.StoredBookingSession>; rows=v; writes++ }
}
