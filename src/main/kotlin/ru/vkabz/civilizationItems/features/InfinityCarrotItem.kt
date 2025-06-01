package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

object InfinityCarrotItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#FFA500:#FFD700>Бесконечная морковь</gradient></b>"
    private val miniMessage = MiniMessage.miniMessage()

    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "infinity_carrot_item")

        // Регистрация слушателя событий
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создание бесконечной моркови.
     *
     * @param noUniqueId Флаг, указывающий, создаётся ли предмет без уникального идентификатора (как у вас в примере).
     * @return Созданный ItemStack.
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.GOLDEN_CARROT, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            // Устанавливаем отображаемое имя
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            // Лор
            lore(
                listOf(
                    miniMessage.deserialize("<white>Эта морковь никогда не заканчивается!")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            // Уникальный ключ для идентификации предмета
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)

            // Если надо, задаём уникальный идентификатор, иначе помечаем для оператора
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

            // Можно добавить "энчант" для красоты (чтобы переливался)
            addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true)
            addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    /**
     * Слушатель события поедания предметов.
     * Если игрок съедает нашу «бесконечную морковь», она:
     * 1) Не расходуется.
     * 2) Полностью восстанавливает голод и насыщение.
     * 3) (Опционально) даёт эффект регенерации/устранения голода и т.д.
     */
    @EventHandler
    fun onPlayerConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Проверяем, действительно ли игрок ест "бесконечную морковь"
        if (pdc.has(ITEM_KEY, PersistentDataType.BYTE)) {
            val player: Player = event.player

            // Отменяем расход предмета
            event.isCancelled = true

            // Восстанавливаем голод и насыщение
            player.foodLevel = 20
            player.saturation = 20f

            // Можно добавить любой эффект, например, небольшую регенерацию
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1))
            // Можно вывести сообщение
            player.sendMessage(miniMessage.deserialize("<green>Вы съели бесконечную морковь!"))

            // Восстанавливаем предмет обратно в слот
            // (Т.к. мы отменили событие, предмет не должен был удалиться,
            //  но если какие-то плагины/моды вмешиваются, можно подстраховаться.)
            val newAmount = item.amount
            item.amount = newAmount // Просто присваиваем то же значение
            player.inventory.setItemInMainHand(item)
        }
    }
}
