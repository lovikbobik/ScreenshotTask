package ScreenshotTask

import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.event.EventHandler
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.shape.StrokeLineJoin
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import javax.imageio.ImageIO

class ScreenshotTask : Application() {
    private lateinit var stage: Stage
    private lateinit var drawLayout: AnchorPane
    private var imageView = ImageView()
    private var configFile = getConfigFile()


    override fun start(stage: Stage) {
        this.stage = stage
        val scene = screenshotScene()

        stage.title = "Screen"
        stage.scene = scene
        stage.show()
    }

    private fun screenshotScene(): Scene {
        val rootScreenshot = VBox()
        val menuBar = setMenuBarScreenshot()

        val slider = Slider(0.0, 10.0, 0.0)
        slider.blockIncrement = 1.0
        slider.majorTickUnit = 1.0
        slider.minorTickCount = 0
        slider.isShowTickLabels = true
        slider.isSnapToTicks = true

        val rollUpCheckBox = CheckBox("roll up")
        val makeScreenshotButton = Button("make ")

        rootScreenshot.children.addAll(menuBar, makeScreenshotButton, slider, rollUpCheckBox)

        val scene = Scene(rootScreenshot, 500.0, 120.0)

        makeScreenshotButton.onAction = EventHandler {
            if (rollUpCheckBox.isSelected)
                stage.isIconified = true

            Thread.sleep(
                if (slider.value.toInt() != 0)
                    1000 * slider.value.toLong()
                else 200
            )
            val img = SwingFXUtils.toFXImage(
                Robot().createScreenCapture(Rectangle(Toolkit.getDefaultToolkit().screenSize)),
                null
            )
            stage.scene = drawScene(img)
            stage.isMaximized = true

        }
        return scene
    }

    private fun drawScene(image: WritableImage): Scene {
        val scrollPane = ScrollPane()
        val vBox = VBox()
        drawLayout = AnchorPane()
        val toolLayout = HBox()
        this.imageView.image = image
        val menu = setMenuBarPaint()
        val colorPicker = ColorPicker(Color.RED)
        val colorWeight = Slider(1.0, 20.0, 1.0)
        val cutCheckBox = CheckBox("Cutting")


        var cutXStart = 0.0
        var cutYStart = 0.0
        var cutXLast: Double
        var cutYLast: Double

        val ratio = 0.85
        val width = Toolkit.getDefaultToolkit().screenSize.getWidth() * ratio
        val height = Toolkit.getDefaultToolkit().screenSize.getHeight() * ratio
        val canvas = Canvas(width, height)
        this.imageView.fitWidth = width
        this.imageView.fitHeight = height

        val graphicsContext = canvas.graphicsContext2D
        graphicsContext.globalAlpha = 0.7
        graphicsContext.lineJoin = StrokeLineJoin.ROUND

        canvas.onMousePressed = EventHandler { event ->
            if (cutCheckBox.isSelected) {
                cutXStart = event.x
                cutYStart = event.y
            } else {
                graphicsContext.lineWidth = colorWeight.value
                graphicsContext.stroke = colorPicker.value
                graphicsContext.beginPath()
            }
        }
        canvas.onMouseDragged = EventHandler { event ->
            if (cutCheckBox.isSelected) {
                //
            } else {
                val brushSize = colorWeight.value
                val x = event.x - brushSize / 2
                val y = event.y - brushSize / 2
                if (event.button == MouseButton.SECONDARY) {
                    graphicsContext.clearRect(x, y, brushSize, brushSize)
                } else {
                    graphicsContext.lineTo(event.x, event.y)
                    graphicsContext.stroke()
                }
            }
        }
        canvas.onMouseReleased = EventHandler { event ->
            if (cutCheckBox.isSelected) {
                cutYLast = event.y
                cutXLast = event.x

                var yLeftMin = minOf(cutYStart, cutYLast)
                var yRightMin = maxOf(cutYStart, cutYLast)

                var xLeftMin = minOf(cutXStart, cutXLast)
                var xRightMin = maxOf(cutXStart, cutXLast)

                if (xRightMin > this.imageView.fitWidth) xRightMin = this.imageView.fitWidth
                if (yRightMin > this.imageView.fitHeight) yRightMin = this.imageView.fitHeight

                if (xLeftMin < 0) xLeftMin = 0.0
                if (yLeftMin < 0) yLeftMin = 0.0

                val parameters = SnapshotParameters()
                parameters.viewport = Rectangle2D(
                    xLeftMin,
                    yLeftMin,
                    xRightMin - xLeftMin,
                    yRightMin - yLeftMin
                )
                val cutImage: WritableImage = this.imageView.snapshot(parameters, null)
                this.imageView.fitWidth = cutImage.width
                this.imageView.fitHeight = cutImage.height
                this.imageView.image = cutImage

                canvas.width = xRightMin
                canvas.height = yRightMin

                canvas.translateX = -xLeftMin
                canvas.translateY = -yLeftMin

            } else {
                graphicsContext.closePath()
            }
        }

        scrollPane.content = drawLayout
        scrollPane.maxWidth = width + 2
        toolLayout.children.addAll(colorPicker, colorWeight, cutCheckBox)
        vBox.children.addAll(menu, toolLayout, scrollPane)
        drawLayout.children.addAll(this.imageView, canvas)

        val scene = Scene(vBox)

        val saveItem = KeyCodeCombination(KeyCode.S, KeyCodeCombination.CONTROL_DOWN, KeyCodeCombination.SHIFT_DOWN)
        val fastSaveItem = KeyCodeCombination(KeyCode.S, KeyCodeCombination.CONTROL_DOWN)
        val newScreenshotItem = KeyCodeCombination(KeyCode.N, KeyCodeCombination.CONTROL_DOWN)

        scene.accelerators[saveItem] = Runnable { saveFileAsDirectory() }
        scene.accelerators[fastSaveItem] = Runnable { fastSaveFile() }
        scene.accelerators[newScreenshotItem] = Runnable {
            stage.isMaximized = false
            stage.scene = screenshotScene()
        }

        return scene
    }

