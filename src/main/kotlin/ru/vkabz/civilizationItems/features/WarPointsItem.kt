package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.UUID

object WarPointsItem {

    private const val ITEM_NAME = "<b><gradient:#FFD700:#FFA500>Очки Войны</gradient></b>"
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
    private const val WAR_POINTS_KEY = "war_points"

    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "war_points_item")
    }

    /**
     * Создание предмета "Око Войны".
     *
     * @param warPoints Количество очков войны, которые будет начислять предмет при использовании.
     * @return Созданный ItemStack.
     */
    // WarPointsItem.kt

    fun createItem(warPoints: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Начисляет <gold>$warPoints</gold> очков войны.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Используйте правым кликом, чтобы активировать.")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            // Устанавливаем ключ для предмета
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            // Устанавливаем количество очков войны
            persistentDataContainer.set(
                NamespacedKey(plugin, WAR_POINTS_KEY),
                PersistentDataType.INTEGER,
                warPoints
            )
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
            addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
            // Скрываем отображение зачарований
            addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta

        return item
    }


    /**
     * Активация предмета "Око Войны".
     *
     * @param player Игрок, который использует предмет.
     * @param item Используемый предмет.
     */
    fun activate(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Получаем количество очков войны из мета
        val warPoints = pdc.get(NamespacedKey(plugin, WAR_POINTS_KEY), PersistentDataType.INTEGER) ?: 0

        if (warPoints <= 0) {
            player.sendMessage(
                miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Этот предмет не имеет назначенных очков войны.")
            )
            return
        }

        // Используем CivilizationAPI для начисления очков войны
        val success = CivilizationAPI.addWarPoints(player.uniqueId, warPoints)
        if (success) {
            player.sendMessage(
                miniMessage.deserialize(
                    "<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вам было добавлено <yellow>$warPoints</yellow> очков войны."
                )
            )
            // Удаляем предмет из инвентаря после использования
            player.inventory.setItemInMainHand(null)
        } else {
            player.sendMessage(
                miniMessage.deserialize(
                    "<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Не удалось начислить очки войны. Попробуйте позже."
                )
            )
        }
    }
}
