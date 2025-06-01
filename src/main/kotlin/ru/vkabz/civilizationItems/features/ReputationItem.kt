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

object ReputationItem {

    private const val ITEM_NAME = "<b><gradient:#FFD700:#FFA500>Репутация</gradient></b>"
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
    private const val REPUTATION_KEY = "reputation"

    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "reputation_item")
    }

    /**
     * Создание предмета "Репутация".
     *
     * @param reputation Количество репутации, которые будет начислять предмет при использовании.
     * @param noUniqueId Флаг, указывающий, создаётся ли предмет без уникального идентификатора.
     * @return Созданный ItemStack.
     */
    fun createItem(reputation: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.GOLD_INGOT, 1) // Выберите подходящий материал
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Начисляет <yellow>$reputation</yellow> репутации.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Используйте правым кликом, чтобы активировать.")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            // Устанавливаем ключ для предмета
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            // Устанавливаем количество репутации
            persistentDataContainer.set(
                NamespacedKey(plugin, REPUTATION_KEY),
                PersistentDataType.INTEGER,
                reputation
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
     * Активация предмета "Репутация".
     *
     * @param player Игрок, который использует предмет.
     * @param item Используемый предмет.
     */
    fun activate(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Проверяем наличие unique_id у предмета, если игрок не оператор
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(
                miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера.")
            )
            return
        }

        // Получаем количество репутации из мета
        val reputation = pdc.get(NamespacedKey(plugin, REPUTATION_KEY), PersistentDataType.INTEGER) ?: 0

        if (reputation <= 0) {
            player.sendMessage(
                miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Этот предмет не имеет назначенной репутации.")
            )
            return
        }

        // Используем CivilizationAPI для начисления репутации
        val success = CivilizationAPI.addReputation(player.uniqueId, reputation)
        if (success) {
            player.sendMessage(
                miniMessage.deserialize(
                    "<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вам было добавлено <yellow>$reputation</yellow> репутации."
                )
            )
            // Удаляем предмет из инвентаря после использования
            player.inventory.setItemInMainHand(null)
        } else {
            player.sendMessage(
                miniMessage.deserialize(
                    "<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Не удалось начислить репутацию. Попробуйте позже."
                )
            )
        }
    }
}
