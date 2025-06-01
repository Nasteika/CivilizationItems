package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object EternalDawnChestplateItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()
    private val random = Random()

    // Список игроков, которым мы добавили эффект регенерации
    private val playersWithOurRegeneration = ConcurrentHashMap.newKeySet<UUID>()

    lateinit var ITEM_KEY: NamespacedKey

    /**
     * Инициализация. Вызывается один раз при старте плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "eternal_dawn_chestplate_item")

        // Регистрируем обработчики событий
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Запускаем периодическую проверку эффектов (каждые 5 секунд)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                updatePlayerEffects(player)
            }
        }, 100L, 100L) // 100 тиков = 5 секунд
    }

    /**
     * Создаёт нагрудник «Нагрудник Вечной Зари».
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_CHESTPLATE, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FBAB08:#FFEFC8>Нагрудник Вечной Зари</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )

            lore(
                listOf(
                    miniMessage.deserialize("").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Особенности:</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Постоянный эффект восстановления I при ношении.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• С шансом 10% накладывает 'Замедление'</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>  на врага на 5 секунд при ударе.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• При критическом здоровье (4 сердца</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>  и менее) выдает эффект Сопротивление I</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Неразрушимый.</white>").decoration(TextDecoration.ITALIC, false)
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
            addEnchant(Enchantment.THORNS, 2, true)
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
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // Удаляем наш эффект при выходе, если он был добавлен нами
        if (playersWithOurRegeneration.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.REGENERATION)
            playersWithOurRegeneration.remove(player.uniqueId)
        }
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
        val player = event.player
        // Удаляем игрока из списка при смерти
        playersWithOurRegeneration.remove(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Проверяем, связан ли клик с нагрудником
        val isChestplateSlot = event.slot == 38 || event.rawSlot == 38 // Слот нагрудника
        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        // Проверяем, является ли какой-либо из предметов нашим нагрудником
        val isChestplateInvolved = isChestplateSlot ||
                isEternalDawnChestplate(clickedItem) ||
                isEternalDawnChestplate(cursorItem) ||
                (event.isShiftClick && isEternalDawnChestplate(event.currentItem))

        if (isChestplateInvolved) {
            // Отложенная проверка после клика в инвентаре
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updatePlayerEffects(player)
            }, 2L)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val victim = event.entity

        // Если атакующий не игрок — выходим
        if (damager !is Player) return
        val player = damager

        // Проверяем, носит ли игрок наш нагрудник
        if (!isWearingEternalDawnChestplate(player)) return

        // 10% шанс
        if (random.nextDouble() <= 0.10) {
            // Жертва должна быть LivingEntity (крипер, другой игрок и т.д.)
            if (victim is LivingEntity) {
                // Замедление I на 5 секунд = 100 тиков
                victim.addPotionEffect(
                    PotionEffect(PotionEffectType.SLOWNESS, 100, 0, true, true, true)
                )
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity !is Player) return
        val player = entity

        // Проверяем, надет ли нагрудник
        if (!isWearingEternalDawnChestplate(player)) return

        // Считаем, сколько останется хп после удара
        val newHealth = player.health - event.finalDamage
        if (newHealth > 0 && newHealth <= 8.0) {
            // Выдаём Сопротивление I на 5 секунд = 100 тиков
            player.addPotionEffect(
                PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false, true)
            )
        }
    }

    // Вспомогательные методы

    /**
     * Проверяет, является ли предмет нашим нагрудником
     */
    private fun isEternalDawnChestplate(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(ITEM_KEY, PersistentDataType.BYTE)
    }

    /**
     * Проверка, надет ли у игрока наш "Нагрудник Вечной Зари".
     */
    private fun isWearingEternalDawnChestplate(player: Player): Boolean {
        val chestplate = player.inventory.chestplate ?: return false
        return isEternalDawnChestplate(chestplate)
    }

    /**
     * Обновляет эффекты игрока в зависимости от наличия нагрудника
     */
    private fun updatePlayerEffects(player: Player) {
        val isWearing = isWearingEternalDawnChestplate(player)

        if (isWearing) {
            // Если нагрудник надет, добавляем эффект
            addRegeneration(player)
        } else {
            // Если нагрудник снят, удаляем наш эффект, если он есть
            removeOurRegeneration(player)
        }
    }

    /**
     * Даём игроку Регенерацию I и добавляем его в список
     */
    private fun addRegeneration(player: Player) {
        // Добавляем эффект только если его нет или он не от нас
        if (!player.hasPotionEffect(PotionEffectType.REGENERATION) ||
            !playersWithOurRegeneration.contains(player.uniqueId)) {

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.REGENERATION,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    true,
                    false,
                    true
                )
            )

            // Добавляем игрока в список
            playersWithOurRegeneration.add(player.uniqueId)
        }
    }

    /**
     * Снимаем с игрока наш эффект Регенерации, если он был добавлен нами
     */
    private fun removeOurRegeneration(player: Player) {
        // Удаляем эффект только если он был добавлен нами
        if (playersWithOurRegeneration.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.REGENERATION)
            playersWithOurRegeneration.remove(player.uniqueId)
        }
    }
}