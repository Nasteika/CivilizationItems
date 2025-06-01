package ru.vkabz.civilizationItems.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class CommandCompleter : TabCompleter {

    private val availableItems = listOf("pvp_dome", "disorientation", "regen", "radar", "lastchance", "warpoints", "reputation", "fire", "mine", "thor", "angel", "godpickage", "speedboots", "uniqueaxeitem", "helmetofdawn", "chestplateofdawn", "leggingsofdawn", "bootsofdawn", "carrot", "escape", "lockkey", "spawnerpickaxe")
    private val warPointsOptions = listOf("10", "20", "50", "100", "200") // Пример возможных значений очков войны
    private val reputationOptions = listOf("5", "10", "15", "20", "25") // Пример возможных значений репутации
    private val quantityOptions = listOf("1", "5", "10", "15", "20", "25", "30", "40", "50", "60", "64")

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        return when (args.size) {
            1 -> { // Список игроков для первого аргумента
                val input = args[0].lowercase()
                sender.server.onlinePlayers.map(Player::getName).filter { it.lowercase().startsWith(input) }
            }
            2 -> { // Список доступных предметов для второго аргумента
                val input = args[1].lowercase()
                availableItems.filter { it.startsWith(input, ignoreCase = true) }
            }
            3 -> { // Список доступных количеств для третьего аргумента
                val selectedItem = args.getOrNull(1)?.lowercase() ?: ""
                when (selectedItem) {
                    "warpoints", "war_points", "warpointsitem", "war_points_item" -> {
                        warPointsOptions.filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                    "reputation", "reputationitem" -> {
                        reputationOptions.filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                    else -> {
                        quantityOptions.filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                }
            }
            4 -> { // Для warpoints и reputation, список warPointsOptions или reputationOptions
                val selectedItem = args.getOrNull(1)?.lowercase() ?: ""
                when (selectedItem) {
                    "warpoints", "war_points", "warpointsitem", "war_points_item" -> {
                        warPointsOptions.filter { it.startsWith(args[3], ignoreCase = true) }
                    }
                    "reputation", "reputationitem" -> {
                        reputationOptions.filter { it.startsWith(args[3], ignoreCase = true) }
                    }
                    else -> {
                        listOf("-no_unique_id")
                    }
                }
            }
            5 -> { // Предлагаем флаг -no_unique_id для warpoints и reputation
                val selectedItem = args.getOrNull(1)?.lowercase() ?: ""
                if (selectedItem in listOf("warpoints", "war_points", "warpointsitem", "war_points_item", "reputation", "reputationitem") && sender.isOp) {
                    if ("-no_unique_id".startsWith(args[4], ignoreCase = true)) {
                        listOf("-no_unique_id")
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
