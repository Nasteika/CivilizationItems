package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.*
import kotlin.collections.HashSet
import kotlin.random.Random

object PvPDome : Listener {

    private const val ITEM_NAME = "<b><gradient:#CB2D3E:#EF473A>ХУЙНЯ</gradient></b>"
    private const val RADIUS = 5
    private const val DURATION_TICKS = 20 * 60 // 1 минута
    private const val COOLDOWN_TIME_MILLIS = 60000L // 1 минута
    private const val CHORUS_SUCCESS_CHANCE = 0.4 // 40% шанс успеха хоруса

    // Список возможных цветов стекла для купола
    private val GLASS_COLORS = listOf(
        Material.PINK_STAINED_GLASS,
        Material.RED_STAINED_GLASS,
        Material.ORANGE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS,
        Material.GREEN_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS
    )

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    // Хранение всех активных куполов и их блоков
    private val activeDomsMap = mutableMapOf<Location, MutableSet<Block>>()
    // Общий список всех блоков куполов для быстрой проверки в событиях
    private val allDomeBlocks = HashSet<Block>()
    // Хранение задач удаления куполов
    private val domeDeletionTasks = mutableMapOf<Location, BukkitRunnable>()
    // Хранение цветов для каждого купола
    private val domeColors = mutableMapOf<Location, Pair<Material, Material>>()
    // Хранение игроков, находящихся в куполах
    private val playersInDomes = HashSet<UUID>()

