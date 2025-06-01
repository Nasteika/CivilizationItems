import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

object CooldownManager {
    // Карта для хранения времени последнего использования предметов (Pair<UUID игрока, NamespacedKey предмета>)
    private val cooldowns: MutableMap<Pair<UUID, NamespacedKey>, Long> = ConcurrentHashMap()

    /**
     * Проверяет, может ли игрок использовать предмет с заданным ключом и временем cooldown.
     *
     * @param player Игрок
     * @param key NamespacedKey предмета
     * @param cooldownTimeMillis Длительность cooldown в миллисекундах
     * @return true, если cooldown истёк, иначе false
     */
    fun canUse(player: Player, key: NamespacedKey, cooldownTimeMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastUsed = cooldowns[Pair(player.uniqueId, key)] ?: 0

        return now - lastUsed >= cooldownTimeMillis
    }

    /**
     * Устанавливает текущее время как время последнего использования предмета.
     *
     * @param player Игрок
     * @param key NamespacedKey предмета
     */
    fun setCooldown(player: Player, key: NamespacedKey) {
        cooldowns[Pair(player.uniqueId, key)] = System.currentTimeMillis()
    }

    /**
     * Возвращает оставшееся время cooldown.
     *
     * @param player Игрок
     * @param key NamespacedKey предмета
     * @param cooldownTimeMillis Длительность cooldown в миллисекундах
     * @return Оставшееся время в миллисекундах, или 0, если cooldown истёк
     */
    fun getTimeLeft(player: Player, key: NamespacedKey, cooldownTimeMillis: Long): Long {
        val now = System.currentTimeMillis()
        val lastUsed = cooldowns[Pair(player.uniqueId, key)] ?: 0
        val timeLeft = cooldownTimeMillis - (now - lastUsed)
        return if (timeLeft > 0) timeLeft else 0
    }

    /**
     * Проверяет кулдаун и отправляет сообщение игроку в удобном формате (часы, минуты, секунды).
     *
     * @param player Игрок
     * @param key NamespacedKey предмета
     * @param cooldownTimeMillis Длительность cooldown в миллисекундах
     * @return true, если кулдаун активен (действие нельзя выполнить), иначе false
     */
    fun checkCooldownAndNotify(player: Player, key: NamespacedKey, cooldownTimeMillis: Long): Boolean {
        if (player.isOp) {
            return false
        }
        if (!canUse(player, key, cooldownTimeMillis)) {
            val timeLeftMillis = getTimeLeft(player, key, cooldownTimeMillis)
            val timeFormatted = formatTime(timeLeftMillis)
            val miniMessage = MiniMessage.miniMessage()
            player.sendMessage(miniMessage.deserialize("<red>Вы сможете использовать этот предмет через $timeFormatted."))
            return true
        }
        return false
    }

    /**
     * Преобразует время в миллисекундах в строку с часами, минутами и секундами.
     *
     * @param millis Время в миллисекундах
     * @return Форматированная строка вида "1 час 23 минуты 45 секунд"
     */
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000 % 60
        val minutes = millis / 1000 / 60 % 60
        val hours = millis / 1000 / 60 / 60

        val timeParts = mutableListOf<String>()
        if (hours > 0) timeParts.add("$hours час${getPlural(hours)}")
        if (minutes > 0) timeParts.add("$minutes минут${getPlural(minutes)}")
        if (seconds > 0) timeParts.add("$seconds секунд${getPlural(seconds)}")

        return timeParts.joinToString(" ")
    }

    /**
     * Возвращает окончание для числительных (час/часа/часов).
     *
     * @param number Число
     * @return Правильное окончание
     */
    private fun getPlural(number: Long): String {
        return when {
            number % 10 == 1L && number % 100 != 11L -> ""
            number % 10 in 2..4 && (number % 100 < 10 || number % 100 >= 20) -> "ы"
            else -> "ов"
        }
    }
}
