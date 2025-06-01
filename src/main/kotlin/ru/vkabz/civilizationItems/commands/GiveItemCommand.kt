package ru.vkabz.civilizationItems.commands

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import ru.vkabz.civilizationItems.features.*

class GiveItemCommand(private val plugin: Plugin) : CommandExecutor {

    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Преобразуем args в изменяемый список для удобства обработки
        val argsList = args.toMutableList()

        // Проверка наличия флага -no_unique_id
        var noUniqueId = false
        if (argsList.contains("-no_unique_id")) {
            if (!sender.isOp) {
                sender.sendMessage(miniMessage.deserialize("<red>Только оператор может использовать флаг -no_unique_id."))
                return true
            }
            noUniqueId = true
            argsList.remove("-no_unique_id")
        }

        // Проверка минимального количества аргументов
        if (argsList.size < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Использование: /giveitem <игрок> <предмет> [количество] [warPoints] [-no_unique_id]"))
            return true
        }

        val playerName = argsList[0]
        val itemName = argsList[1].lowercase()
        val player = Bukkit.getPlayer(playerName)

        if (player == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Игрок <yellow>$playerName</yellow> не найден."))
            return true
        }

        // Инициализация переменных
        var amount = 1
        var warPoints: Int? = null
        var reputationPoints: Int? = null

        // Разбор аргументов в зависимости от предмета
        when (itemName) {
            "warpoints", "war_points", "warpointsitem", "war_points_item" -> {
                // Для WarPointsItem требуется количество очков войны
                if (argsList.size < 4) {
                    sender.sendMessage(miniMessage.deserialize("<red>Использование для WarPointsItem: /giveitem <игрок> warpoints <количество> <warPoints> [-no_unique_id]"))
                    return true
                }

                // Разбор количества предметов (стеков)
                amount = argsList[2].toIntOrNull() ?: 1
                if (amount <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Количество должно быть положительным числом."))
                    return true
                }

                // Разбор количества очков войны
                warPoints = argsList[3].toIntOrNull()
                if (warPoints == null || warPoints <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Количество очков войны должно быть положительным числом."))
                    return true
                }

                // Ограничение максимального количества для предотвращения дюпа через команду
                val maxAllowed = 64
                if (amount > maxAllowed) {
                    sender.sendMessage(miniMessage.deserialize("<red>Максимальное количество для выдачи: <yellow>$maxAllowed</yellow>."))
                    return true
                }
            }
            "reputation", "reputationitem" -> {
                // Для ReputationItem требуется количество репутации
                if (argsList.size < 4) {
                    sender.sendMessage(miniMessage.deserialize("<red>Использование для ReputationItem: /giveitem <игрок> reputation <количество> <reputationPoints> [-no_unique_id]"))
                    return true
                }

                // Разбор количества предметов (стеков)
                amount = argsList[2].toIntOrNull() ?: 1
                if (amount <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Количество должно быть положительным числом."))
                    return true
                }

                // Разбор количества репутации
                reputationPoints = argsList[3].toIntOrNull()
                if (reputationPoints == null || reputationPoints <= 0) {
                    sender.sendMessage(miniMessage.deserialize("<red>Количество репутации должно быть положительным числом."))
                    return true
                }

                // Ограничение максимального количества для предотвращения дюпа через команду
                val maxAllowed = 64
                if (amount > maxAllowed) {
                    sender.sendMessage(miniMessage.deserialize("<red>Максимальное количество для выдачи: <yellow>$maxAllowed</yellow>."))
                    return true
                }
            }
            else -> {
                // Для остальных предметов количество очков войны или репутации не требуется
                if (argsList.size >= 3) {
                    amount = argsList[2].toIntOrNull() ?: 1
                    if (amount <= 0) {
                        sender.sendMessage(miniMessage.deserialize("<red>Количество должно быть положительным числом."))
                        return true
                    }
                }

                // Ограничение максимального количества для предотвращения дюпа через команду
                val maxAllowed = 64
                if (amount > maxAllowed) {
                    sender.sendMessage(miniMessage.deserialize("<red>Максимальное количество для выдачи: <yellow>$maxAllowed</yellow>."))
                    return true
                }
            }
        }

