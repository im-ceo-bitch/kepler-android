package com.iumrah.beta.core.network
sealed class APIException:Exception(){class Status(val code:Int):APIException(); class Server(val code:Int,val serverMessage:String):APIException(); data object MissingBookingToken:APIException()}
