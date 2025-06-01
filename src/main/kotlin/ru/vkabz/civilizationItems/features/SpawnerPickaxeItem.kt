package ru.vkabz.civilizationItems.features

import org.bukkit.*
import org.bukkit.block.CreatureSpawner
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.NamespacedKey
import kotlin.random.Random

object SpawnerPickaxeItem : Listener {

    private lateinit var key: NamespacedKey

    fun init(plugin: JavaPlugin) {
        key = NamespacedKey(plugin, "spawner_pickaxe")
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun createItem(): ItemStack {
        val item = ItemStack(Material.WOODEN_PICKAXE)
        val meta = item.itemMeta!!
        meta.setDisplayName("§6Кирка 'Спавнеро-дробилка'")
        meta.lore = listOf(
            "§7Позволяет добывать спавнеры",
            "§7Шанс выпадения спавнера: §a20%",
            "§7Шанс получить яйцо призыва: §a50%"
        )
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onSpawnerBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        val itemInHand = player.inventory.itemInMainHand

        if (block.type != Material.SPAWNER) return
        if (itemInHand.type != Material.WOODEN_PICKAXE) return
        if (itemInHand.itemMeta?.displayName != "§6Кирка 'Спавнеро-дробилка'") return

        // ✅ Получаем тип существа ДО удаления блока
        val entityType = (block.state as? CreatureSpawner)?.spawnedType

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(this::class.java), Runnable {
            if (block.type != Material.AIR) return@Runnable

            val spawnerDropChance = 0.20
            val eggDropChance = 0.50

            val dropSpawner = Random.nextDouble() <= spawnerDropChance

            if (dropSpawner) {
                val spawnerItem = ItemStack(Material.SPAWNER)
                block.world.dropItemNaturally(block.location, spawnerItem)
                player.sendMessage("§aВам выпал спавнер!")

                val dropEgg = Random.nextDouble() <= eggDropChance

                if (dropEgg && entityType != null) {
                    val eggMaterial = Material.getMaterial("${entityType.name}_SPAWN_EGG")
                    if (eggMaterial != null) {
                        val egg = ItemStack(eggMaterial)
                        block.world.dropItemNaturally(block.location, egg)
                        player.sendMessage("§eВам выпало яйцо призыва!")
                    }
                } else if (dropEgg) {
                    player.sendMessage("§eЯйцо не выдалось — спавнер был пустым.")
                }

                // Эффекты
                player.world.spawnParticle(
                    Particle.CLOUD, player.location.add(0.0, 1.0, 0.0),
                    20, 0.2, 0.5, 0.2, 0.01
                )
                player.world.spawnParticle(
                    Particle.FLAME, player.location.add(0.0, 1.0, 0.0),
                    15, 0.2, 0.5, 0.2, 0.01
                )
                player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
                player.inventory.itemInMainHand.amount = 0

            } else {
                player.sendMessage("§cСпавнер не выпал.")
            }
        }, 1L)
    }
}
