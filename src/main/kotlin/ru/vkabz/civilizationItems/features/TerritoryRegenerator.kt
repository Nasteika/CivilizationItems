package ru.vkabz.civilizationItems.features

import CooldownManager
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.world.RegenOptions
import com.sk89q.worldedit.world.block.BlockTypes
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

object TerritoryRegenerator {

    private const val ITEM_NAME = "<b><gradient:#FFFF00:#FFA500>РЕГЕНЕРАТОР ТЕРРИТОРИИ</gradient></b>"
    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey
    private const val COOLDOWN_TIME_MILLIS = 600000L

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "territory_regenerator_item")
    }

    // Создание предмета
    fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.NETHER_STAR, amount)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Плавно восстанавливает территорию в чанке.")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Не работает в защищенных регионах.")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                )
            )
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            val uniqueId = UUID.randomUUID().toString()
            persistentDataContainer.set(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING, uniqueId)
        }
        item.itemMeta = meta
        return item
    }

    // Активация предмета
    fun activate(player: Player) {
        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        val chunk = player.location.chunk
        val worldGuard = WorldGuard.getInstance()
        val adaptedWorld = BukkitAdapter.adapt(player.world)
        val regionContainer = worldGuard.platform.regionContainer

        val chunkMin = BukkitAdapter.asBlockVector(chunk.getBlock(0, 0, 0).location)
        val chunkMax = BukkitAdapter.asBlockVector(chunk.getBlock(15, player.world.maxHeight - 1, 15).location)
        val region = ProtectedCuboidRegion("temp-region", chunkMin, chunkMax)

        val regionManager = regionContainer.get(adaptedWorld)
        if (regionManager != null) {
            val applicableRegions = regionManager.getApplicableRegions(region)
            if (!applicableRegions.regions.isEmpty()) {
                player.sendMessage(
                    miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Нельзя восстановить территорию в защищенном регионе.")
                )
                return
            }
        }

        player.sendMessage(
            miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Начинается восстановление территории...")
        )

        regenerateChunk(player, chunk)

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }

    private fun regenerateChunk(player: Player, chunk: org.bukkit.Chunk) {
        val world = player.world
        val weWorld = BukkitAdapter.adapt(world)
        val chunkX = chunk.x
        val chunkZ = chunk.z

        val minX = chunkX shl 4
        val minZ = chunkZ shl 4
        val maxX = minX + 15
        val maxZ = minZ + 15

        val minY = world.minHeight
        val maxY = world.maxHeight - 1

        val yChunks = (minY..maxY).chunked(16)

        val regions = yChunks.map { yRange ->
            CuboidRegion(
                weWorld,
                BlockVector3.at(minX, yRange.first(), minZ),
                BlockVector3.at(maxX, yRange.last(), maxZ)
            )
        }

        object : BukkitRunnable() {
            var index = 0

            override fun run() {
                if (index >= regions.size) {
                    player.sendMessage(
                        miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Территория восстановлена!")
                    )
                    plugin.logger.info("Регенерация успешно завершена для игрока ${player.name}")
                    cancel()
                    return
                }

                val region = regions[index]
                index++

                try {
                    val session = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .build()

                    try {
                        weWorld.regenerate(region, session)
                    } finally {
                        session.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    plugin.logger.severe("Ошибка при восстановлении территории: ${e.message}")
                    player.sendMessage(
                        miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Ошибка при восстановлении территории.")
                    )
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}
