package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

object FrostyShot : Listener {

    private const val ITEM_NAME = "<blue><bold>Морозный выстрел</bold></blue>"
    private const val FREEZE_SECONDS = 5L
    private const val COOLDOWN_TIME_MILLIS = 15_000L

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "frosty_shot_item")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun createItem(): ItemStack {
        val item = ItemStack(Material.SNOWBALL, 1)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<gray>Специальный снежок, который <blue>замораживает</blue> игрока на <yellow>$FREEZE_SECONDS секунд</yellow>."),
                    miniMessage.deserialize("<gray>Кулдаун использования: <yellow>${COOLDOWN_TIME_MILLIS / 1000} секунд</yellow>.")
                )
            )
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onPlayerUse(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return

        event.isCancelled = true

        if (player.hasCooldown(Material.SNOWBALL)) {
            player.sendMessage(miniMessage.deserialize("<red>Снежок ещё перезаряжается!</red>"))
            return
        }

        // Системный кулдаун (показывается на иконке снежка)
        player.setCooldown(Material.SNOWBALL, (COOLDOWN_TIME_MILLIS / 50L).toInt())

        // Бросаем снежок вручную
        val snowball = player.launchProjectile(Snowball::class.java)
        snowball.setMetadata("frosty", FixedMetadataValue(plugin, true))
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity
        if (!snowball.hasMetadata("frosty")) return
        if (snowball.shooter !is Player || event.hitEntity !is Player) return

        val shooter = snowball.shooter as Player
        val target = event.hitEntity as Player

        target.sendMessage(miniMessage.deserialize("<aqua>Вас заморозил <yellow>${shooter.name}</yellow>!"))
        freezePlayer(target)
    }

    private fun freezePlayer(player: Player) {
        player.walkSpeed = 0f
        player.flySpeed = 0f
        player.setMetadata("frozen", FixedMetadataValue(plugin, true))

        // Замедление для блокировки прыжков и резких движений
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, (FREEZE_SECONDS * 20).toInt(), 10, false, false, false))

        // Анимация заморозки как в рыхлом снеге
        player.freezeTicks = (FREEZE_SECONDS * 20).toInt()

        // Заголовок
        player.showTitle(
            Title.title(
                miniMessage.deserialize("<aqua><bold>Вы заморожены!</bold></aqua>"),
                miniMessage.deserialize("<gray>Подождите $FREEZE_SECONDS секунд</gray>"),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(FREEZE_SECONDS), Duration.ZERO)
            )
        )

        object : BukkitRunnable() {
            override fun run() {
                player.removeMetadata("frozen", plugin)
                player.walkSpeed = 0.2f
                player.flySpeed = 0.1f
                player.freezeTicks = 0
            }
        }.runTaskLater(plugin, FREEZE_SECONDS * 20)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.hasMetadata("frozen") &&
            (event.from.x != event.to.x || event.from.z != event.to.z || event.from.y < event.to.y)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerInteractWhileFrozen(event: PlayerInteractEvent) {
        if (event.player.hasMetadata("frozen")) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onDropWhileFrozen(event: PlayerDropItemEvent) {
        if (event.player.hasMetadata("frozen")) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onDamageWhileFrozen(event: EntityDamageByEntityEvent) {
        if (event.damager is Player && event.damager.hasMetadata("frozen")) {
            event.isCancelled = true
        }
    }
}
