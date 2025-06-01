package ru.vkabz.civilizationItems.features

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.nio.file.Files.getAttribute
import java.util.*
import java.util.UUID

object UniqueAxeItem : Listener {

    private lateinit var plugin: Plugin
    private val miniMessage = MiniMessage.miniMessage()
    lateinit var ITEM_KEY: NamespacedKey

    // Константы
    private const val DAMAGE_MULTIPLIER = 0.25 // Уменьшаем урон до 25% от стандартного
    private const val DURABILITY_MULTIPLIER = 50 // Увеличиваем урон щитов и брони в 4 раза

    fun init(plugin: Plugin) {
        this.plugin = plugin
        ITEM_KEY = NamespacedKey(plugin, "unique_axe_item")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun createItem(noUniqueId: Boolean = false): ItemStack {
        val item = ItemStack(Material.NETHERITE_AXE, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.apply {
            displayName(
                miniMessage.deserialize("<b><gradient:#FF0000:#FFA500>Щитобой</gradient></b>")
                    .decoration(TextDecoration.ITALIC, false)
            )
            lore(
                listOf(
                    miniMessage.deserialize("<white>Ломает щиты и броню в 4 раза быстрее, но имеет сниженный урон.").decoration(TextDecoration.ITALIC, false)
                )
            )
            isUnbreakable = true

            // Помечаем предмет как уникальный топор
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

            // Уменьшаем урон топора
            // Удаляем все существующие модификаторы атрибутов
//            getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.attributeModifiers?.clear()

            // Добавляем новый модификатор для уменьшения урона
//            addAttributeModifier(
//                Attribute.GENERIC_ATTACK_DAMAGE,
//                AttributeModifier(
//                    UUID.randomUUID(),
//                    "generic.attackDamage",
//                    -5.0, // Уменьшаем на 5 (стандартный урон Netherite Axe = 9, новый = 4)
//                    AttributeModifier.Operation.ADD_NUMBER
//                )
//            )

            // Добавляем стандартные зачарования или любые другие по желанию
//            addEnchant(Enchantment.DIG_SPEED, 5, true) // Например, Efficiency V
//            addEnchant(Enchantment.DURABILITY, 3, true) // Например, Unbreaking III
        }

        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // Проверяем, что атакующий - игрок
        if (event.damager !is Player) return
        val player = event.damager as Player

        // Проверяем, держит ли игрок уникальный топор
        val itemInHand = player.inventory.itemInMainHand
        val meta = itemInHand.itemMeta ?: return
        val pdc = meta.persistentDataContainer

        if (!pdc.has(ITEM_KEY, PersistentDataType.BYTE)) return

        // Логируем атаку
        plugin.logger.info("Player ${player.name} использует Щитобой для атаки ${event.entity.type} с урона ${event.damage}")

        // Уменьшаем урон
        val originalDamage = event.damage
        val newDamage = originalDamage * DAMAGE_MULTIPLIER
        event.damage = newDamage
        plugin.logger.info("Урон уменьшен с $originalDamage до $newDamage")

        // Проверяем, что цель имеет броню или щит
        val target = event.entity
        if (target is Player) {
            // Проверяем наличие щита
            if (target.isBlocking) {
                val shield = target.inventory.itemInOffHand
                if (shield.type == Material.SHIELD) {
                    // Уменьшаем прочность щита
                    shield.durability = (shield.durability + DURABILITY_MULTIPLIER).toShort()
                    target.sendMessage(miniMessage.deserialize("<red>Ваш щит был повреждён щитобоем!"))
                    plugin.logger.info("Щит игрока ${target.name} повреждён. Новая прочность: ${shield.durability}")
                }
            }

            // Уменьшаем прочность брони
            target.inventory.armorContents.forEach { armor ->
                if (armor != null && armor.type != Material.AIR) {
                    armor.durability = (armor.durability + DURABILITY_MULTIPLIER).toShort()
                    target.inventory.setArmorContents(target.inventory.armorContents)
                    target.sendMessage(miniMessage.deserialize("<red>Ваша броня была повреждена щитобоем!"))
                    plugin.logger.info("Броня игрока ${target.name} типа ${armor.type} повреждена. Новая прочность: ${armor.durability}")
                }
            }
        }
    }
}
