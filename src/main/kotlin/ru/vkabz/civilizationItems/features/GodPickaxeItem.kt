package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.math.abs

object GodPickaxeItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()
    lateinit var ITEM_KEY: NamespacedKey
    private lateinit var MODE_KEY: NamespacedKey

    // Предотвращаем рекурсию при ломании дополнительных блоков
    private var breakingAdditionalBlocks = false

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "god_pickaxe_item")
        MODE_KEY = NamespacedKey(plugin, "god_pickaxe_mode")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private enum class Mode(val id: Int) {
        SINGLE(0),
        TUNNEL_3X3(1);

        companion object {
            fun fromId(id: Int): Mode {
                return values().firstOrNull { it.id == id } ?: SINGLE
            }
        }
    }

    private enum class Direction {
        UP, DOWN, NORTH, SOUTH, EAST, WEST
    }

    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_PICKAXE, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FFD700:#FFFFFF>Кирка Бога</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )
            lore(
                listOf(
                    miniMessage.deserialize("<white>Мгновенно ломает любые блоки (кроме бедрока).").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>ПКМ – переключение режимов.").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>- Обычный: ломает 1 блок").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>- 3x3x3: Ломаем 3 слоя по 3x3 вперёд.").decoration(TextDecoration.ITALIC, false)
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

            persistentDataContainer.set(MODE_KEY, PersistentDataType.INTEGER, Mode.SINGLE.id)

//            addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true)
//            addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta
        return item
    }

    fun switchMode(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return

        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера."))
            return
        }

        val currentMode = Mode.fromId(pdc.get(MODE_KEY, PersistentDataType.INTEGER) ?: Mode.SINGLE.id)
        val newMode = when (currentMode) {
            Mode.SINGLE -> Mode.TUNNEL_3X3
            Mode.TUNNEL_3X3 -> Mode.SINGLE
        }
        pdc.set(MODE_KEY, PersistentDataType.INTEGER, newMode.id)
        item.itemMeta = meta

        val modeName = when (newMode) {
            Mode.SINGLE -> "Обычный"
            Mode.TUNNEL_3X3 -> "3x3x3 тоннель"
        }

        plugin.logger.info("Player ${player.name} switched mode from $currentMode to $newMode")
        player.sendActionBar(miniMessage.deserialize("<green>Режим кирки изменён на: <yellow>$modeName"))
    }

    @EventHandler
    fun onBlockDamage(event: BlockDamageEvent) {
        val player = event.player
        val block = event.block
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера."))
            return
        }

        // Не ломаем бедрок
        if (block.type == Material.BEDROCK) {
            plugin.logger.info("BlockDamage: Player ${player.name} tried to break bedrock at ${block.location}, cancelled.")
            event.isCancelled = true
            return
        }

        plugin.logger.info("BlockDamage: Player ${player.name} at ${block.location}, instaBreak enabled.")
        event.setInstaBreak(true)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (breakingAdditionalBlocks) return

        val player = event.player
        val block = event.block
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            return
        }

        if (event.isCancelled) {
            plugin.logger.info("BlockBreak: Event cancelled at ${block.location}")
            return
        }

        val mode = Mode.fromId(pdc.get(MODE_KEY, PersistentDataType.INTEGER) ?: Mode.SINGLE.id)
        plugin.logger.info("BlockBreak: Player ${player.name} broke ${block.location}, mode=$mode")

        if (mode == Mode.SINGLE) {
            plugin.logger.info("Mode SINGLE: no additional blocks.")
            return
        }

        // Определяем направление на основе yaw/pitch
        val yaw = player.location.yaw
        val pitch = player.location.pitch
        val mainDir = getDirectionFromYawPitch(yaw, pitch)

        plugin.logger.info("Player ${player.name}: yaw=$yaw, pitch=$pitch, mainDir=$mainDir")

        // Определяем векторы forward, right, up для данного направления
        val (forward, right, up) = getVectorsForDirection(mainDir)
        plugin.logger.info("Direction vectors: forward=$forward, right=$right, up=$up")

        val baseX = block.x
        val baseY = block.y
        val baseZ = block.z

        // Ломаем 3 слоя по 3x3
        // layer=0..2
        // i,j=-1..1
        // layer=0: пропускаем центральный (уже сломан), ломаем 8 вокруг
        // layer=1,2: ломаем все 9
        breakingAdditionalBlocks = true
        for (layer in 0..2) {
            for (i in -1..1) {
                for (j in -1..1) {
                    if (layer == 0 && i == 0 && j == 0) continue

                    val tx = baseX + forward.first * layer + right.first * i + up.first * j
                    val ty = baseY + forward.second * layer + right.second * i + up.second * j
                    val tz = baseZ + forward.third * layer + right.third * i + up.third * j

                    val targetBlock = block.world.getBlockAt(tx, ty, tz)
                    plugin.logger.info("Try break (layer=$layer,i=$i,j=$j) at $tx,$ty,$tz block=${targetBlock.type}")
                    tryBreakAdditionalBlock(player, targetBlock)
                }
            }
        }
        breakingAdditionalBlocks = false
        plugin.logger.info("Finished breaking additional blocks.")
    }

    /**
     * Определяет направление взгляда по yaw/pitch.
     * pitch > 45 => DOWN
     * pitch < -45 => UP
     * иначе по yaw: 0=South, 90=West, 180=North, 270=East с допуском ±45°
     */
    private fun getDirectionFromYawPitch(yaw: Float, pitch: Float): Direction {
        if (pitch > 45) return Direction.DOWN
        if (pitch < -45) return Direction.UP

        // Горизонтальное направление
        var angle = (yaw % 360 + 360) % 360
        // 0° = South
        // Диапазоны:
        // -45..45: South
        // 45..135: West
        // 135..225: North
        // 225..315: East
        // 315..360 и 0..45: снова South
        return when {
            angle < 45 || angle >= 315 -> Direction.SOUTH
            angle < 135 -> Direction.WEST
            angle < 225 -> Direction.NORTH
            angle < 315 -> Direction.EAST
            else -> Direction.SOUTH
        }
    }

    /**
     * Возвращает тройку векторов (forward, right, up) для заданного направления.
     * Каждый вектор - кортеж (dx,dy,dz).
     *
     * Для упрощения считаем, что tunnel всегда образуется так:
     * - forward направление вдоль "слоёв"
     * - right направление по оси i (горизонтальный сдвиг)
     * - up направление по оси j (вертикальный сдвиг)
     */
    private fun getVectorsForDirection(dir: Direction): Triple<Triple<Int,Int,Int>, Triple<Int,Int,Int>, Triple<Int,Int,Int>> {
        return when (dir) {
            Direction.UP -> {
                // Смотрим вверх:
                // forward=(0,1,0) - слои идут вверх по Y
                // right=(1,0,0) - i вдоль X
                // up=(0,0,1) - j вдоль Z
                Triple(Triple(0,1,0), Triple(1,0,0), Triple(0,0,1))
            }
            Direction.DOWN -> {
                // Вниз:
                // forward=(0,-1,0)
                // right=(1,0,0)
                // up=(0,0,-1)
                Triple(Triple(0,-1,0), Triple(1,0,0), Triple(0,0,-1))
            }
            Direction.NORTH -> {
                // Север: Z уменьшается
                // forward=(0,0,-1)
                // right=(-1,0,0) (вправо будет запад->восток)
                // up=(0,1,0) (j по вертикали)
                Triple(Triple(0,0,-1), Triple(-1,0,0), Triple(0,1,0))
            }
            Direction.SOUTH -> {
                // Юг: Z увеличивается
                // forward=(0,0,1)
                // right=(1,0,0)
                // up=(0,1,0)
                Triple(Triple(0,0,1), Triple(1,0,0), Triple(0,1,0))
            }
            Direction.EAST -> {
                // Восток: X увеличивается
                // forward=(1,0,0)
                // right=(0,0,-1) (поворот "вправо" вдоль Z в другую сторону)
                // up=(0,1,0)
                Triple(Triple(1,0,0), Triple(0,0,-1), Triple(0,1,0))
            }
            Direction.WEST -> {
                // Запад: X уменьшается
                // forward=(-1,0,0)
                // right=(0,0,1)
                // up=(0,1,0)
                Triple(Triple(-1,0,0), Triple(0,0,1), Triple(0,1,0))
            }
        }
    }

    private fun tryBreakAdditionalBlock(player: Player, block: Block) {
        if (block.type == Material.BEDROCK) {
            plugin.logger.info("Skipping bedrock at ${block.location}")
            return
        }
        if (block.type == Material.AIR) {
            plugin.logger.info("Skipping air at ${block.location}")
            return
        }

        plugin.logger.info("Calling BlockBreakEvent for ${block.location}")
        val breakEvent = BlockBreakEvent(block, player)
        plugin.server.pluginManager.callEvent(breakEvent)
        if (breakEvent.isCancelled) {
            plugin.logger.info("BlockBreakEvent cancelled for ${block.location}")
            return
        }

        val drops = block.getDrops(player.inventory.itemInMainHand, player)
        plugin.logger.info("Breaking ${block.location}, drops=${drops.size}")
        block.type = Material.AIR
        for (item in drops) {
            block.world.dropItemNaturally(block.location, item)
        }
        block.world.playSound(block.location, Sound.BLOCK_STONE_BREAK, 1f, 1f)
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        block.world.spawnParticle(Particle.BLOCK, block.location.add(0.5,0.5,0.5),
            20, 0.3,0.3,0.3, block.blockData)
    }
}
