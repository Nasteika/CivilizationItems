package ru.vkabz.civilizationItems.features

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.experimental.or

object OreHighlighterItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#00FFFF:#0000FF>Подсвечиватель руды</gradient></b>"
    private const val RADIUS = 60
    private const val DURATION_TICKS = 20 * 120 // 2 минуты
    private const val COOLDOWN_TIME_MILLIS = 300000L // 5 минут
    private const val MULTIPLIER = 2 // Множитель добычи руды

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    // Множитель добычи для игроков
    private val oreMultiplierPlayers = ConcurrentHashMap<UUID, Int>()

    // Список подсвеченных сущностей для каждого игрока
    private val highlightedEntities = ConcurrentHashMap<UUID, List<Int>>()

    /**
     * Инициализация объекта с указанием плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "ore_highlighter_item")

        // Регистрация слушателя событий
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создание предмета "Подсвечиватель руды".
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.PRISMARINE_CRYSTALS, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<gray>Подсвечивает всю руду в радиусе <yellow>$RADIUS</yellow> блоков.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>Эффект длится <yellow>${DURATION_TICKS / 20 / 60} минут</yellow>.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<gray>Добыча руды умножается на <yellow>$MULTIPLIER</yellow> во время действия.")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
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
            addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true)
            addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Активация предмета "Подсвечиватель руды".
     */
    fun activate(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Проверка unique_id у предмета
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(
                miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера.")
            )
            return
        }

        // Проверка кулдауна
        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        // Активируем эффект подсветки руды и множитель добычи
        highlightOres(player)
        grantOreMultiplier(player)

        player.sendMessage(
            miniMessage.deserialize("<green>Вы активировали ${ITEM_NAME}. Эффект продлится <yellow>${DURATION_TICKS / 20 / 60} минут</yellow>.")
        )

        // Удаляем предмет из инвентаря после использования
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }

    /**
     * Подсвечивает руду для игрока в радиусе RADIUS.
     */
    private fun highlightOres(player: Player) {
        val world = player.world
        val center = player.location
        val ores = mutableListOf<Location>()

        val minX = center.blockX - RADIUS
        val maxX = center.blockX + RADIUS
        val minY = (center.blockY - RADIUS).coerceAtLeast(world.minHeight.toDouble().toInt())
        val maxY = (center.blockY + RADIUS).coerceAtMost(world.maxHeight.toDouble().toInt())
        val minZ = center.blockZ - RADIUS
        val maxZ = center.blockZ + RADIUS

        plugin.logger.info("Начинаем поиск руды для игрока ${player.name}")

        // Поиск руды в заданном радиусе
        object : BukkitRunnable() {
            override fun run() {
                var count = 0
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            val block = world.getBlockAt(x, y, z)
                            if (isOre(block)) {
                                ores.add(block.location.clone().add(0.5, 0.0, 0.5))
                                count++
                            }
                        }
                    }
                }

                plugin.logger.info("Найдено $count блоков руды для подсветки игроку ${player.name}")

                if (ores.isEmpty()) {
                    plugin.logger.info("Нет руды для подсветки игроку ${player.name}")
                    return
                }

                // Подсвечиваем найденные руды для игрока
                object : BukkitRunnable() {
                    override fun run() {
                        sendArmorStandPackets(player, ores)
                        // Убираем подсветку после окончания действия
                        object : BukkitRunnable() {
                            override fun run() {
                                removeArmorStandPackets(player)
                            }
                        }.runTaskLater(plugin, DURATION_TICKS.toLong())
                    }
                }.runTask(plugin)
            }
        }.runTaskAsynchronously(plugin)
    }

    /**
     * Проверяет, является ли блок рудой.
     */
    private fun isOre(block: Block): Boolean {
        return when (block.type) {
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE -> true
            else -> false
        }
    }

    /**
     * Отправляет пакеты для создания светящихся невидимых стойкек для доспехов на позициях руды.
     */
    private fun sendArmorStandPackets(player: Player, locations: List<Location>) {
        val protocolManager = ProtocolLibrary.getProtocolManager()
        val entityIds = mutableListOf<Int>()

        plugin.logger.info("Отправляем пакеты для создания ${locations.size} стойкек для доспехов игроку ${player.name}")

        for (location in locations) {
            val entityId = (Math.random() * Integer.MAX_VALUE).toInt()
            entityIds.add(entityId)

            val spawnPacket = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY)
            spawnPacket.integers.write(0, entityId) // Entity ID
            spawnPacket.uuiDs.write(0, UUID.randomUUID()) // Random UUID
            spawnPacket.entityTypeModifier.write(0, EntityType.ARMOR_STAND)
            spawnPacket.doubles
                .write(0, location.x)
                .write(1, location.y)
                .write(2, location.z)

            val metadataPacket = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
            metadataPacket.integers.write(0, entityId)
            val watcher = WrappedDataWatcher()

            // Устанавливаем невидимость и свечение
            val metaIndex = WrappedDataWatcher.WrappedDataWatcherObject(0, Registry.get(Byte::class.java))
            val metaValue = (0x20 or 0x40).toByte() // Invisible and Glowing
            watcher.setObject(metaIndex, metaValue)

            // Отмечаем как маленький (опционально)
            val metaIndexSize = WrappedDataWatcher.WrappedDataWatcherObject(15, Registry.get(Boolean::class.java))
            watcher.setObject(metaIndexSize, false) // false для нормального размера

            metadataPacket.watchableCollectionModifier.write(0, watcher.watchableObjects)

            try {
                protocolManager.sendServerPacket(player, spawnPacket)
                protocolManager.sendServerPacket(player, metadataPacket)
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
                plugin.logger.severe("Ошибка при отправке пакетов игроку ${player.name}: ${e.message}")
            }
        }

        highlightedEntities[player.uniqueId] = entityIds
        plugin.logger.info("Стойки для доспехов отправлены игроку ${player.name}")
    }

    /**
     * Убирает подсвеченные стойки для доспехов для игрока.
     */
    private fun removeArmorStandPackets(player: Player) {
        val protocolManager = ProtocolLibrary.getProtocolManager()
        val entityIds = highlightedEntities[player.uniqueId] ?: return

        plugin.logger.info("Удаляем подсвеченные стойки для игрока ${player.name}")

        val destroyPacket = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)
        destroyPacket.integerArrays.write(0, entityIds.toIntArray())

        try {
            protocolManager.sendServerPacket(player, destroyPacket)
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
            plugin.logger.severe("Ошибка при отправке пакета удаления игроку ${player.name}: ${e.message}")
        }

        highlightedEntities.remove(player.uniqueId)
    }

    /**
     * Предоставляет игроку множитель добычи руды.
     */
    private fun grantOreMultiplier(player: Player) {
        oreMultiplierPlayers[player.uniqueId] = MULTIPLIER

        // Убираем множитель после окончания действия
        object : BukkitRunnable() {
            override fun run() {
                oreMultiplierPlayers.remove(player.uniqueId)
            }
        }.runTaskLater(plugin, DURATION_TICKS.toLong())
    }

    /**
     * Обработчик события добычи блока.
     */
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        if (oreMultiplierPlayers.containsKey(player.uniqueId) && isOre(block)) {
            val multiplier = oreMultiplierPlayers[player.uniqueId] ?: 1

            val drops = block.getDrops(player.inventory.itemInMainHand)
            drops.forEach { itemStack ->
                itemStack.amount *= multiplier
                player.world.dropItemNaturally(block.location, itemStack)
            }

            block.type = Material.AIR
            event.isCancelled = true
        }
    }
}
