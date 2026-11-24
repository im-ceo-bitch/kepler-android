package com.iumrah.beta.domain.nuitee
import com.iumrah.beta.models.nuitee.NuiteeHotel
object NuiteeRules {
    fun filterHotels(rows:List<NuiteeHotel>,stars:Int,query:String) = rows.filter {
        (stars==0 || it.roundedStars==stars) && it.name.contains(query,ignoreCase=true)
    }
}
