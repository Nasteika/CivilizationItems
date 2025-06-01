package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
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

object EternalDawnHelmetItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()
    private val random = Random()

    // Список игроков, которым мы добавили эффект ночного зрения
    private val playersWithOurNightVision = ConcurrentHashMap.newKeySet<UUID>()

    lateinit var ITEM_KEY: NamespacedKey

    /**
     * Инициализация. Вызывается один раз при старте плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "eternal_dawn_helmet_item")

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
     * Создаёт наш особый шлем:
     *  - Название "<dark_gray>Шлем Вечной Зари"
     *  - Неразрушимый
     *  - Зачарования: Protection VII, Fire Protection VII, Blast Protection VII, Projectile Protection VII,
     *    Respiration III, Aqua Affinity, Thorns I
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_HELMET, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FBAB08:#FFEFC8>Шлем Вечной Зари</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )

            lore(
                listOf(
                    miniMessage.deserialize("").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Особенности:</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Постоянный эффект ночного зрения.</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>• Шанс 3% наложить слабость</white>").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>  на врага на 2 секунды при получении урона.</white>").decoration(TextDecoration.ITALIC, false),
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

            // Добавляем нужные зачарования (с уровнем выше стандартного)
            addEnchant(Enchantment.PROTECTION, 5, true)
            addEnchant(Enchantment.FIRE_PROTECTION, 6, true)
            addEnchant(Enchantment.BLAST_PROTECTION, 6, true)
            addEnchant(Enchantment.PROJECTILE_PROTECTION, 6, true)// Projectile Protection VII
            addEnchant(Enchantment.RESPIRATION, 3, true)                   // Respiration III
            addEnchant(Enchantment.AQUA_AFFINITY, 1, true)             // Aqua Affinity
            addEnchant(Enchantment.THORNS, 1, true)                   // Thorns I
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
        if (playersWithOurNightVision.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION)
            playersWithOurNightVision.remove(player.uniqueId)
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
        playersWithOurNightVision.remove(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Проверяем, связан ли клик со шлемом
        val isHelmetSlot = event.slot == 39 || event.rawSlot == 39 // Слот шлема
        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        // Проверяем, является ли какой-либо из предметов нашим шлемом
        val isHelmetInvolved = isHelmetSlot ||
                isEternalDawnHelmet(clickedItem) ||
                isEternalDawnHelmet(cursorItem) ||
                (event.isShiftClick && isEternalDawnHelmet(event.currentItem))

        if (isHelmetInvolved) {
            // Отложенная проверка после клика в инвентаре
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                updatePlayerEffects(player)
            }, 2L)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        if (victim !is Player) return

        // Проверяем, надет ли наш шлем
        if (!isWearingEternalDawnHelmet(victim)) return

        // 3% шанс
        if (random.nextDouble() <= 0.03) {
            val damager: Entity? = event.damager
            if (damager is LivingEntity) {
                // Слабость на 2 секунды (40 тиков)
                damager.addPotionEffect(
                    PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true)
                )
            }
        }
    }

    // Вспомогательные методы

    /**
     * Проверяет, является ли предмет нашим шлемом
     */
    private fun isEternalDawnHelmet(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(ITEM_KEY, PersistentDataType.BYTE)
    }

    /**
     * Проверка, надет ли у игрока наш "Шлем Вечной Зари".
     */
    private fun isWearingEternalDawnHelmet(player: Player): Boolean {
        val helmet = player.inventory.helmet ?: return false
        return isEternalDawnHelmet(helmet)
    }

    /**
     * Обновляет эффекты игрока в зависимости от наличия шлема
     */
    private fun updatePlayerEffects(player: Player) {
        if (isWearingEternalDawnHelmet(player)) {
            addNightVision(player)
        } else {
            removeOurNightVision(player)
        }
    }

    /**
     * Добавляем игроку Ночное Зрение и добавляем его в список
     */
    private fun addNightVision(player: Player) {
        // Добавляем эффект только если его нет или он не от нас
        if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION) ||
            !playersWithOurNightVision.contains(player.uniqueId)) {

            // Применяем новый эффект
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    true,
                    false,
                    true
                )
            )

            // Добавляем игрока в список
            playersWithOurNightVision.add(player.uniqueId)
        }
    }

    /**
     * Снимаем у игрока эффект Ночного Зрения, если он был добавлен нами
     */
    private fun removeOurNightVision(player: Player) {
        // Удаляем эффект только если он был добавлен нами
        if (playersWithOurNightVision.contains(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION)
            playersWithOurNightVision.remove(player.uniqueId)
        }
    }
}