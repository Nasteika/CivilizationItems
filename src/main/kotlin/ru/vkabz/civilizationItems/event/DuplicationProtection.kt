package ru.vkabz.civilizationItems.event

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

object DuplicationProtection : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * Инициализация объекта с указанием плагина
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // Обработчики событий
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        scheduleDuplicationCheck(player)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        scheduleDuplicationCheck(player)
    }

    @EventHandler
    fun onPlayerPickupItem(event: PlayerPickupItemEvent) {
        val player = event.player
        scheduleDuplicationCheck(player)
    }

    /**
     * Отложенная проверка дубликатов и присвоение уникальных ID
     */
    private fun scheduleDuplicationCheck(player: Player) {
        object : BukkitRunnable() {
            override fun run() {
                assignUniqueIdToOperatorItems(player)
                checkAndRemoveDuplicates(player)
            }
        }.runTaskLater(plugin, 1L)
    }

    /**
     * Присваивает уникальный ID предметам с меткой operator_only, если они находятся у обычного игрока
     */
    private fun assignUniqueIdToOperatorItems(player: Player) {
        if (player.isOp) return // Операторам не нужно присваивать уникальные ID

        val inventory = player.inventory
        for (item in inventory.contents) {
            val meta = item?.itemMeta ?: continue
            val pdc = meta.persistentDataContainer

            if (pdc.has(NamespacedKey(plugin, "operator_only"), PersistentDataType.BYTE) &&
                !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {

                // Присваиваем уникальный идентификатор
                pdc.set(
                    NamespacedKey(plugin, "unique_id"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
                )
                // Удаляем метку operator_only
                pdc.remove(NamespacedKey(plugin, "operator_only"))

                // Обновляем метаданные предмета
                item.itemMeta = meta
            }
        }
    }

    /**
     * Проверяет инвентарь игрока на наличие дубликатов и удаляет их
     */
    fun checkAndRemoveDuplicates(player: Player): Boolean {
        val inventory = player.inventory
        val uniqueIdMap = mutableMapOf<String, MutableList<Int>>()
        val slotsToRemove = mutableSetOf<Int>()

        // Сбор информации о предметах
        for ((index, item) in inventory.contents.withIndex()) {
            val meta = item?.itemMeta ?: continue
            val pdc = meta.persistentDataContainer

            // Пропускаем предметы без unique_id
            val uniqueId = pdc.get(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING) ?: continue

            // Проверяем количество предметов
            if (item.amount > 1) {
                slotsToRemove.add(index)
            }

            uniqueIdMap.computeIfAbsent(uniqueId) { mutableListOf() }.add(index)
        }

        // Проверка на дублирование unique_id
        uniqueIdMap.forEach { (_, slots) ->
            if (slots.size > 1) {
                // Все предметы с этим unique_id считаются дубликатами
                slots.forEach { slot ->
                    slotsToRemove.add(slot)
                }
            }
        }

        if (slotsToRemove.isNotEmpty()) {
            slotsToRemove.forEach { slot ->
                inventory.setItem(slot, null)
            }

            // Формирование сообщения для игрока
            val message = "<red>Обнаружены дублирующие предметы. Они были удалены из вашего инвентаря."
            player.sendMessage(miniMessage.deserialize(message))
            plugin.logger.warning("Игрок ${player.name} имел дублирующие предметы. Предметы удалены.")

            return true
        }

        return false
    }
}