    // Инициализация объекта с указанием плагина
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "pvp_dome_item")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // Создание предмета
    fun createItem(amount: Int, noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.CHORUS_FRUIT, amount)
        val meta = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false)
            )
            lore(
                listOf(
                    miniMessage.deserialize("<white>Создает цветной стеклянный купол для PvP.").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Купол исчезает через <yellow>1 минуту</yellow>.").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Хорус имеет только <yellow>40%</yellow> шанс сработать внутри купола.").decoration(TextDecoration.ITALIC, false)
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

    // Активация купола
    fun activate(player: Player, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        // Проверяем наличие unique_id у предмета, если игрок не оператор
        if (!player.isOp && !pdc.has(NamespacedKey(plugin, "unique_id"), PersistentDataType.STRING)) {
            player.sendMessage(
                miniMessage.deserialize("<red>Этот предмет предназначен только для операторов сервера.")
            )
            return
        }

        // Проверяем наличие кулдауна
        if (CooldownManager.checkCooldownAndNotify(player, ITEM_KEY, COOLDOWN_TIME_MILLIS)) {
            return
        }
        CooldownManager.setCooldown(player, ITEM_KEY)

        // Находим свободное место для купола
        val center = findFreeLocation(player.location) ?: run {
            player.sendMessage(
                miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Не удалось найти место для создания купола.")
            )
            return
        }

        // Выбираем случайные цвета для купола
        val outerColor = GLASS_COLORS.random()
        val innerColor = GLASS_COLORS.random()
        domeColors[center] = Pair(outerColor, innerColor)

        // Создаём купол
        createDome(center)
        teleportPlayersToSphere(center, player)
        player.sendMessage(
            miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вы использовали предмет ${ITEM_NAME}. Купол исчезает через <yellow>1 минуту</yellow>.")
        )
        scheduleRemoval(center)

        // Удаляем предмет из инвентаря после использования
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.amount > 1) {
            itemInHand.amount -= 1
        } else {
            player.inventory.removeItem(itemInHand)
        }
    }

    // Поиск свободного места
    private fun findFreeLocation(start: Location, maxAttempts: Int = 20, step: Int = 5): Location? {
        val world = start.world ?: return null
        var currentY = start.blockY

        repeat(maxAttempts) {
            val center = Location(world, start.blockX + 0.5, currentY.toDouble(), start.blockZ + 0.5)
            if (isLocationFree(center)) return center
            currentY += step
        }
        return null
    }

    // Проверка, свободно ли пространство
    private fun isLocationFree(center: Location): Boolean {
        val world = center.world ?: return false

        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) {
                for (z in -RADIUS..RADIUS) {
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared <= RADIUS * RADIUS) {
                        val blockX = center.blockX + x
                        val blockY = center.blockY + y
                        val blockZ = center.blockZ + z
                        val block = world.getBlockAt(blockX, blockY, blockZ)
                        if (!block.isEmpty) return false
                    }
                }
            }
        }
        return true
    }

    // Создание двухслойного стеклянного купола с случайными цветами
    private fun createDome(center: Location) {
        val world = center.world ?: return

        // Получаем цвета для этого купола
        val (outerColor, innerColor) = domeColors[center] ?: Pair(
            GLASS_COLORS.random(),
            GLASS_COLORS.random()
        ).also { domeColors[center] = it }

        // Создаем новый набор блоков для этого купола
        val domeBlocks = HashSet<Block>()
        activeDomsMap[center] = domeBlocks

        val outerRadiusSquared = RADIUS * RADIUS
        val innerRadiusSquared = (RADIUS - 1) * (RADIUS - 1)
        val innerInnerRadiusSquared = (RADIUS - 2) * (RADIUS - 2)

        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) {
                for (z in -RADIUS..RADIUS) {
                    val distanceSquared = x * x + y * y + z * z

                    // Внешний слой купола
                    if (distanceSquared <= outerRadiusSquared && distanceSquared >= innerRadiusSquared) {
                        val blockX = center.blockX + x
                        val blockY = center.blockY + y
                        val blockZ = center.blockZ + z
                        val block = world.getBlockAt(blockX, blockY, blockZ)
                        if (block.isEmpty) {
                            // Случайно выбираем другой цвет для некоторых блоков для создания узора
                            val useAlternateColor = Random.nextDouble() < 0.2
                            block.type = if (useAlternateColor) GLASS_COLORS.random() else outerColor
                            domeBlocks.add(block)
                            allDomeBlocks.add(block)
                        }
                    }
                    // Внутренний слой купола
                    else if (distanceSquared < innerRadiusSquared && distanceSquared >= innerInnerRadiusSquared) {
                        val blockX = center.blockX + x
                        val blockY = center.blockY + y
                        val blockZ = center.blockZ + z
                        val block = world.getBlockAt(blockX, blockY, blockZ)
                        if (block.isEmpty) {
                            // Случайно выбираем другой цвет для некоторых блоков для создания узора
                            val useAlternateColor = Random.nextDouble() < 0.2
                            block.type = if (useAlternateColor) GLASS_COLORS.random() else innerColor
                            domeBlocks.add(block)
                            allDomeBlocks.add(block)
                        }
                    }
                }
            }
        }
    }

    // Удаление купола
    private fun removeDome(center: Location) {
        val blocksToRemove = activeDomsMap[center] ?: return

        for (block in blocksToRemove) {
            if (GLASS_COLORS.contains(block.type)) {
                block.type = Material.AIR
            }
            allDomeBlocks.remove(block)
        }

        // Удаляем игроков из списка находящихся в куполе
        val radiusSquared = RADIUS * RADIUS
        center.world?.players?.forEach { player ->
            if (player.location.distanceSquared(center) <= radiusSquared) {
                playersInDomes.remove(player.uniqueId)
            }
        }

        activeDomsMap.remove(center)
        domeDeletionTasks.remove(center)
        domeColors.remove(center)
    }

    // Телепортировка игроков в купол
    private fun teleportPlayersToSphere(center: Location, activator: Player) {
        val radiusSquared = RADIUS * RADIUS
        val activatorCivilization = CivilizationAPI.getCivilization(activator.uniqueId)

        val playersToTeleport = activator.world.players.filter { player ->
            player.location.distanceSquared(activator.location) <= radiusSquared &&
                    CivilizationAPI.getCivilization(player.uniqueId) != "Нет цивилизации"
        }

        playersToTeleport.forEach { player ->
            player.teleport(center.clone().add(0.0, 1.0, 0.0))
            player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, DURATION_TICKS, 1))

            // Добавляем игрока в список находящихся в куполе
            playersInDomes.add(player.uniqueId)
            player.world.spawnParticle(org.bukkit.Particle.FLAME, player.location.add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, 0.05)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.0f)
            player.sendMessage(
                miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>Вы были телепортированы в купол для PvP</gradient></b>")
            )
        }
    }

    // Планировщик удаления купола
    private fun scheduleRemoval(center: Location) {
        // Отменяем предыдущую задачу для этой локации, если она существует
        domeDeletionTasks[center]?.cancel()

        val task = object : BukkitRunnable() {
            override fun run() {
                // Применяем эффект плавного падения к игрокам внутри купола
                val radiusSquared = RADIUS * RADIUS
                val playersInDome = center.world?.players?.filter { player ->
                    player.location.distanceSquared(center) <= radiusSquared &&
                            CivilizationAPI.getCivilization(player.uniqueId) != "Нет цивилизации"
                } ?: listOf()

                playersInDome.forEach { player ->
                    player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 1)) // 5 секунд
                }

                // Удаляем купол
                removeDome(center)
            }
        }

        task.runTaskLater(plugin, DURATION_TICKS.toLong())
        domeDeletionTasks[center] = task
    }

    // Проверка, находится ли игрок в каком-либо куполе
    private fun isPlayerInAnyDome(player: UUID): Boolean {
        return playersInDomes.contains(player)
    }

    // Обработчик события разрушения блока
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (allDomeBlocks.contains(event.block)) {
            event.isCancelled = true
            event.player.sendMessage(
                miniMessage.deserialize("<red>Вы не можете разрушить блоки купола для PvP.")
            )
        }
    }

    // Защита от взрывов
    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        val iterator = event.blockList().iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (allDomeBlocks.contains(block)) {
                iterator.remove() // Удаляем блок из списка взрываемых блоков
            }
        }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        val iterator = event.blockList().iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (allDomeBlocks.contains(block)) {
                iterator.remove() // Удаляем блок из списка взрываемых блоков
            }
        }
    }

    // Обработчик употребления хоруса
    @EventHandler
    fun onChorusFruitConsume(event: PlayerItemConsumeEvent) {
        val player = event.player

        // Проверяем, является ли предмет хорусом и находится ли игрок в куполе
        if (event.item.type == Material.CHORUS_FRUIT && isPlayerInAnyDome(player.uniqueId)) {
            // 40% шанс успеха
            if (Random.nextDouble() >= CHORUS_SUCCESS_CHANCE) {
                // Отменяем событие употребления
                event.isCancelled = true

                // Уменьшаем количество хоруса в руке игрока
                val itemInHand = player.inventory.itemInMainHand
                if (itemInHand.type == Material.CHORUS_FRUIT) {
                    if (itemInHand.amount > 1) {
                        itemInHand.amount -= 1
                    } else {
                        player.inventory.setItemInMainHand(null)
                    }
                } else {
                    val itemInOffHand = player.inventory.itemInOffHand
                    if (itemInOffHand.type == Material.CHORUS_FRUIT) {
                        if (itemInOffHand.amount > 1) {
                            itemInOffHand.amount -= 1
                        } else {
                            player.inventory.setItemInOffHand(null)
                        }
                    }
                }

                // Сообщаем игроку о неудаче
                player.sendMessage(
                    miniMessage.deserialize("<red>Вам не повезло, хорус оказался несвежим.")
                )

                // Добавляем эффект тошноты на короткое время для визуального эффекта
                player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 20 * 3, 0))
            }
        }
    }

    // Обработчик телепортации (для отслеживания выхода из купола)
    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player

        // Если игрок телепортируется из купола (например, с помощью хоруса)
        if (isPlayerInAnyDome(player.uniqueId)) {
            // Проверяем, находится ли новая локация вне всех куполов
            var stillInDome = false

            for (center in activeDomsMap.keys) {
                val radiusSquared = RADIUS * RADIUS
                if (event.to.distanceSquared(center) <= radiusSquared) {
                    stillInDome = true
                    break
                }
            }

            // Если игрок телепортировался за пределы купола, удаляем его из списка
            if (!stillInDome) {
                playersInDomes.remove(player.uniqueId)
            }
        }
    }
}