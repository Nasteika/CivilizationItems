package ru.vkabz.civilizationItems.features

import CooldownManager
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.*


object FireTornadoItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#FF4500:#FF8C00>Огненный смерч</gradient></b>"
    private const val RADIUS = 10
    private const val DURATION_TICKS = 20 * 30 // 30 секунд
    private const val COOLDOWN_TIME_MILLIS = 120000L // 2 минуты
    private const val FIRE_DAMAGE_INCREASE = 8.0 // Повышенный урон от огня

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    // Set to keep track of players affected by the tornado
    private val affectedPlayers = mutableSetOf<UUID>()

    private val fireProtectedPlayers = mutableSetOf<UUID>() // Игроки с активной защитой от огня


    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "fire_tornado_item")

        // Регистрация слушателя событий
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создание предмета "Огненный смерч".
     *
     * @param noUniqueId Флаг, указывающий, создаётся ли предмет без уникального идентификатора.
     * @return Созданный ItemStack.
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.FIRE_CHARGE, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Создаёт огненный смерч, поджигающий игроков и блоки в радиусе <yellow>$RADIUS</yellow> блоков.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Повышенный урон от огня: <red>$FIRE_DAMAGE_INCREASE</red>.").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Используйте правым кликом, чтобы активировать.").decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Может задеть игроков вашей цивилизации.").decoration(TextDecoration.ITALIC, false)
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
            addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true)
            // Скрываем отображение зачарований
            addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Активация предмета "Огненный смерч".
     *
     * @param player Игрок, который использует предмет.
     * @param item Используемый предмет.
     */
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

        // Создаём смерч
        val center = player.location.clone().apply { y += 1.0 } // Немного выше уровня ног
        createFireTornado(center, player)
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 1))

        player.sendMessage(
            miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вы использовали предмет ${ITEM_NAME}. Огненный смерч активирован в радиусе <yellow>$RADIUS</yellow> блоков.")
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
     * Создаёт огненный смерч в указанной локации.
     *
     * @param center Центр смерча.
     * @param activator Игрок, активировавший смерч.
     */
    private fun createFireTornado(center: Location, activator: Player) {
        val world = center.world ?: return

        // Устанавливаем блоки огня в радиусе RADIUS по всему объёму
        for (x in -RADIUS..RADIUS) {
            for (y in -RADIUS..RADIUS) { // Полная высота смерча
                for (z in -RADIUS..RADIUS) {
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared <= RADIUS * RADIUS) {
                        val blockX = center.blockX + x
                        val blockY = center.blockY + y
                        val blockZ = center.blockZ + z
                        val block = world.getBlockAt(blockX, blockY, blockZ)
                        if (block.type == Material.AIR) {
                            block.type = Material.FIRE
                        }
                    }
                }
            }
        }

        // Применяем эффект поджигания к игрокам в радиусе, исключая активатора
        Bukkit.getOnlinePlayers().filter { it.world == world && it.location.distanceSquared(center) <= RADIUS * RADIUS }
            .forEach { target ->
                if (target.uniqueId != activator.uniqueId) {
                    // Проверяем наличие эффекта огнеупорности
                    if (!target.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
                        // Проверяем, что игрок не уже горит от другого источника
                        if (target.fireTicks <= 0) {
                            target.sendMessage(
                                miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>УНИКАЛЬНЫЕ ПРЕДМЕТЫ</gradient></b> <dark_gray>» <white>Вы попали под действие огненного смерча! Потушите себя водой или выпейти зелье огнестойкости!")
                            )
                        }
                        target.setFireTicks(DURATION_TICKS) // Устанавливаем продолжительность горения
                        // Добавляем игрока в список затронутых смерчем
                        affectedPlayers.add(target.uniqueId)
                    }
                }
            }

        // Запускаем задачу для нанесения урона игрокам каждые 2 секунды
        object : BukkitRunnable() {
            override fun run() {
                val iterator = affectedPlayers.iterator()
                while (iterator.hasNext()) {
                    val uuid = iterator.next()
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null) {
                        iterator.remove()
                        continue
                    }
                    if (!player.isOnline) {
                        iterator.remove()
                        continue
                    }
                    if (player.fireTicks <= 0) {
                        // Игрок не горит, прекращаем наносить урон
                        iterator.remove()
                        continue
                    }
                    if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
                        // Игрок имеет огнеупорность, прекращаем наносить урон
                        iterator.remove()
                        continue
                    }
                    // Наносим дополнительный урон
                    val playerCivilization = CivilizationAPI.getCivilization(player.uniqueId)
                    val activatorCivilization = CivilizationAPI.getCivilization(activator.uniqueId)

                    if (player == activator) {
                        iterator.remove()
                        continue
                    }

                    if (playerCivilization == activatorCivilization) {
                        player.damage(5.0)
                    } else {
                        player.damage(FIRE_DAMAGE_INCREASE, activator)
                    }

                }
            }
        }.runTaskTimer(plugin, 0L, 40L) // Повторяется каждые 40 тиков (2 секунды)
    }



    /**
     * Слушатель для отмены стандартного урона от огня для затронутых игроков
     */


}
