package com.actuallyrizzn.voicekb

data class VeniceTextModel(
    val id: String,
    val displayName: String?,
    val description: String?,
    /** Lower is cheaper (sum of input + output USD pricing fields when present). */
    val pricingScore: Double,
)