    private fun setMenuBarScreenshot(): MenuBar {
        val menuBar = MenuBar()

        val menu = Menu("File")

        val openItem = MenuItem("Open")
        val exitItem = MenuItem("Exit")

        openItem.onAction = EventHandler {
            open()
        }
        exitItem.onAction = EventHandler {
            Platform.exit()
        }

        menu.items.addAll(openItem, exitItem)

        menuBar.menus.add(menu)
        return menuBar
    }

    private fun setMenuBarPaint(): MenuBar {
        val menuBar = MenuBar()

        val menuFile = Menu("File")

        val newScreenshotItem = MenuItem("New Screenshot, ctrl + n")
        val openItem = MenuItem("Open")
        val saveItem = MenuItem("Save, ctrl + shift + s")
        val fastSaveItem = MenuItem("Fast Save, ctrl + s")
        val exitItem = MenuItem("Exit")

        newScreenshotItem.onAction = EventHandler {
            stage.isMaximized = false
            stage.scene = screenshotScene()
        }
        openItem.onAction = EventHandler {
            open()
        }
        saveItem.onAction = EventHandler {
            saveFileAsDirectory()
        }
        fastSaveItem.onAction = EventHandler {
            fastSaveFile()
        }
        exitItem.onAction = EventHandler {
            Platform.exit()
        }

        menuFile.items.addAll(newScreenshotItem, openItem, saveItem, fastSaveItem, exitItem)

        menuBar.menus.addAll(menuFile)
        return menuBar
    }

    private fun open() {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.addAll(
            FileChooser.ExtensionFilter("All Files", "*.*"),
            FileChooser.ExtensionFilter("JPG", "*.jpg", "*.jpeg"),
            FileChooser.ExtensionFilter("PNG", "*.png"),
        )
        val file = fileChooser.showOpenDialog(stage) ?: return
        try {
            val image = SwingFXUtils.toFXImage(ImageIO.read(file), null)
            stage.scene = drawScene(image)
        } catch (e: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.contentText = e.toString()
            alert.show()
        }
        stage.isMaximized = false
        stage.isMaximized = true
    }

    private fun getConfigFile(): File {
        val pathname = System.getenv("LOCALAPPDATA")
            ?: System.getenv("XDG_DATA_HOME")
            ?: "./"
        return File("$pathname\\ScreenshotTask.conf")
    }

    private fun getFile(pathname: String): File {
        var saveFile = File("$pathname\\image.png")
        var i = 1
        while (saveFile.exists()) {
            saveFile = File("$pathname\\image ($i).png")
            i++
        }
        return saveFile
    }

    private fun fastSaveFile() {
        val pathname = System.getenv("HOME")
            ?: System.getenv("USERPROFILE")?.plus("\\Desktop")
            ?: "./"
        saveFile(File(pathname))
    }

    private fun saveFile(directory: File) {
        val parameters = SnapshotParameters()
        parameters.viewport = Rectangle2D(
            drawLayout.layoutX,
            drawLayout.layoutY,
            imageView.fitWidth,
            imageView.fitHeight
        )
        val image = drawLayout.snapshot(parameters, null)

        val file = getFile(directory.toString())
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file)
        } catch (error: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.contentText = error.toString()
            alert.showAndWait()
            return
        }
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.contentText = "Saved!"
        alert.show()
    }

    private fun saveFileAsDirectory() {
        val directoryChooser = DirectoryChooser()
        try {
            directoryChooser.initialDirectory = File(configFile.readText())
        } catch (_: Exception) {
        }
        val directory = directoryChooser.showDialog(stage) ?: return

        saveFile(directory)
        configFile.writeText(directory.toString())
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ScreenshotTask::class.java)
        }
    }
}
