package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object EternalDawnBootsItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()

    // Список игроков, которым мы добавили эффект прыжка
    private val playersWithOurJumpEffect = ConcurrentHashMap.newKeySet<UUID>()

    // Ключ, чтобы отличать эти ботинки
    lateinit var ITEM_KEY: NamespacedKey
    // Ключ, чтобы хранить "режим прыжка" (0..3)
    private lateinit var MODE_KEY: NamespacedKey

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "eternal_dawn_boots_item")
        MODE_KEY = NamespacedKey(plugin, "eternal_dawn_boots_mode")

        plugin.server.pluginManager.registerEvents(this, plugin)

        // Запускаем периодическую проверку эффектов (каждые 5 секунд)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                updatePlayerEffects(player)
            }
        }, 100L, 100L) // 100 тиков = 5 секунд
    }

    /**
     * Создаёт ботинки «Ботинки Вечной Зари».
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_BOOTS, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FBAB08:#FFEFC8>Ботинки Вечной Зари</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )

            lore(
                listOf(
                    miniMessage.deserialize("").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Особенности:</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Увеличивают высоту прыжка на 50%.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Возможность управлять высоким прыжком</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>  при нажатии ПКМ по ботинкам.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Неразрушимые.</white>").decoration(TextDecoration.ITALIC, false),
                )
            )

            // Неразрушимые
            isUnbreakable = true

            // Ставим "наши" метки
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)

            if (!noUniqueId) {
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            }

            persistentDataContainer.set(MODE_KEY, PersistentDataType.INTEGER, 1)

            // Зачарования
            addEnchant(Enchantment.PROTECTION, 5, true)
            addEnchant(Enchantment.FIRE_PROTECTION, 6, true)
            addEnchant(Enchantment.BLAST_PROTECTION, 6, true)
            addEnchant(Enchantment.PROJECTILE_PROTECTION, 6, true)
            addEnchant(Enchantment.SOUL_SPEED, 3, true)
            addEnchant(Enchantment.FROST_WALKER, 2, true)
            addEnchant(Enchantment.FEATHER_FALLING, 3, true)
            addEnchant(Enchantment.DEPTH_STRIDER, 3, true)
        }
        item.itemMeta = meta

        return item
    }

    // Обработчики событий

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Отложенная проверка для корректной инициализации игрока
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updatePlayerEffects(event.player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // Удаляем наш эффект при выходе, если он был добавлен нами
        if (playersWithOurJumpEffect.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
            playersWithOurJumpEffect.remove(player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        // Отложенная проверка после респавна
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            updatePlayerEffects(event.player)
        }, 10L)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Проверяем, связан ли клик с ботинками
        val isBootsSlot = event.slot == 36 || event.rawSlot == 36 // Слот ботинок
        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        // Проверяем, является ли какой-либо из предметов нашими ботинками
        val isBootsInvolved = isBootsSlot ||
                isEternalDawnBoots(clickedItem) ||
                isEternalDawnBoots(cursorItem) ||
                (event.isShiftClick && isEternalDawnBoots(event.currentItem))

        if (isBootsInvolved) {
            // Отложенная проверка после клика в инвентаре
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updatePlayerEffects(player)
            }, 2L)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        // Удаляем игрока из списка при смерти
        playersWithOurJumpEffect.remove(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Проверяем правый клик основной рукой
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val item = player.inventory.itemInMainHand

        if (!isEternalDawnBoots(item)) return

        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Отменяем стандартное действие
        event.isCancelled = true

        // Переключаем режим
        val currentMode = pdc.get(MODE_KEY, PersistentDataType.INTEGER) ?: 0
        val newMode = (currentMode + 1) % 4
        pdc.set(MODE_KEY, PersistentDataType.INTEGER, newMode)
        item.itemMeta = meta

        // Название режима
        val modeName = when (newMode) {
            0 -> "Обычный"
            1 -> "Средний (Jump I)"
            2 -> "Высокий (Jump II)"
            3 -> "Супер (Jump III)"
            else -> "???"
        }

        player.sendMessage(miniMessage.deserialize("<green>Режим прыжка переключён на: <yellow>$modeName"))

        // Обновляем эффекты, если ботинки надеты
        if (isWearingBoots(player)) {
            updatePlayerEffects(player)
        }
    }

    // Вспомогательные методы

    /**
     * Проверяет, являются ли ботинки нашими
     */
    private fun isEternalDawnBoots(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(ITEM_KEY, PersistentDataType.BYTE)
    }

    /**
     * Проверяет наличие ботинок на игроке и обновляет эффекты
     */
    private fun updatePlayerEffects(player: Player) {
        if (isWearingBoots(player)) {
            applyJumpEffect(player)
        } else {
            removeOurJumpEffect(player)
        }
    }

    /**
     * Проверяем, надеты ли "наши" ботинки.
     */
    private fun isWearingBoots(player: Player): Boolean {
        val boots = player.inventory.boots ?: return false
        return isEternalDawnBoots(boots)
    }

    /**
     * Применяет эффект прыжка в зависимости от режима ботинок
     */
    private fun applyJumpEffect(player: Player) {
        val boots = player.inventory.boots ?: return
        val meta = boots.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        val mode = pdc.get(MODE_KEY, PersistentDataType.INTEGER) ?: 0

        // Если режим 0, удаляем эффект если он был добавлен нами
        if (mode == 0) {
            removeOurJumpEffect(player)
            return
        }

        // Применяем новый эффект с соответствующим усилением
        val amplifier = mode - 1

        // Добавляем эффект только если его нет или он не от нас
        if (!player.hasPotionEffect(PotionEffectType.JUMP_BOOST) ||
            !playersWithOurJumpEffect.contains(player.uniqueId)) {

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.JUMP_BOOST,
                    PotionEffect.INFINITE_DURATION,
                    amplifier,
                    false,
                    true,
                    true
                )
            )

            // Добавляем игрока в список
            playersWithOurJumpEffect.add(player.uniqueId)
        } else if (playersWithOurJumpEffect.contains(player.uniqueId)) {
            // Если эффект уже есть и он от нас, проверяем, нужно ли обновить уровень
            val currentEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST)
            if (currentEffect != null && currentEffect.amplifier != amplifier) {
                player.removePotionEffect(PotionEffectType.JUMP_BOOST)
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.JUMP_BOOST,
                        PotionEffect.INFINITE_DURATION,
                        amplifier,
                        false,
                        true,
                        true
                    )
                )
            }
        }
    }

    /**
     * Убираем Jump Boost, если он был добавлен нами
     */
    private fun removeOurJumpEffect(player: Player) {
        // Удаляем эффект только если он был добавлен нами
        if (playersWithOurJumpEffect.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
            playersWithOurJumpEffect.remove(player.uniqueId)
        }
    }
}