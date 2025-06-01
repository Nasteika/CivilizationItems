package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.*

object EscapeItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#4D9DE0:#4DCA9D>ЛИВАЛКА</gradient></b>"
    private const val COOLDOWN_TIME_MILLIS = 60000L // 1 минута
    private val Y_RANGE = 250..350

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "escape_item")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создание предмета "Ливалка".
     *
     * @param amount Количество предметов в стеке.
     * @param noUniqueId Флаг, указывающий, создаётся ли предмет без уникального идентификатора
     *                  (например, для операторов).
     * @return Созданный ItemStack.
     */
    fun createItem(amount: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.FEATHER, amount)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false)
            )
            lore(
                listOf(
                    miniMessage.deserialize("<white>Спасает вас в критический момент").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Активируется нажатием ПКМ.").decoration(TextDecoration.ITALIC, false)
                )
            )
            // Устанавливаем наш ключ, что это "escape_item"
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)

            if (!noUniqueId) {
                // Присваиваем уникальный идентификатор
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            } else {
                // Ставим флаг, что предназначено только для оператора (или других спец. случаев)
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

    /**
     * Активация предмета "Ливалка".
     *
     * @param player Игрок, который использует предмет.
     * @param item Используемый предмет.
     */
    fun activate(player: Player, item: ItemStack) {
        if (player.world.name != "world") {
            miniMessage.deserialize(
                "<b><gradient:#32CD32:#7CFC00>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Этот предмет можно использовать только в обычном мире."
            )
            return
        }
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Если игрок не оператор - проверяем наличие уникального ключа "unique_id"
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(
                miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера.")
            )
            return
        }

        // Проверяем кулдаун (предполагается, что CooldownManager - ваш собственный класс)
        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        // Генерируем случайную высоту от 250 до 350
        val randomY = Y_RANGE.random().toDouble()

        // Берём текущую локацию игрока и выставляем новую Y-координату
        val targetLocation = player.location.clone().apply {
            y = randomY
        }

        // Телепортируем игрока
        player.teleport(targetLocation)
        player.sendMessage(
            miniMessage.deserialize(
                "<b><gradient:#32CD32:#7CFC00>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» " +
                        "<white>Вы были спасены."
            )
        )

        // Удаляем 1 предмет из стака
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }
}
