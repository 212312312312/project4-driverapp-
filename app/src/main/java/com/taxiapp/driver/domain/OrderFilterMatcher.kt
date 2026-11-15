package com.taxiapp.driver.domain

import com.taxiapp.driver.network.DriverFilter
import com.taxiapp.driver.network.Order

/**
 * Доменний сервіс перевірки відповідності замовлень фільтрам водія
 * та підрахунку лічильників для нижньої панелі MainActivity і шапки EtherActivity.
 */
object OrderFilterMatcher {

    data class NavFilterCounters(
        val etherFilteredCount: Int,
        val etherTotalCount: Int,
        val autoCount: Int,
        val cycleCount: Int
    )

    fun matchesEtherFilter(order: Order, filter: DriverFilter): Boolean {
        if (filter.paymentType != "ANY" && !filter.paymentType.equals(order.paymentMethod, ignoreCase = true)) {
            return false
        }
        val orderPrice = order.getTotalFullPrice()
        if (filter.tariffType == "SIMPLE") {
            val minP = filter.minPrice ?: 0.0
            if (minP > 0.0 && orderPrice < minP) return false
            val minKm = filter.minPricePerKm ?: 0.0
            if (minKm > 0.0) {
                val distKm = (order.distanceMeters ?: 0) / 1000.0
                if (distKm > 0.0 && (orderPrice / distKm) < minKm) return false
            }
        } else {
            val cMin = filter.complexMinPrice ?: 0.0
            if (cMin > 0.0 && orderPrice < cMin) return false
        }
        return true
    }

    fun calculateBottomNavCounters(
        allOrders: List<Order>,
        filters: List<DriverFilter>,
        isDriverOnline: Boolean
    ): NavFilterCounters {
        val activeOrders = allOrders.filter { !it.isScheduled() }
        val autoCount = filters.count { it.isActive && it.isAuto }
        val cycleCount = filters.count { it.isActive && it.isCycle }
        val activeEtherFilters = filters.filter { it.isActive && it.isEther }

        val etherTotalCount = if (isDriverOnline) activeOrders.size else 0
        val etherFilteredCount = if (!isDriverOnline || activeEtherFilters.isEmpty()) {
            0
        } else {
            activeOrders.count { order ->
                activeEtherFilters.any { filter -> matchesEtherFilter(order, filter) }
            }
        }

        return NavFilterCounters(
            etherFilteredCount = etherFilteredCount,
            etherTotalCount = etherTotalCount,
            autoCount = autoCount,
            cycleCount = cycleCount
        )
    }
}
