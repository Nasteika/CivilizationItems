package ru.vkabz.civilizationItems.api

import org.bukkit.Bukkit
import java.util.UUID
import java.util.logging.Level


object CivilizationAPI : CivilizationAPIInterface {

    private val civilizationWorldPlugin = Bukkit.getPluginManager().getPlugin("CivilizationPlugin")

    // Используем Reflection для получения API экземпляра
    private val apiInstance: Any? = civilizationWorldPlugin?.let { plugin ->
        try {
            val apiMethod = plugin.javaClass.getMethod("getCivilizationAPI")
            apiMethod.invoke(plugin)
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "Не удалось получить CivilizationAPI из CivilizationWorld: ${e.message}")
            null
        }
    }

    override fun getReputation(playerId: UUID): Int {
        return callApiMethod("getReputation", playerId) as? Int ?: 0
    }

    override fun addReputation(playerId: UUID, amount: Int): Boolean {
        return callApiMethod("addReputation", playerId, amount) as? Boolean ?: false
    }

    override fun deductReputation(playerId: UUID, amount: Int): Boolean {
        return callApiMethod("deductReputation", playerId, amount) as? Boolean ?: false
    }

    override fun getWarPoints(playerId: UUID): Int {
        return callApiMethod("getWarPoints", playerId) as? Int ?: 0
    }

    override fun addWarPoints(playerId: UUID, amount: Int): Boolean {
        return callApiMethod("addWarPoints", playerId, amount) as? Boolean ?: false
    }

    override fun deductWarPoints(playerId: UUID, amount: Int): Boolean {
        return callApiMethod("deductWarPoints", playerId, amount) as? Boolean ?: false
    }

    override fun getCivilization(playerId: UUID): String? {
        return callApiMethod("getCivilization", playerId) as? String
    }

    private fun callApiMethod(methodName: String, vararg args: Any): Any? {
        return try {
            // Определяем типы параметров, учитывая примитивные типы Java
            val parameterTypes = args.map { arg ->
                when (arg) {
                    is Int -> Int::class.javaPrimitiveType
                    is Long -> Long::class.javaPrimitiveType
                    is Boolean -> Boolean::class.javaPrimitiveType
                    is Double -> Double::class.javaPrimitiveType
                    is Float -> Float::class.javaPrimitiveType
                    is Short -> Short::class.javaPrimitiveType
                    is Byte -> Byte::class.javaPrimitiveType
                    is Char -> Char::class.javaPrimitiveType
                    else -> arg::class.java
                }
            }.toTypedArray()

            // Логирование для отладки
            Bukkit.getLogger().log(Level.INFO, "Вызов метода: $methodName с параметрами: ${parameterTypes.joinToString(", ") { it?.name ?: "null" }}")

            // Получение метода с правильными типами параметров
            val method = apiInstance?.javaClass?.getMethod(methodName, *parameterTypes)

            if (method == null) {
                Bukkit.getLogger().log(Level.WARNING, "Метод $methodName не найден с заданными параметрами.")
                return null
            }

            // Вызов метода
            method.invoke(apiInstance, *args)
        } catch (e: NoSuchMethodException) {
            Bukkit.getLogger().log(Level.WARNING, "NoSuchMethodException: Метод $methodName с заданными параметрами не найден. ${e.message}")
            null
        } catch (e: Exception) {
            Bukkit.getLogger().log(Level.WARNING, "Не удалось вызвать метод $methodName из CivilizationAPI: ${e.message}")
            null
        }
    }

}
