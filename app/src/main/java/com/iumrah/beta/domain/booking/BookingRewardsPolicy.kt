package com.iumrah.beta.domain.booking

import com.iumrah.beta.models.account.IumrahBookingRewardsAmounts
import com.iumrah.beta.models.account.IumrahFriendsBookingSummary
import kotlin.math.abs

/** Server amounts are authoritative. Do not infer credit or discount from a tier or local package price. */
object BookingRewardsPolicy {
    private fun valid(values: List<Double>) = values.all { it.isFinite() && it >= 0 }
    private fun equal(a: Double, b: Double) = abs(a-b) < .005
    fun confirmed(rewards: IumrahBookingRewardsAmounts?, summary: IumrahFriendsBookingSummary?): Boolean {
        if (rewards == null || !valid(listOf(rewards.totalUsd,rewards.giftDiscountUsd,rewards.creditAppliedUsd,rewards.totalDiscountUsd,rewards.payableUsd))) return false
        if (!equal(rewards.giftDiscountUsd+rewards.creditAppliedUsd,rewards.totalDiscountUsd) || !equal(rewards.totalUsd-rewards.totalDiscountUsd,rewards.payableUsd)) return false
        return summary == null || summary.ok && equal(rewards.totalUsd,summary.totalUsd) && equal(rewards.giftDiscountUsd,summary.giftDiscountUsd) && equal(rewards.creditAppliedUsd,summary.creditAppliedUsd) && equal(rewards.totalDiscountUsd,summary.totalDiscountUsd) && equal(rewards.payableUsd,summary.payableUsd)
    }
    fun amounts(status: String, receiptsEmpty: Boolean, rewards: IumrahBookingRewardsAmounts?, summary: IumrahFriendsBookingSummary?, bookingID: String): List<Int> {
        if (!status.equals("payment_pending",true) || !receiptsEmpty || summary == null || summary.bookingID != bookingID || !summary.identityConfirmed || summary.creditAppliedUsd != 0.0 || !confirmed(rewards,summary)) return emptyList()
        if (!valid(listOf(summary.availableCreditUsd,summary.remainingAllowanceUsd,summary.maxDiscountUsd))) return emptyList()
        return listOf(100,200).filter { summary.availableCreditUsd >= it && summary.remainingAllowanceUsd >= it && summary.payableUsd >= it && summary.maxDiscountUsd-summary.totalDiscountUsd >= it }
    }
}
