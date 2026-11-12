package com.brianrobinson.fonds.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** On-device text recognition (ML Kit, bundled model, works offline). Strong on type, weak on cursive. */
class OcrEngine(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(imagePath: String): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
        val result = recognizer.process(image).awaitResult()
        return layout(result)
    }

    /** Keep block structure as paragraphs so letters still read like letters. */
    private fun layout(text: Text): String =
        text.textBlocks
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
            .trim()
}
