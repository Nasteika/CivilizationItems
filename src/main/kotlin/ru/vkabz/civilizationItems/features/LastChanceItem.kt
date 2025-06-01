package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

object LastChanceItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#FF4500:#FF8C00>Последний шанс</gradient></b>"
    private const val TELEPORT_DISTANCE = 70.0 // Фиксированное расстояние для телепорта
    private const val SKY_HEIGHT = 256.0 // Высота для телепортации в небо
    private const val LEVITATION_DURATION = 20 * 180 // Левитация на 3 минуты (180 секунд)
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    // Хранение игроков, для которых активировался "Последний шанс"
     val playersWithLastChance = mutableSetOf<UUID>()

    // Инициализация объекта с указанием плагина
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "last_chance_item")
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    // Создание предмета
    fun createItem(amount: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.ENDER_EYE, amount)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Активируется при смерти.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Телепортирует на безопасное место в 70 блоках.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Если место не найдено — вы отправитесь в небо.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Восстанавливает половину здоровья.")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            if (!noUniqueId) {
                // Присваиваем уникальный идентификатор
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            } else {
                // Помечаем предмет как предназначенный для оператора
                persistentDataContainer.set(
                    NamespacedKey(plugin, "operator_only"),
                    PersistentDataType.BYTE,
                    1
                )
            }
        }
        item.itemMeta = meta
        return item
    }

    // Телепортирует игрока в безопасное место или в небо с левитацией
    fun teleportPlayerToSafeLocation(player: Player) {
        if (player.world.name != "world") {
            miniMessage.deserialize(
                "<b><gradient:#32CD32:#7CFC00>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Этот предмет можно использовать только в обычном мире."
            )
            return
        }

        val startLocation = player.location

        // Генерируем случайное направление на расстоянии TELEPORT_DISTANCE
        val targetLocation = generateRandomLocation(startLocation, TELEPORT_DISTANCE)

        // Ищем безопасное место на поверхности
        val safeLocation = findSurfaceSafeLocation(targetLocation)

        if (safeLocation != null) {
            player.teleport(safeLocation)
            restoreHealth(player)
            player.sendMessage(
                miniMessage.deserialize(
                    "<b><gradient:#32CD32:#7CFC00>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Последний шанс спас вас! Вы были телепортированы."
                )
            )
        } else {
            teleportToSky(player)
        }
    }

    // Генерация случайной точки на фиксированном расстоянии
    private fun generateRandomLocation(startLocation: Location, distance: Double): Location {
        val random = Random()
        val angle = random.nextDouble() * 2 * Math.PI // Угол в радианах (0 до 2π)

        val offsetX = Math.cos(angle) * distance
        val offsetZ = Math.sin(angle) * distance

        return startLocation.clone().add(offsetX, 0.0, offsetZ)
    }

    // Находит безопасное место на поверхности
    private fun findSurfaceSafeLocation(location: Location): Location? {
        val world = location.world ?: return null
        val x = location.blockX
        val z = location.blockZ

        // Получаем самый высокий блок в данной точке
        val highestBlock = world.getHighestBlockAt(x, z)
        val y = highestBlock.location.blockY + 1 // Позиция над самым высоким блоком

        val targetLocation = Location(world, x.toDouble(), y.toDouble(), z.toDouble())

        // Проверяем, что место безопасно (две пустые блоки)
        return if (isSafeLocation(targetLocation)) {
            targetLocation
        } else {
            null
        }
    }

    // Проверяет, является ли локация безопасной (две пустые блоки)
    private fun isSafeLocation(location: Location): Boolean {
        val block = location.block
        val aboveBlock = block.getRelative(0, 1, 0)
        val belowBlock = block.getRelative(0, -1, 0)

        return block.isEmpty && aboveBlock.isEmpty && belowBlock.type.isSolid
    }

    // Восстанавливает половину здоровья игроку
    private fun restoreHealth(player: Player) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        val newHealth = (maxHealth / 2).coerceAtMost(player.health)
        player.health = newHealth
    }

    // Телепортирует игрока в небо и добавляет эффект левитации
    private fun teleportToSky(player: Player) {
        val skyLocation = player.location.clone().add(0.0, SKY_HEIGHT - player.location.y, 0.0)
        player.teleport(skyLocation)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, LEVITATION_DURATION, 1))
        player.sendMessage(
            miniMessage.deserialize(
                "<b><gradient:#FFA500:#FFD700>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Последний шанс активирован! Безопасное место не найдено, вы телепортированы в небо."
            )
        )
    }
}
