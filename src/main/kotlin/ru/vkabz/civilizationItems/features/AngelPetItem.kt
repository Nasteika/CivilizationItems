package ru.vkabz.civilizationItems.features

import CooldownManager
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import org.bukkit.entity.Vex
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import ru.vkabz.civilizationItems.api.CivilizationAPI
import java.util.*
import kotlin.collections.HashSet

object AngelPetItem : Listener {

    private const val ITEM_NAME = "<b><gradient:#FFFFFF:#00FFFF>Ангел - питомец</gradient></b>"
    private const val VEX_COUNT = 5
    private const val VEX_DAMAGE = 10.0 // Урон, наносимый ангелами
    private const val VEX_LIFETIME_TICKS = 20 * 10 // 10 секунд
    private const val COOLDOWN_TIME_MILLIS = 15000L // 15 секунд

    private val miniMessage = MiniMessage.miniMessage()
    private lateinit var plugin: Plugin
    lateinit var ITEM_KEY: NamespacedKey

    // Список призванных ангелов
    private val summonedVexes = HashSet<UUID>()

    /**
     * Инициализация объекта с указанием плагина.
     * Должен вызываться в методе onEnable() основного класса плагина.
     */
    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "angel_pet_item")

        // Регистрация слушателя событий
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Создание предмета "Ангел - питомец".
     *
     * @param noUniqueId Флаг, указывающий, создаётся ли предмет без уникального идентификатора.
     * @return Созданный ItemStack.
     */
    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.TOTEM_OF_UNDYING, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(miniMessage.deserialize(ITEM_NAME).decoration(TextDecoration.ITALIC, false))
            lore(
                listOf(
                    miniMessage.deserialize("<white>Призывает <yellow>$VEX_COUNT</yellow> ангелов, атакующих врагов.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Ангелы наносят <red>${VEX_DAMAGE / 2} сердца</red> урона.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Время жизни: <yellow>${VEX_LIFETIME_TICKS / 20} секунд</yellow>.")
                        .decoration(TextDecoration.ITALIC, false),
                    miniMessage.deserialize("<white>Кулдаун: <yellow>${COOLDOWN_TIME_MILLIS / 1000} секунд</yellow>.")
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
     * Активация предмета "Ангел - питомец".
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

        // Призываем ангелов
        summonVexes(player)

        player.sendMessage(
            miniMessage.deserialize("<b><gradient:#F8D21C:#FF952D>ПРИЗЫВ</gradient></b> <dark_gray>» <white>Вы призвали ангелов-защитников!")
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
     * Призывает ангелов (Vex), атакующих врагов.
     *
     * @param player Игрок, который призывает ангелов.
     */
    private fun summonVexes(player: Player) {
        val playerCivilization = CivilizationAPI.getCivilization(player.uniqueId)

        for (i in 1..VEX_COUNT) {
            val location = player.location.clone().add((Math.random() * 4 - 2), 1.0, (Math.random() * 4 - 2))
            val vex = player.world.spawnEntity(location, EntityType.VEX) as Vex

            vex.isCustomNameVisible = false
            vex.isSilent = true
            vex.setCanPickupItems(false)
            vex.setGravity(true)
//            vex.owner = player
            vex.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = VEX_DAMAGE
            vex.setMetadata("angel_pet_owner", FixedMetadataValue(plugin, player.uniqueId.toString()))

            // Добавляем в список призванных ангелов
            summonedVexes.add(vex.uniqueId)

            // Удаляем ангела через время жизни
            object : BukkitRunnable() {
                override fun run() {
                    vex.remove()
                    summonedVexes.remove(vex.uniqueId)
                }
            }.runTaskLater(plugin, VEX_LIFETIME_TICKS.toLong())
        }
    }

    /**
     * Обработчик события выбора цели ангелом.
     */
    @EventHandler
    fun onEntityTarget(event: EntityTargetEvent) {
        val entity = event.entity
        if (entity is Vex && summonedVexes.contains(entity.uniqueId)) {
            val target = event.target
            if (target is Player) {
                val ownerUUID = entity.getMetadata("angel_pet_owner").firstOrNull()?.asString()?.let { UUID.fromString(it) }
                if (ownerUUID != null) {
                    val ownerCivilization = CivilizationAPI.getCivilization(ownerUUID)
                    val targetCivilization = CivilizationAPI.getCivilization(target.uniqueId)

                    // Проверяем, что цель является врагом
                    if (ownerCivilization == targetCivilization) {
                        event.isCancelled = true
                    } else {
                        // Цель является врагом, ангел атакует
                        event.target = target
                    }
                } else {
                    // Если нет информации о владельце, отменяем атаку
                    event.isCancelled = true
                }
            } else {
                // Цель не игрок, отменяем атаку
                event.isCancelled = true
            }
        }
    }
}
