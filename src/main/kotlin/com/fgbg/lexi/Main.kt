import java.awt.*
import java.awt.event.*
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.text.CharacterIterator
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.UIManager

data class Vec2(val x: Int, val y: Int)
data class Vec4(val x: Int, val y: Int, val w: Int, val h: Int)

fun vec4(x: Int, y: Int, size: Vec2): Vec4 = Vec4(x, y, size.x, size.y)
fun Char.isASCII(): Boolean = this <= '\u007F'

interface Glyph {
    var x: Int
    var y: Int
    /**
     * 宽, 高
     */
    var size: Vec2
    fun insert(glyph: Glyph, index: Int = 0): Unit = throw UnsupportedOperationException()
    fun remove(index: Int): Unit = throw UnsupportedOperationException()
    fun draw(g: java.awt.Graphics)
    fun drawSelection(g: java.awt.Graphics, x: Int, y: Int, w: Int, h: Int): Unit = Unit
    fun inRow(p: Vec2): Boolean = p.y >= y && p.y <= y + size.y
    fun inCol(p: Vec2): Boolean = p.x >= x && p.x <= x + size.x
    fun isBefore(p: Vec2): Boolean = p.x < x
    fun intersects(p: Vec2): Boolean = inRow(p) && inCol(p)
    /**
     * 计算图元布局信息, 并设置size属性, 同时返回字图元的位置信息
     */
    fun layout(x: Int, y: Int, g: java.awt.Graphics): Vec4
    /**
     * 必须在layout计算完排版后才能够调用
     */
    fun getHeight(): Int
    /**
     * 必须在layout计算完排版后才能够调用
     */
    fun getWidth(): Int
    fun getPosition(): Vec2 = Vec2(-1, -1)
}

class Character(private val char: Char) : Glyph {
    // private val font = UIManager.getFont("Label.font")
    private val font = Font("Smiley Sans", Font.PLAIN, 16)
    private var offset: Int = 2

    override var x: Int = 0
    override var y: Int = 0

    override var size: Vec2 = Vec2(-1, -1)

    override fun draw(g: java.awt.Graphics) {
        g.font = font
        // drawString的y坐标是相对于基线的, 所以需要加上ascent
        g.drawString(char.toString(), x, y + g.fontMetrics.ascent)
    }

    override fun drawSelection(g: java.awt.Graphics, x: Int, y: Int, w: Int, h: Int) {
        g.fillRect(x, y, w, h)
    }

    override fun getWidth(): Int {
//        if (char == 'i' || char == 'l' || char == 'j') {
//            return this.size.x + offset + 1
//        }
//        return this.size.x + offset
        return this.size.x + offset
    }

    /**
     * 字符采取半宽设计.换句话说，光标点击在字符前半部分，则视为左移，点击在字符后半部分，则视为右移。
     */
    override fun inCol(p: Vec2): Boolean {
        val halfW = getWidth() / 2
        return p.x > x + halfW && p.x <= x + getWidth() + halfW
    }
    override fun isBefore(p: Vec2): Boolean {
        val halfW = getWidth() / 2
        return p.x < x + halfW
    }

    override fun getHeight(): Int {
        return this.size.y
    }

    override fun getPosition(): Vec2 = Vec2(x, y)

    override fun layout(x: Int, y: Int, g: java.awt.Graphics): Vec4 {
        val oldFont = g.font
        this.x = x
        this.y = y

        g.font = font
        this.size = Vec2(g.fontMetrics.charWidth(char), g.fontMetrics.height)
        g.font = oldFont

        return vec4(x, y, size)
    }

    fun getChar(): Char = char
}

class DefaultGlyph : Glyph {
    override var x: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    override var y: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    override var size: Vec2
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun draw(g: Graphics): Unit = Unit
    override fun layout(x: Int, y: Int, g: Graphics): Vec4 = Vec4(0, 0, 0, 0)
    override fun getHeight(): Int {
        TODO("Not yet implemented")
    }

    override fun getWidth(): Int {
        TODO("Not yet implemented")
    }
}

class Row : Glyph {
    private val children = mutableListOf<Glyph>()
    override var x: Int = 0
    override var y: Int = 0
    override var size: Vec2 = Vec2(-1, -1)

