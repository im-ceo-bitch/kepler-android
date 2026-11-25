package com.iumrah.beta.data.hotel
fun main() {
    val a = "2026-11-25T23:30:00+03:00"
    fun c(d: String, o: String = "MED", dest: String = "FEG") = StorefrontReturnPolicy.compatible(a, d, "FEG", "MED", o, dest)
    check(!c("2026-11-25T10:00:00+03:00"))
    check(!c("2026-11-26T23:00:00+03:00"))
    check(c("2026-11-27T00:00:00+03:00"))
    check(c("2026-12-10T23:00:00+03:00"))
    check(!c("2026-12-11T00:00:00+03:00"))
    check(!c("2026-11-28T09:00:00+03:00", "JED"))
    check(c("2026-11-28T09:00:00+03:00", dest = "TAS"))
    check(!c("2026-11-28T09:00:00+03:00", dest = "SKD"))
    check(!c("invalid"))
    check(c("2026-11-26T22:00:00Z")) // Saudi Nov 27
    check(!StorefrontReturnPolicy.chronological(a, a))
    check(StorefrontReturnPolicy.chronological(a, "2026-11-26T00:00:00+03:00"))
    println("12 storefront return route/window assertions passed")
}
