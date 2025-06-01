package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.*

object LockKeyItem {

    private const val ITEM_NAME = "<gradient:#00FFF7:#0089FF><b>КЛЮЧ ВЗЛОМЩИКА</b></gradient>"
    private val miniMessage = MiniMessage.miniMessage()

    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
        private set

    /**
     * Инициализация (вызывать в onEnable).
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "lock_key_item")
    }

    /**
     * Создание предмета «Ключ взломщика».
     * [amount] — количество предметов, [noUniqueId] — пропустить установку уникального UUID.
     */
    fun createItem(amount: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.TRIPWIRE_HOOK, amount)
        val meta = item.itemMeta
        meta?.apply {
            // Название
            displayName(
                miniMessage.deserialize(ITEM_NAME)
                    .decoration(TextDecoration.ITALIC, false)
            )

            // Описание
            lore(
                listOf(
                    miniMessage.deserialize("<white>Мгновенно открывает любой запертый сейф.</white>")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>Одноразовый предмет.</gray>")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )

            // Добавляем "фейковое" зачарование для эффекта свечения
            addEnchant(Enchantment.LURE, 1, true)
            addItemFlags(ItemFlag.HIDE_ENCHANTS)

            // Указываем PDC
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)

            if (!noUniqueId) {
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            } else {
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
}
