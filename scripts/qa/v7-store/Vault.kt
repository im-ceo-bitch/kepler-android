package com.iumrah.beta.core.security
class SecureJsonStore(var rows: List<com.iumrah.beta.models.booking.StoredBookingSession>) {
 @Suppress("UNCHECKED_CAST") fun <T> read(key:String, serializer:T):T? = rows as T
 fun <T> write(key:String,value:T,serializer:T) { @Suppress("UNCHECKED_CAST") val v=value as List<com.iumrah.beta.models.booking.StoredBookingSession>; rows=v }
}
