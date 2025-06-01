package ru.vkabz.civilizationItems.api
import java.util.UUID

interface CivilizationAPIInterface {
    fun getReputation(playerId: UUID): Int
    fun addReputation(playerId: UUID, amount: Int): Boolean
    fun deductReputation(playerId: UUID, amount: Int): Boolean
    fun getWarPoints(playerId: UUID): Int
    fun addWarPoints(playerId: UUID, amount: Int): Boolean
    fun deductWarPoints(playerId: UUID, amount: Int): Boolean
    fun getCivilization(playerId: UUID): String?
}
