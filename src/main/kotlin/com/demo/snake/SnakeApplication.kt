package com.demo.snake

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.system.exitProcess

const val window_width: Int = 30
const val widnow_height: Int = 20
const val max_columns: Int = 15
const val max_rows: Int = 15
const val chanceToDrop = 2

val initCells = listOf(
        Cell(6, 5),
        Cell(5, 5),
        Cell(4, 5),
        Cell(4, 4)
)

fun main(args: Array<String>) {
    Game(
            Snake(initCells),
            Apples(growthSpeed = chanceToDrop)
    )
}

data class Game(var snake: Snake, var apples: Apples) {
    var terminal: Terminal = DefaultTerminalFactory()
            .setInitialTerminalSize(TerminalSize(window_width, widnow_height))
            .setTerminalEmulatorTitle("Snake Game :D")
            .createTerminal()

    private var gameState = GameState.INITIALIZATION
    private var score = 3
    private lateinit var gameCycle: Timer

    init {
        terminal.enterPrivateMode()
        terminal.flush()
        startGame()
    }

    private fun startGame() {
        terminal.clearScreen()
        snake = Snake(initCells.map { it.copy() })
        apples = Apples()
        score = snake.tail.size + 1
        drawGame()
        startGameCycle()
        gameState = GameState.IN_PROGRESS
    }

    private fun startGameCycle() {
        val period = when {
            score <= 5 -> 200L
            score <= 10 -> 180L
            score <= 15 -> 160L
            score <= 20 -> 140L
            else -> 120L
        }
        gameCycle = Timer("gameCycle", false)
        gameCycle.scheduleAtFixedRate(period, period) {
            gameCycle()
        }
    }

    private fun gameCycle() {
        val keyStroke: KeyStroke? = terminal.pollInput()
        systemProcess(keyStroke)
        if (gameState != GameState.IN_PROGRESS)
            return

        update(keyStroke)
    }

    private fun systemProcess(keyStroke: KeyStroke?) {
        when (keyStroke?.keyType) {
            KeyType.Enter ->
                when (gameState) {
                    GameState.GAME_OVER -> {
                        gameState = GameState.INITIALIZATION
                        gameCycle.cancel()
                        startGame()
                    }
                    GameState.IN_PROGRESS -> pause()
                    GameState.PAUSE -> resume()
                }
            KeyType.Escape -> {
                gameCycle.cancel()
                exitProcess(0)
            }
        }
    }

    private fun resume() {
        gameState = GameState.IN_PROGRESS
    }

    private fun gameOver(score: Int) {
        gameState = GameState.GAME_OVER
        val gameOverMsg = "Game Over!"
        val scoreMsg = "Score: $score"
        val pressMsg = "Press enter to start"
        printInPosition(gameOverMsg, Cell((window_width-gameOverMsg.length) / 2, max_rows + 1))
        printInPosition(scoreMsg, Cell((window_width-scoreMsg.length) / 2, max_rows + 2))
        printInPosition(pressMsg, Cell((window_width-pressMsg.length) / 2, max_rows + 3))
        terminal.flush()
    }

    private fun pause() {
        gameState = GameState.PAUSE
        val pauseMsg = "Press enter to resume"
        printInPosition(pauseMsg, Cell((window_width-pauseMsg.length) / 2, max_rows + 2))
        terminal.flush()
    }

    private fun update(keyStroke: KeyStroke?) {
        val newDirection = when (keyStroke?.keyType) {
            KeyType.ArrowUp -> Direction.UP
            KeyType.ArrowDown -> Direction.DOWN
            KeyType.ArrowLeft -> Direction.LEFT
            KeyType.ArrowRight -> Direction.RIGHT
            else -> null
        }

        snake.turn(newDirection)
        snake.move()
        val increased = snake.eat(apples)
        apples.grow()

        score = snake.tail.size + 1
        val isOver = snake.tail.contains(snake.head)
                || snake.cells.any {
            it.x !in 1.until(max_columns - 1) || it.y !in 1.until(max_rows)
        }

        drawGame()
        if (increased) {
            gameCycle.cancel()
            startGameCycle()
        }

        if (isOver) gameOver(score)
    }

    private fun drawGame() {
        terminal.clearScreen()
        apples.cells.forEach { apple -> printInPosition('', apple) }
        snake.tail.forEach { tailSegment -> printInPosition('o', tailSegment) }
        val headChar = when (snake.currDirection) {
            Direction.UP -> '∆'
            Direction.DOWN -> '¥'
            Direction.LEFT -> '≤'
            Direction.RIGHT -> '≥'
        }
        printInPosition(headChar, snake.head)
        drawBox()
        terminal.flush()
    }

    private fun drawBox() {
        for (i in 0 until max_columns) {
            printInPosition('_', Cell(i, 0))
            printInPosition('¯', Cell(i, max_rows))
        }
        for (i in 0 until max_rows) {
            printInPosition('|', Cell(0, i))
            printInPosition('|', Cell(max_columns - 1, i))
        }
    }

    private fun printInPosition(input: Char, cell: Cell) {
        terminal.setCursorPosition(cell.x, cell.y)
        terminal.putCharacter(input)
        moveCursorToRightBotCorner()
    }

    private fun printInPosition(input: String, cell: Cell) {
        for ((ind, char) in input.withIndex()) {
            terminal.setCursorPosition(cell.x + ind, cell.y)
            terminal.putCharacter(char)
        }
        moveCursorToRightBotCorner()
    }

    private fun moveCursorToRightBotCorner() {
        terminal.setCursorPosition(window_width + 1, widnow_height + 1)
    }
}

data class Snake(val cells: List<Cell>, private var direction: Direction = Direction.RIGHT) {
    val head = cells.first()
    val tail: MutableList<Cell> = cells.drop(1) as MutableList<Cell>

    val currDirection: Direction
        get() = direction

    fun move() {
        for (i in tail.size - 1 downTo 1) {
            tail[i].moveTailToNextCell(tail[i - 1])
        }
        tail[0].moveTailToNextCell(head)
        head.moveHead(direction)
    }

    fun turn(newDirection: Direction?) {
        if (newDirection != null && !direction.isOppositeTo(newDirection)) {
            direction = newDirection
        }
    }

    fun eat(apples: Apples): Boolean {
        if (head in apples.cells) {
            apples.cells.remove(head)
            tail.add(Cell(tail.last().x, tail.last().y))
            return true
        }
        return false
    }
}

data class Apples(
        val cells: MutableSet<Cell> = mutableSetOf(),
        val growthSpeed: Int = 2
) {
    fun grow() {
        if ((0..10).random() >= growthSpeed) return

        val newApple = Cell((1 until max_columns).random(), (1 until (max_rows - 1)).random())
        cells.add(newApple)
    }

    fun clear() {
        cells.clear()
    }
}

data class Cell(var x: Int, var y: Int) {
    fun moveHead(direction: Direction) {
        x += direction.dx
        y += direction.dy
    }

    fun moveTailToNextCell(cell: Cell) {
        x = cell.x
        y = cell.y
    }
}

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),      // ^
    DOWN(0, 1),     // V
    LEFT(-1, 0),    // <-
    RIGHT(1, 0);    // ->

    fun isOppositeTo(direction: Direction) =
            dx + direction.dx == 0 && dy + direction.dy == 0
}

enum class GameState {
    INITIALIZATION,
    IN_PROGRESS,
    PAUSE,
    GAME_OVER
}