    override fun layout(x: Int, y: Int, g: Graphics): Vec4 {
        // 遍历所有的child, 计算它们的位置信息
        var currentX = x
        var maxH = 16
        this.x = x
        this.y = y
        for (glyph in children) {
            maxH = maxOf(glyph.layout(currentX, y, g).h, maxH)
            currentX += glyph.getWidth()
        }
        this.size = Vec2(currentX - x, maxH)
        return vec4(x, y, size)
    }

    fun indexAt(index: Int): Glyph? {
        if (index < 0 || index >= children.size) return null
        return children[index]
    }

    override fun getHeight(): Int = size.y
    override fun getWidth(): Int = size.x
    override fun insert(glyph: Glyph, index: Int): Unit = children.add(index, glyph)
    override fun remove(index: Int): Unit {
        if (index in children.indices) {
            children.removeAt(index)
        }
    }
    override fun draw(g: java.awt.Graphics) {
        for (glyph in children) {
            glyph.draw(g)
        }
    }

    fun drawSelection(g: java.awt.Graphics, from: Int, to: Int) {
        val len = children.size
        if (from > len || from < 0 || to > len || to < 0) return
        for (i in from..<to) {
            val glyph = children[i]
            glyph.drawSelection(g, glyph.getPosition().x, y, glyph.getWidth(), getHeight())
        }
    }

    fun getChildren(): MutableList<Glyph> = children

    fun addAll(glyphs: List<Glyph>): Unit {
        children.addAll(glyphs)
    }

    override fun getPosition(): Vec2 = Vec2(x, y)
}

// Composition.kt
class Composition(glyph: Glyph) : Glyph by glyph {
    private val rows = mutableListOf<Row>()
    private val compositor: Compositor = SimpleCompositor()
    private var caretVisible = true
    var caret = Caret(
        0, 0, 0, 0, 0,
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.createCompatibleImage(1, 1).graphics,
        500
    )

    // 添加选中状态
    var selectionStart: Vec2 = Vec2(-1, -1)  // 选中起始位置 (row, col)
    var selectionEnd: Vec2 = Vec2(-1, -1)    // 选中结束位置

    // 检查是否有选中文本
    fun hasSelection(): Boolean = selectionStart.x >= 0 && selectionEnd.x >= 0

    // 清除选中状态
    fun clearSelection() {
        selectionStart = Vec2(-1, -1)
        selectionEnd = Vec2(-1, -1)
    }

    // 获取选中范围内的所有字符
    fun getSelectedText(): String {
        if (!hasSelection()) return ""

        val (startRow, startCol) = selectionStart
        val (endRow, endCol) = selectionEnd

        val builder = StringBuilder()
        for (row in minOf(startRow, endRow)..maxOf(startRow, endRow)) {
            if (row !in rows.indices) continue

            val rowStart = if (row == minOf(startRow, endRow)) minOf(startCol, endCol) else 0
            val rowEnd = if (row == maxOf(startRow, endRow)) maxOf(startCol, endCol) else rows[row].getChildren().size

            for (col in rowStart until rowEnd) {
                if (col in rows[row].getChildren().indices) {
                    val char = (rows[row].getChildren()[col] as? Character)?.getChar() ?: ' '
                    builder.append(char)
                }
            }
        }
        return builder.toString()
    }

    init {
        rows.add(Row())
    }

    fun insert(glyph: Glyph) {
        val row = rows[caret.row]
        row.insert(glyph, caret.col)
        caret.col++
        if (row.getWidth() > caret.maxWidth) {
            wrapLine()
        }
    }

    fun delete() {
        if (caret.col > 0) {
            rows[caret.row].remove(caret.col - 1)
            caret.col--
        } else if (caret.row > 0) {
            val current = rows.removeAt(caret.row)
            caret.row--
            val previous = rows[caret.row]
            val offset = previous.getChildren().size
            previous.addAll(current.getChildren())
            caret.col = offset
        }
    }

    fun moveLeft() {
        if (caret.col > 0) caret.col--
        else if (caret.row > 0) {
            caret.row--
            caret.col = rows[caret.row].getChildren().size
        }
    }

