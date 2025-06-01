package ru.vkabz.civilizationItems.features

import com.github.sirblobman.combatlogx.api.ICombatLogX
import com.github.sirblobman.combatlogx.api.manager.ICombatManager
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.scheduler.BukkitTask
import java.util.*

object SpeedBootsItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()

    lateinit var ITEM_KEY: NamespacedKey

    // CombatLogX
    private var combatLogX: ICombatLogX? = null
    private val combatManager: ICombatManager?
        get() = combatLogX?.combatManager

    // Константа скорости полёта
    private const val FLY_SPEED = 3
    // Период задачи в тиках (20 тиков = 1 сек)
    private const val TICK_PERIOD = 2L

    private val playerTasks = mutableMapOf<UUID, BukkitTask>()
    private val playerWasAllowedFlight = mutableMapOf<UUID, Boolean>()

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "speed_boots_item")

        // CombatLogX (если установлен)
        combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX") as? ICombatLogX

        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private fun isInCombat(player: Player): Boolean =
        combatManager?.isInCombat(player) ?: false

    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.GOLDEN_BOOTS, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FFD700:#FFA500>Сапоги скороходы</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )
            lore(
                listOf(
                    miniMessage.deserialize("<white>При удержании SHIFT вы летите вперед").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Защита X, неразрушимы").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>- Нет урона от падения").decoration(TextDecoration.ITALIC, false)
                )
            )
            isUnbreakable = true

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

            addEnchant(Enchantment.PROTECTION, 10, true)
        }

        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player

        // Сначала – сапоги ли надеты?
        val boots = player.inventory.boots ?: return
        val meta = boots.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return

        // Если пытается включить, но в combat – запрещаем
        if (event.isSneaking) {
            if (isInCombat(player)) {
                player.sendActionBar(
                    miniMessage.deserialize("<red>Вы в бою – сапоги скороходы отключены!")
                )
                return                      // не выдаём полёт
            }

            // обычное поведение
            playerWasAllowedFlight[player.uniqueId] = player.allowFlight
            player.allowFlight = true
            player.isFlying = true

            // Запускаем задачу, если ещё нет
            if (!playerTasks.containsKey(player.uniqueId)) {
                val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                    if (!player.isOnline) { stopTask(player); return@Runnable }

                    // Если combat начался – выключаем
                    if (isInCombat(player)) {
                        player.sendActionBar(
                            miniMessage.deserialize("<red>Бой начался – полёт выключен!")
                        )
                        stopTask(player)
                        return@Runnable
                    }

                    if (!player.isSneaking) { stopTask(player); return@Runnable }

                    // проверяем, что сапоги не сняты
                    val currentBoots = player.inventory.boots
                    if (currentBoots?.itemMeta?.persistentDataContainer
                            ?.has(ITEM_KEY, PersistentDataType.BYTE) != true
                    ) {
                        stopTask(player)
                        return@Runnable
                    }

                    val direction = player.location.direction.normalize()
                    player.isFlying = true
                    player.velocity = direction.multiply(FLY_SPEED)
                }, 0L, TICK_PERIOD)

                playerTasks[player.uniqueId] = task
            }
        } else {
            stopTask(player)
        }
    }

    private fun stopTask(player: Player) {
        playerTasks.remove(player.uniqueId)?.cancel()

        val wasAllowed = playerWasAllowedFlight.remove(player.uniqueId) ?: false
        player.allowFlight = wasAllowed
        if (!wasAllowed) player.isFlying = false
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val player = event.entity as? Player ?: return

        val boots = player.inventory.boots ?: return
        val meta = boots.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return

        // всё как раньше
        event.isCancelled = true
        player.sendActionBar(miniMessage.deserialize("<green>Вы не получили урон от падения!"))
    }
}