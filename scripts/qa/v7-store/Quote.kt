package com.iumrah.beta.domain.pricing
class PackageQuote(val quoteProof:String?,val bookingAllowed:Boolean?,val quoteId:String?) { val hasVerifiedBreakdown=true; fun allowsOccupancy(adults:Int,rooms:Int)=adults>0 && rooms>0 }

class PackageStayPlan
