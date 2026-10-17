package com.obliprabu.plansaver

import kotlinx.coroutines.delay

/**
 * Where plan data comes from. The Search button calls [search] on every tap.
 * Today it reads the bundled research; swap in [RemotePlanRepository] later to
 * hit a server (Cloud Function) that does the live web lookup. Never put a
 * search/AI API key in this app - keep it on the server.
 */
interface PlanRepository {
    suspend fun search(request: SearchRequest): List<Result>
}

class BundledPlanRepository : PlanRepository {
    override suspend fun search(request: SearchRequest): List<Result> {
        delay(600) // placeholder for the network round trip
        return PlanData.plans
            .filter { request.includePostpaid || it.prepaid }
            .filter { !request.freePhoneOnly || it.freePhoneOffer }
            .filter { it.dataGb == null || (request.dataNeedGb != null && it.dataGb >= request.dataNeedGb) }
            .map {
                val m = it.monthly(request.lines)
                Result(it, m, (request.currentMonthly - m) * 12)
            }
            .sortedBy { it.monthly }
    }
}