    fun moveRight() {
        if (caret.col < rows[caret.row].getChildren().size) caret.col++
        else if (caret.row < rows.size - 1) {
            caret.row++
            caret.col = 0
        }

    }
    fun moveUp() {
        if (caret.row > 0) {
            caret.row--
            val upperRowLength = rows[caret.row].getChildren().size
            caret.col = minOf(caret.col, upperRowLength)
        }
    }

    fun moveDown() {
        if (caret.row < rows.size - 1) {
            caret.row++
            val lowerRowLength = rows[caret.row].getChildren().size
            caret.col = minOf(caret.col, lowerRowLength)
        }
    }

    fun newLine() {
        val current = rows[caret.row]
        val newRow = Row()
        current.getChildren().subList(caret.col, current.getChildren().size).toList().apply {
            if (this.isNotEmpty()) newRow.addAll(this)
        }
        current.getChildren().subList(caret.col, current.getChildren().size).clear()
        rows.add(caret.row + 1, newRow)
        caret.row++
        caret.col = 0
    }

    /**
     * 布局, 统一计算所有子图元的位置信息
     */
    fun layout(g: java.awt.Graphics) {
        val x = 20
        var y = 20
        var h = 0

        for ((index, row) in rows.withIndex()) {
            row.layout(x, y, g)
            h = row.getHeight()
            y += h
        }

        val row = rows[caret.row]
        val gly = row.indexAt(caret.col - 1)
        caret.x = (gly?.getPosition()?.x ?: x) + (gly?.getWidth() ?: 0)
        caret.y = row.getPosition().y
        caret.h = gly?.getHeight() ?: 16
    }

    override fun draw(g: java.awt.Graphics) {
        caretVisible = !caretVisible
        caret.graphics = g

        layout(g)

        // 绘制选中背景
        if (hasSelection()) {
            drawSelection(g)
        }

        // 绘制文本
        for (row in rows) {
            row.draw(g)
        }

        // 绘制光标
        if (caretVisible) {
            g.drawLine(caret.x, caret.y, caret.x, caret.y + caret.h)
        }
    }

    fun drawSelection(g: Graphics) {
        val (startRow, startCol) = selectionStart
        val (endRow, endCol) = selectionEnd

        val normalizedStartRow = minOf(startRow, endRow)
        val normalizedEndRow = maxOf(startRow, endRow)
        val normalizedStartCol = minOf(startCol, endCol)
        val normalizedEndCol = maxOf(startCol, endCol)

        val oldColor = g.color
        g.color = Color(0, 120, 215, 100) // 半透明蓝色背景

        for (rowIdx in normalizedStartRow..normalizedEndRow) {
            if (rowIdx == normalizedStartRow && rowIdx == normalizedEndRow) {
                rows[rowIdx].drawSelection(g, normalizedStartCol, normalizedEndCol)
            }
        }

        g.color = oldColor // 恢复默认颜色
    }

    fun getRows(): List<Row> = rows

    private fun wrapLine() {
        newLine()
    }

    fun compose() {
        compositor.compose()
    }

    /**
     * 移动光标
     */
    fun moveCaret(x: Int, y: Int) {
        if (rows.isEmpty()) {
            return
        }
        // n + m 的时间复杂度, 不是n * m
        // 首先遍历n行, 找到光标所在行后, 遍历该行的子图元, 找到光标所在位置后, 移动光标
        for ((rowIdx, row) in rows.withIndex()) {
            val p = Vec2(x, y)
            if (row.inRow(p)) {
                caret.row = rowIdx
                for ((colIdx, child) in row.getChildren().withIndex()) {
                    // 因为半字符的相交设定, 开头首个字符需要进行特判
                    if (colIdx == 0 && child.isBefore(p)) {
                        caret.col = 0
                        return
                    }
                    caret.col = colIdx + 1
                    if (child.inCol(p)) {
                        return
                    }
                }
                // 光标在最后一列
                caret.col = row.getChildren().size
                return
            }
        }
        // 光标在最后一行的最后一列
        caret.row =  rows.size - 1
        caret.col =  rows[caret.row].getChildren().size
    }
}

