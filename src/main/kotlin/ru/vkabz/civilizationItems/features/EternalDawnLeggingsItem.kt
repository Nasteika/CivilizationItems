package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

object EternalDawnLeggingsItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()
    private val random = Random()

    lateinit var ITEM_KEY: NamespacedKey

    /**
     * Инициализация. Вызывается один раз при старте плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "eternal_dawn_leggings_item")

        // Регистрируем обработчики событий
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создаём поножи:
     *  - Название "<b><gradient:#FBAB08:#FFEFC8>Поножи Вечной Зари</gradient></b>"
     *  - Неразрушимые
     *  - Зачарования: Protection VII, Fire Protection VII, Blast Protection VII, Projectile Protection VII, Thorns I
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_LEGGINGS, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            // Название с градиентом
            displayName(
                miniMessage.deserialize("<b><gradient:#FBAB08:#FFEFC8>Поножи Вечной Зари</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )

            lore(
                listOf(
                    miniMessage.deserialize("").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Особенности:</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Увеличение скорости на 20% при ношении.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Снижает урон от падения на 50%.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Дает игроку эффект 'Скорость II',</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>  если здоровье ниже 50%.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Неразрушимые.</white>").decoration(TextDecoration.ITALIC, false)
                )
            )

            // Неразрушимость
            isUnbreakable = true

            // Ставим метку, чтобы отличать этот предмет
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)

            if (!noUniqueId) {
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            }

            // Добавляем нужные зачарования (с повышенными уровнями)
            addEnchant(Enchantment.PROTECTION, 5, true)
            addEnchant(Enchantment.FIRE_PROTECTION, 6, true)
            addEnchant(Enchantment.BLAST_PROTECTION, 6, true)
            addEnchant(Enchantment.PROJECTILE_PROTECTION, 6, true)
            addEnchant(Enchantment.THORNS, 1, true)                  // Thorns I
        }
        item.itemMeta = meta

        return item
    }

    // Обработчики событий

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // Отложенная проверка для корректной инициализации игрока
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updatePlayerEffects(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        // Отложенная проверка после респавна
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updatePlayerEffects(player)
        }, 10L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // Сбрасываем скорость при смерти
        resetSpeed(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Проверяем, связан ли клик с поножами
        val isLeggingsSlot = event.slot == 37 || event.rawSlot == 37 // Слот поножей
        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        // Проверяем, является ли какой-либо из предметов нашими поножами
        val isLeggingsInvolved = isLeggingsSlot ||
                isEternalDawnLeggings(clickedItem) ||
                isEternalDawnLeggings(cursorItem) ||
                event.isShiftClick // Shift-клик может переместить предмет в слот поножей

        if (isLeggingsInvolved) {
            // Отложенная проверка после клика в инвентаре
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updatePlayerEffects(player)
            }, 2L)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity !is Player) return
        val player = entity

        // Если не надеты наши поножи — не трогаем
        if (!isWearingEternalDawnLeggings(player)) return

        // Снижаем урон от падения
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.damage *= 0.5 // минус 50%
        }

        // Проверим, какое здоровье останется
        val newHealth = player.health - event.finalDamage
        // Если после урона все ещё жив (newHealth > 0), но при этом HP < 50%
        if (newHealth > 0 && newHealth < player.maxHealth * 0.5) {
            // Даем эффект Скорость II на 5 секунд (100 тиков)
            player.addPotionEffect(
                PotionEffect(PotionEffectType.SPEED, 100, 1, true, false, true)
            )
        }
    }

    // Вспомогательные методы

    /**
     * Проверяет, является ли предмет нашими поножами
     */
    private fun isEternalDawnLeggings(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(ITEM_KEY, PersistentDataType.BYTE)
    }

    /**
     * Проверка, надеты ли у игрока наши "Поножи Вечной Зари".
     */
    private fun isWearingEternalDawnLeggings(player: Player): Boolean {
        val leggings = player.inventory.leggings ?: return false
        return isEternalDawnLeggings(leggings)
    }

    /**
     * Обновляет эффекты игрока в зависимости от наличия поножей
     */
    private fun updatePlayerEffects(player: Player) {
        if (isWearingEternalDawnLeggings(player)) {
            setSpeedBonus(player)
        } else {
            resetSpeed(player)
        }
    }

    /**
     * Увеличиваем скорость игрока (walkSpeed) на 20%.
     * Базовая = 0.2, значит ставим 0.24.
     */
    private fun setSpeedBonus(player: Player) {
        // Проверяем, не установлена ли уже скорость
        if (player.walkSpeed != 0.24f) {
            player.walkSpeed = 0.24f
        }
    }

    /**
     * Сбрасываем скорость до стандартной (0.2).
     */
    private fun resetSpeed(player: Player) {
        // Проверяем, не установлена ли уже стандартная скорость
        if (player.walkSpeed != 0.2f) {
            player.walkSpeed = 0.2f
        }
    }
}