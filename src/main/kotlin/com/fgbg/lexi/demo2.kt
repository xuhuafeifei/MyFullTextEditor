package com.fgbg.lexi
import java.awt.GraphicsEnvironment
import javax.swing.JTextPane

fun main() {
    val fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
    fonts.filter { it.contains("Smiley", ignoreCase = true) || it.contains("得意黑") }
        .forEach { println(it) }
    javax.swing.SwingUtilities.invokeLater {
        val frame = javax.swing.JFrame("飞哥不鸽文本编辑器").apply {
            defaultCloseOperation = javax.swing.JFrame.EXIT_ON_CLOSE
            contentPane.add(JTextPane())
            setSize(600, 400)
            isVisible = true
        }
    }
}
