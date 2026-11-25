package com.iumrah.beta.ui.home
import com.iumrah.beta.models.hotel.*
fun main() {
    fun leg(origin:String="TAS", destination:String="JED", date:String="2027-01-01T07:00:00Z", stops:Int=0)=StorefrontFlightLeg("Test", "TEST1", "HY",origin,destination,date,date,240,stops,"economy")
    fun row(id:String, fare:Double=200.0, currency:String="USD", out:StorefrontFlightLeg=leg(), back:StorefrontFlightLeg?=null)=StorefrontFlightOption(id,"one_way",currency=currency,travelerCount=1,totalFare=fare,perTravelerFare=fare,observedAt="",outbound=out,inbound=back)
    val result=flattenGlobalPublished(listOf(row("b",back=leg("JED","TAS","2027-01-10T07:00:00Z")),row("a",out=leg("FEG","MED")),row("a",out=leg("FEG","MED")),row("c",currency="SAR"),row("d",fare=0.0),row("e",fare=Double.NaN),row("f",out=leg(stops=1))))
    check(result.size==6); check(result.first().rowID=="a"); check(result.first().leg.origin=="FEG")
    check(result.count{it.rowID=="b"}==2); check(result.filter{it.rowID=="b"}.all{it.pair && it.selectable()!=null})
    check(result.filter{it.rowID in listOf("c","d","e")}.all{it.price==null && it.selectable()==null})
    check(result.none{it.rowID=="f"}); check(result.last().leg.origin=="JED")
    check(flattenGlobalPublished((1..400).map{row("flight-$it")}).size==400)
    check(flattenGlobalPublished(emptyList()).isEmpty())
    println("Home catalogue JVM smoke: 10 checks passed")
}