data class Caret(
    // row本身就是索引下标
    var row: Int = 0,
    // col-1才是索引下标
    var col: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var h: Int = 0,
    var graphics: java.awt.Graphics,
    val maxWidth: Int
)

interface Compositor {
    fun compose()
}

class SimpleCompositor : Compositor {
    override fun compose() {}
}


class Editor : JComponent() {
    private val composition = Composition(DefaultGlyph())
    private var isDragging = false

    init {
        isFocusable = true
        background = Color.WHITE

        // enable InputMethodEvent for on-the-spot pre-editing
        enableEvents(AWTEvent.KEY_EVENT_MASK or AWTEvent.INPUT_METHOD_EVENT_MASK)
        enableInputMethods(true)

        // 定时刷新（可选）
        Timer(500) { repaint() }.start()

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> composition.moveLeft()
                    KeyEvent.VK_RIGHT -> composition.moveRight()
                    KeyEvent.VK_UP -> composition.moveUp()
                    KeyEvent.VK_DOWN -> composition.moveDown()
                    KeyEvent.VK_BACK_SPACE -> composition.delete()
                    KeyEvent.VK_ENTER -> composition.newLine()
                }
                repaint()
            }

            override fun keyTyped(e: KeyEvent) {
                val ch = e.keyChar
                if (ch == '\n') return

                // 只处理 ASCII 字符，中文交给输入法处理
                if (ch.isASCII() && !ch.isISOControl()) {
                    composition.insert(Character(ch))
                    repaint()
                    e.consume() // 吞掉事件
                }
            }
        })

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) { // 左键按下
                    isDragging = true
                    composition.clearSelection()
                    composition.moveCaret(e.x, e.y)

                    composition.selectionStart = Vec2(composition.caret.row, composition.caret.col)
                    repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) {
                    repaint()
                }
            }

            override fun mouseClicked(e: MouseEvent?) {
                isDragging = false
                composition.clearSelection()
                super.mouseClicked(e)
                e?: return
                composition.moveCaret(e.x, e.y)
                repaint()
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    composition.moveCaret(e.x, e.y)
                    composition.selectionEnd = Vec2(composition.caret.row, composition.caret.col)
                    repaint()
                }
            }
        })
    }

    /**
     * 必须重写, 否则无法处理中文输入, 但实际上并不需要实现任何具体功能. 目前其底层原理尚未可知, 唯一能够确定的是
     * </br>
     * 不重写当前方法, {@link #processInputMethodEvent(InputMethodEvent)} 不会被调用.
     */
    override fun getInputMethodRequests(): InputMethodRequests {
        return object: InputMethodRequests {
            override fun getTextLocation(offset: TextHitInfo?): Rectangle = Rectangle(0, 0, 0, 0)
            override fun getLocationOffset(x: Int, y: Int): TextHitInfo? = null
            override fun getInsertPositionOffset(): Int = 0
            override fun getCommittedText(beginIndex: Int, endIndex: Int, attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator = AttributedString("").getIterator()
            override fun getCommittedTextLength(): Int = 0
            override fun cancelLatestCommittedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? = null
            override fun getSelectedText(attributes: Array<out AttributedCharacterIterator.Attribute>?): AttributedCharacterIterator? = null
        }
    }

    override fun processInputMethodEvent(e: InputMethodEvent?) {
        super.processInputMethodEvent(e)
        if (e == null) return

        val text = e.text ?: return

        // 输入法提交字符个数
        val committedCount = e.committedCharacterCount

        var ch = text.first()
        var index = 0
        while (ch != CharacterIterator.DONE) {
            ch = text.next()
            index++
        }

        text.first()
        val confirmedText = StringBuilder()
        for (i in 0 until committedCount) {
            confirmedText.append(text.current())
            text.next()
        }

        if (confirmedText.isNotEmpty()) {
            for (c in confirmedText) {
                composition.insert(Character(c))
            }
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        composition.draw(g)
    }
}


fun main() {
    javax.swing.SwingUtilities.invokeLater {
        val frame = javax.swing.JFrame("飞哥不鸽文本编辑器").apply {
            defaultCloseOperation = javax.swing.JFrame.EXIT_ON_CLOSE
            contentPane.add(Editor())
            setSize(600, 400)
            isVisible = true
        }
    }
}