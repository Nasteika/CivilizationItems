package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.*

object DisorientationItem {

    private const val ITEM_NAME = "<gradient:#62CB2D:#08AC13><b>ДЕЗОРИЕНТАЦИЯ</b></gradient>"
    private const val RADIUS = 10 // Радиус действия эффекта
    private val NEGATIVE_EFFECTS = listOf(
        PotionEffectType.BLINDNESS,
        PotionEffectType.NAUSEA, // Нausea
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS
    )
    private const val EFFECT_DURATION = 20 * 10 // 10 секунд
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
    private const val COOLDOWN_TIME_MILLIS = 60000L


    // Инициализация объекта с указанием плагина
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "disorientation_item")
    }

    // Создание предмета
    fun createItem(amount: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.FERMENTED_SPIDER_EYE, amount)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(
                TextDecoration.ITALIC, false),)
            lore(listOf(
                miniMessage.deserialize("<white>Накладывает негативные эффекты на игроков вокруг.").decoration(
                    TextDecoration.ITALIC, false),
                miniMessage.deserialize("<white>Радиус действия: <red>$RADIUS блоков</red>.").decoration(TextDecoration.ITALIC, false),
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
            player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вокруг нет игроков для воздействия."))
            return
        }

        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        val getPlayerCivilization = CivilizationAPI.getCivilization(player.uniqueId)

        for (target in nearbyPlayers) {
            val targetPlayerCivilization = CivilizationAPI.getCivilization(target.uniqueId)

            if (getPlayerCivilization == targetPlayerCivilization) {
                continue
            }

            for (effectType in NEGATIVE_EFFECTS) {
                target.addPotionEffect(PotionEffect(effectType, EFFECT_DURATION, 1))
            }
            target.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Игрок ${player.name} подверг вас дезориентации!"))
        }

        player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вы использовали предмет <yellow>Дезориентация."))
        // Удаляем один предмет из руки игрока
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }
}
