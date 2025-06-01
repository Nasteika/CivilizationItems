package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import ru.vkabz.civilizationItems.api.CivilizationAPI
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import java.util.*

object EnemyHighlighterItem {

    private const val ITEM_NAME = "<b><gradient:#FF0000:#FF7F00>РАДАР ВРАГА</gradient></b>"
    private var RADIUS = 60.0 // Радиус действия (можно сделать настраиваемым)
    private const val DURATION_TICKS = 20 * 120 // 2 минуты
    private const val COOLDOWN_TIME_MILLIS = 150000L // Кулдаун (настраиваемый)

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    private val protocolManager: ProtocolManager = ProtocolLibrary.getProtocolManager()

    // Хранение информации о подсвеченных игроках для каждого игрока
    private val glowingPlayersMap: MutableMap<UUID, MutableSet<UUID>> = mutableMapOf()

    // Хранение запущенных задач для каждого игрока
    private val scheduledTasks: MutableMap<UUID, BukkitRunnable> = mutableMapOf()

    // Инициализация объекта с указанием плагина
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "enemy_highlighter_item")
    }

    // Создание предмета
    fun createItem(amount: Int,noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.COMPASS, amount)
        val meta = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Подсвечивает игроков вражеских цивилизаций.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Длительность действия: <yellow>2 минуты</yellow>.")
                        .decoration(TextDecoration.ITALIC, false)
                )
            )
            // Устанавливаем уникальный ключ в PersistentDataContainer
            persistentDataContainer.set(ITEM_KEY, PersistentDataType.BYTE, 1)
            if (!noUniqueId) {
                // Присваиваем уникальный идентификатор
                persistentDataContainer.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
            } else {
                // Помечаем предмет как предназначенный для оператора
                persistentDataContainer.set(
                    NamespacedKey(plugin, "operator_only"),
                    PersistentDataType.BYTE,
                    1
                )
            }

        }
        item.itemMeta = meta
        return item
    }

    // Активация предмета
    fun activate(player: Player) {

        return
        // Проверяем кулдаун
        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        // Устанавливаем кулдаун
        CooldownManager.setCooldown(player, ITEM_KEY)

        val playerCivilization = CivilizationAPI.getCivilization(player.uniqueId)

        if (playerCivilization == null) {
            player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <red>Вы не принадлежите ни к одной цивилизации."))
            return
        }

        // Найти всех врагов в радиусе
        val nearbyPlayers = player.world.getNearbyPlayers(player.location, RADIUS).filter { it != player }

        val enemyPlayers = nearbyPlayers.filter { target ->
            val targetCivilization = CivilizationAPI.getCivilization(target.uniqueId)
            if (targetCivilization == "Нет цивилизации") {
                return@filter false
            }
            targetCivilization != null && targetCivilization != playerCivilization
        }

        if (enemyPlayers.isEmpty()) {
            player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <yellow>Вокруг нет игроков вражеских цивилизаций."))
            return
        }

        // Добавляем игроков в карту подсвеченных
        glowingPlayersMap[player.uniqueId] = enemyPlayers.map { it.uniqueId }.toMutableSet()

        // Включаем подсветку
        enemyPlayers.forEach { target ->
            sendGlowingPacket(target, player, true)
        }

        player.sendMessage(miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Игроки вражеских цивилизаций подсвечены на <yellow>2</yellow> минуты."))

        // Запускаем задачу для динамического обновления подсветки
        val task = object : BukkitRunnable() {
            override fun run() {
                // Проверка текущих врагов в радиусе
                val currentNearbyPlayers = player.world.getNearbyPlayers(player.location, RADIUS).filter { it != player }

                val currentEnemyPlayers = currentNearbyPlayers.filter { target ->
                    val targetCivilization = CivilizationAPI.getCivilization(target.uniqueId)
                    if (targetCivilization == "Нет цивилизации") {
                        return@filter false
                    }
                    targetCivilization != null && targetCivilization != playerCivilization
                }

                val currentEnemyUUIDs = currentEnemyPlayers.map { it.uniqueId }.toSet()
                val glowingUUIDs = glowingPlayersMap[player.uniqueId] ?: mutableSetOf()

                // Новые враги, которых еще не подсветили
                val newEnemies = currentEnemyUUIDs - glowingUUIDs
                currentEnemyUUIDs.forEach { uuid ->
                    val target = Bukkit.getPlayer(uuid) ?: return@forEach
                    sendGlowingPacket(target, player, true)
                    glowingPlayersMap[player.uniqueId]?.add(uuid)
                }

                // Враги, которых больше нет в радиусе
//                val enemiesToRemove = glowingUUIDs - currentEnemyUUIDs
//                enemiesToRemove.forEach { uuid ->
//                    val target = Bukkit.getPlayer(uuid) ?: return@forEach
//                    sendGlowingPacket(target, player, false)
//                    glowingPlayersMap[player.uniqueId]?.remove(uuid)
//                }
            }
        }

        // Запустить задачу каждые 20 тик (1 секунда)
        task.runTaskTimer(plugin, 0L, 20L)

        // Сохранить задачу для последующего отмены
        scheduledTasks[player.uniqueId] = task

        // Планируем снятие подсветки через 2 минуты
        object : BukkitRunnable() {
            override fun run() {
                removeGlow(player)
            }
        }.runTaskLater(plugin, DURATION_TICKS.toLong())

        // Удаляем один предмет из руки игрока
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }

    // Отправка пакетов для включения/отключения подсветки
    private fun sendGlowingPacket(target: Player, receiver: Player, glowing: Boolean) {
        val entityId = target.entityId

        val packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, entityId)

        val dataWatcher = WrappedDataWatcher()
        val serializer = WrappedDataWatcher.Registry.get(Byte::class.javaObjectType)

        val index = 0 // Индекс для флага Entity Metadata (может отличаться в зависимости от версии)

        val metadata = if (glowing) 0x40.toByte() else 0x00.toByte()

        dataWatcher.setObject(
            WrappedDataWatcher.WrappedDataWatcherObject(index, serializer),
            metadata
        )

        packet.watchableCollectionModifier.write(0, dataWatcher.watchableObjects)

        protocolManager.sendServerPacket(receiver, packet)
    }

    // Снятие подсветки
    fun removeGlow(player: Player) {
        val glowingPlayers = glowingPlayersMap[player.uniqueId] ?: return
        val onlinePlayers = Bukkit.getOnlinePlayers().associateBy { it.uniqueId }

        glowingPlayers.forEach { uuid ->
            val target = onlinePlayers[uuid]
            if (target != null) {
                sendGlowingPacket(target, player, false)
            }
        }

        glowingPlayersMap.remove(player.uniqueId)

        // Отправляем сообщение игроку
        player.sendMessage(
            miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <yellow>Подсветка игроков вражеских цивилизаций закончилась.")
        )

        // Отменяем задачу по обновлению подсветки
        val task = scheduledTasks[player.uniqueId]
        if (task != null) {
            task.cancel()
            scheduledTasks.remove(player.uniqueId)
        }
    }
}
