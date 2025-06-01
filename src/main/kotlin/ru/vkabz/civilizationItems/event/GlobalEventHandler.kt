package ru.vkabz.civilizationItems.event

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.event.block.Action
import ru.vkabz.civilizationItems.features.*

class GlobalEventHandler(private val plugin: Plugin) : Listener {
    private fun isInBlockedRegion(player: Player): Boolean {
        val blockedRegionIds = setOf(
            "dange_scout",
            "dange_xz",
            "gange_golem",
            "dange_spider",
            "dange_magma",
            "alfs_pvparena",
            "jotuns_pvparena",
            "alvs_spawn",
            "jotuns_spawn",
            "dange_nlo",
            "alvs_cave",
            "dange_chemistry",
            "dange_necropolis",
            "dange_alvs_ruins"
        )

        val world = player.world
        val location = player.location
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container[BukkitAdapter.adapt(world)] ?: return false
        val regions = regionManager.getApplicableRegions(BlockVector3.at(location.x, location.y, location.z))
        return regions.any { it.id in blockedRegionIds }
    }


    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        val offHandItem = player.inventory.itemInOffHand
        if (offHandItem.type == Material.TOTEM_OF_UNDYING) return

        if (offHandItem.itemMeta?.persistentDataContainer?.has(LastChanceItem.ITEM_KEY, PersistentDataType.BYTE) == true) {
            if (offHandItem.amount > 1) {
                offHandItem.amount -= 1
            } else {
                player.inventory.setItemInOffHand(null)
            }

            event.isCancelled = true
            LastChanceItem.playersWithLastChance.add(player.uniqueId)

            object : BukkitRunnable() {
                override fun run() {
                    LastChanceItem.teleportPlayerToSafeLocation(player)
                    LastChanceItem.playersWithLastChance.remove(player.uniqueId)
                }
            }.runTaskLater(plugin, 1L)
            return
        }

        val itemInInventory = player.inventory.contents.find { item ->
            item?.itemMeta?.persistentDataContainer?.has(LastChanceItem.ITEM_KEY, PersistentDataType.BYTE) == true
        } ?: return

        val toRemove = itemInInventory.clone()
        toRemove.amount = 1
        player.inventory.removeItem(toRemove)

        event.isCancelled = true
        LastChanceItem.playersWithLastChance.add(player.uniqueId)

        object : BukkitRunnable() {
            override fun run() {
                LastChanceItem.teleportPlayerToSafeLocation(player)
                LastChanceItem.playersWithLastChance.remove(player.uniqueId)
            }
        }.runTaskLater(plugin, 1L)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val item = player.inventory.itemInMainHand

        if (DuplicationProtection.checkAndRemoveDuplicates(player)) return
        val pdc = item.itemMeta?.persistentDataContainer ?: return

        fun blocked(): Boolean {
            if (isInBlockedRegion(player)) {
                player.sendMessage("§cВы не можете использовать этот предмет в данной зоне.")
                return true
            }
            return false
        }

        when {
            pdc.has(PvPDome.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                PvPDome.activate(player, item)
            }

            pdc.has(DisorientationItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                DisorientationItem.activate(player)
            }

            pdc.has(TerritoryRegenerator.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                TerritoryRegenerator.activate(player)
            }

            pdc.has(EnemyHighlighterItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                EnemyHighlighterItem.activate(player)
            }

            pdc.has(LastChanceItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                event.isCancelled = true
                // Без активации
            }

            pdc.has(WarPointsItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                WarPointsItem.activate(player, item)
            }

            pdc.has(ReputationItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                ReputationItem.activate(player, item)
            }

            pdc.has(FireTornadoItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                FireTornadoItem.activate(player, item)
            }

            pdc.has(OreHighlighterItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                OreHighlighterItem.activate(player, item)
            }

            pdc.has(SonOfThorItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                SonOfThorItem.activate(player)
            }

            pdc.has(AngelPetItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                AngelPetItem.activate(player, item)
            }

            pdc.has(GodPickaxeItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                GodPickaxeItem.switchMode(player, item)
            }

            pdc.has(EscapeItem.ITEM_KEY, PersistentDataType.BYTE) -> {
                if (blocked()) return
                event.isCancelled = true
                EscapeItem.activate(player, item)
            }
        }
    }

}
