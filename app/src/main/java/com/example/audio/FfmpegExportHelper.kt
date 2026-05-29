package com.example.audio

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FfmpegExportHelper {
    private const val TAG = "FfmpegExportHelper"

    fun splitChannels(
        inputFile: File,
        outputDir: File,
        baseName: String,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        asFlac: Boolean,
        onChannelDone: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> {
        val files = WavHelper.splitMultichannelWav(
            inputFile = inputFile,
            outputDir = outputDir,
            baseName = baseName,
            channelCount = channelCount,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample
        )
        // Call the callback to update progress smoothly
        for (i in 1..files.size) {
            onChannelDone(i, files.size)
        }
        return files
    }

    fun stereoDownmix(
        inputFile: File,
        outputFile: File,
        sampleRate: Int,
        bitsPerSample: Int,
        asFlac: Boolean
    ): Boolean {
        return try {
            WavHelper.downmixToStereWav(
                inputFile = inputFile,
                outputFile = outputFile,
                sourceChannelCount = 6, // Default source channel count
                sampleRate = sampleRate,
                bitsPerSample = bitsPerSample
            )
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Stereo downmix failed: ${e.message}")
            false
        }
    }

    fun zipFiles(files: List<File>, zipFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name))
                    FileInputStream(f).use { it.copyTo(zos) }
                    zos.closeEntry()
                    f.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ZIP failed: ${e.message}")
            false
        }
    }
}
