package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.*

object SonOfThorItem {

    private const val ITEM_NAME = "<gradient:#FFD700:#FFA500><b>СЫН ТОРА</b></gradient>"
    private const val RADIUS = 20 // Радиус действия эффекта
    private const val DAMAGE = 12.0 // 4 HP урона
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
    private const val COOLDOWN_TIME_MILLIS = 15000L // 15 секунд

    // Инициализация объекта с указанием плагина
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "son_of_thor_item")
    }

    // Создание предмета
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.TRIDENT, 1)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(
                TextDecoration.ITALIC, false),)
            lore(listOf(
                miniMessage.deserialize("<white>Призывает молнию, поражающую врагов вокруг.").decoration(
                    TextDecoration.ITALIC, false),
                miniMessage.deserialize("<white>Радиус действия: <red>$RADIUS блоков</red>.").decoration(TextDecoration.ITALIC, false),
                miniMessage.deserialize("<white>Наносит <red>${DAMAGE / 2} сердцем</red> урона.").decoration(TextDecoration.ITALIC, false),
                miniMessage.deserialize("<white>Кулдаун: <red>${COOLDOWN_TIME_MILLIS / 1000} секунд</red>.").decoration(TextDecoration.ITALIC, false),
            ))
            // Устанавливаем уникальный ключ в PersistentDataContainer
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
            addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true)
            // Скрываем отображение зачарований
            addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    // Активация предмета
    fun activate(player: Player) {

        val location = player.location
        val world = player.world

        val nearbyPlayers = world.getNearbyPlayers(location, RADIUS.toDouble()).filter { it != player }

        if (nearbyPlayers.isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вокруг нет врагов для атаки."))
            return
        }

        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        val playerCivilization = CivilizationAPI.getCivilization(player.uniqueId)

        for (target in nearbyPlayers) {
            val targetCivilization = CivilizationAPI.getCivilization(target.uniqueId)

            // Проверяем, что игроки из разных цивилизаций
            if (playerCivilization == targetCivilization) {
                continue
            }

            // Наносим урон и вызываем молнию
            target.world.strikeLightningEffect(target.location)
            target.damage(DAMAGE, player)
            target.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>АТАКА</gradient></b> <dark_gray>» <white>Игрок ${player.name} поразил вас молнией!"))
        }

        player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вы использовали предмет <yellow>Сын Тора</yellow>."))
        // Удаляем один предмет из руки игрока
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }
}