        // Создание и выдача предметов
        val itemStacks = mutableListOf<ItemStack>()
        for (i in 1..amount) {
            val itemStack = when (itemName) {
                "pvp_dome" -> PvPDome.createItem(1, noUniqueId)
                "disorientation" -> DisorientationItem.createItem(1, noUniqueId)
                "regen" -> TerritoryRegenerator.createItem(1)
                "radar" -> EnemyHighlighterItem.createItem(1, noUniqueId)
                "lastchance" -> LastChanceItem.createItem(1, noUniqueId)
                "warpoints", "war_points", "warpointsitem", "war_points_item" -> {
                    // При выдаче WarPointsItem передаем параметр noUniqueId
                    WarPointsItem.createItem(warPoints!!, noUniqueId)
                }
                "reputation", "reputationitem" -> {
                    // При выдаче ReputationItem передаем параметр noUniqueId
                    ReputationItem.createItem(reputationPoints!!, noUniqueId)
                }
                "fire" -> {
                    FireTornadoItem.createItem(noUniqueId)
                }
                "mine" -> {
                    OreHighlighterItem.createItem(noUniqueId)
                }
                "thor" -> {
                    SonOfThorItem.createItem(noUniqueId)
                }
                "angel" -> {
                    AngelPetItem.createItem(noUniqueId)
                }
                "godpickage" -> {
                    GodPickaxeItem.createItem(noUniqueId)
                }
                "speedboots" -> {
                    SpeedBootsItem.createItem(noUniqueId)
                }
                "uniqueaxeitem" -> {
                    UniqueAxeItem.createItem(noUniqueId)
                }
                "helmetofdawn" -> {
                    EternalDawnHelmetItem.createItem(noUniqueId)
                }
                "chestplateofdawn" -> {
                    EternalDawnChestplateItem.createItem(noUniqueId)
                }
                "leggingsofdawn" -> {
                    EternalDawnLeggingsItem.createItem(noUniqueId)
                }
                "bootsofdawn" -> {
                    EternalDawnBootsItem.createItem(noUniqueId)
                }
                "carrot" -> {
                    InfinityCarrotItem.createItem(noUniqueId)
                }
                "escape" -> {
                    EscapeItem.createItem(1, noUniqueId)
                }
                "lockkey" -> {
                    LockKeyItem.createItem(1, noUniqueId)
                }
                "spawnerpickaxe" -> {
                    SpawnerPickaxeItem.createItem()
                }

                else -> {
                    sender.sendMessage(miniMessage.deserialize("<red>Неизвестный предмет: <yellow>$itemName</yellow>."))
                    return true
                }
            }
            itemStacks.add(itemStack)
        }

        // Добавляем предметы в инвентарь игрока
        player.inventory.addItem(*itemStacks.toTypedArray())

        // Отправляем сообщения об успешной выдаче
        val itemDisplayName = when (itemName) {
            "pvp_dome" -> "Купол PvP"
            "disorientation" -> "Дезориентация"
            "regen" -> "Регенератор территории"
            "radar" -> "Подсвечиватель врагов"
            "lastchance" -> "Последний шанс"
            "warpoints", "war_points", "warpointsitem", "war_points_item" -> "Очки Войны"
            "reputation", "reputationitem" -> "Репутация"
            "spawnerpickaxe" -> "Кирка Захвата Спавнера"
            else -> itemName
        }

        val message = when (itemName) {
            "warpoints", "war_points", "warpointsitem", "war_points_item" -> {
                val uniqueIdText = if (noUniqueId) " без уникального ID" else ""
                "<green>Вы выдали игроку <yellow>${player.name}</yellow> предмет <gold>$itemDisplayName</gold> с <gold>$warPoints</gold> очками войны$uniqueIdText."
            }
            "reputation", "reputationitem" -> {
                val uniqueIdText = if (noUniqueId) " без уникального ID" else ""
                "<green>Вы выдали игроку <yellow>${player.name}</yellow> предмет <gold>$itemDisplayName</gold> с <gold>$reputationPoints</gold> репутацией$uniqueIdText."
            }
            else -> "<green>Вы выдали игроку <yellow>${player.name}</yellow> предмет <gold>$itemDisplayName</gold>."
        }

        sender.sendMessage(miniMessage.deserialize(message))
        player.sendMessage(miniMessage.deserialize("<green>Вам был выдан предмет <gold>$itemDisplayName</gold>."))
        return true
    }

    // Функция для капитализации слов
    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
