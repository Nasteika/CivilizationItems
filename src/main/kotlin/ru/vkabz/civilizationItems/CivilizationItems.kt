package ru.vkabz.civilizationItems

import com.github.sirblobman.combatlogx.api.ICombatLogX
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import ru.vkabz.civilizationItems.commands.CommandCompleter
import ru.vkabz.civilizationItems.commands.GiveItemCommand
import ru.vkabz.civilizationItems.event.DuplicationProtection
import ru.vkabz.civilizationItems.event.GlobalEventHandler
import ru.vkabz.civilizationItems.features.*


class CivilizationItems : JavaPlugin() {

    override fun onEnable() {

        PvPDome.init(this)
        DisorientationItem.init(this)
        TerritoryRegenerator.init(this)
        EnemyHighlighterItem.init(this)
        LastChanceItem.init(this)
        WarPointsItem.init(this) // Инициализируем WarPointsItem
        ReputationItem.init(this)
        FireTornadoItem.init(this)
        OreHighlighterItem.init(this)
        SonOfThorItem.init(this)
        AngelPetItem.init(this)
        GodPickaxeItem.init(this)
        SpeedBootsItem.init(this)
        UniqueAxeItem.init(this)
        EternalDawnHelmetItem.init(this)
        EternalDawnChestplateItem.init(this)
        EternalDawnLeggingsItem.init(this)
        EternalDawnBootsItem.init(this)
        InfinityCarrotItem.init(this)
        SpawnerPickaxeItem.init(this)
        EscapeItem.init(this)
        LockKeyItem.init(this)


        // Инициализация и регистрация защиты от дюпа
        DuplicationProtection.init(this)

        server.pluginManager.registerEvents(GlobalEventHandler(this), this)

        // Регистрация команды и комплитера
        getCommand("giveitem")?.setExecutor(GiveItemCommand(this))
        getCommand("giveitem")?.tabCompleter = CommandCompleter()

        logger.info("Unique Item Plugin enabled!")
    }

    override fun onDisable() {
        logger.info("Unique Item Plugin disabled!")
    }
}
