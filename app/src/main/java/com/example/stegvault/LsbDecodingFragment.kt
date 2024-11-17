package com.example.stegvault

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.SecretKey

class LsbDecodingFragment : Fragment() {

    private lateinit var ivPickImageForDecoding: ImageView
    private lateinit var tvDecodedMessage: TextView
    private lateinit var btnDecodeMessage: Button
    private lateinit var etSecretKey: EditText


    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_lsb_decoding, container, false)

        // Initialize views
        ivPickImageForDecoding = view.findViewById(R.id.ivPickImageForDecoding)
        tvDecodedMessage = view.findViewById(R.id.tvDecodedMessage)
        btnDecodeMessage = view.findViewById(R.id.btnDecodeMessage)
        etSecretKey= view.findViewById(R.id.etSecretKey)

        // Set up image picking
        ivPickImageForDecoding.setOnClickListener {
            pickImageFromGallery()
        }

        // Set up decoding logic
        btnDecodeMessage.setOnClickListener {
            if (selectedImageUri != null) {
                performLsbDecodingWithCoroutine()
            } else {
                Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            ivPickImageForDecoding.setImageURI(selectedImageUri)
            tvDecodedMessage.text = "" // Clear previous message
        }
    }

    private fun performLsbDecodingWithCoroutine() {
        val progressDialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(progressDialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        // Launch a coroutine to perform decoding
        lifecycleScope.launch {
            val password = etSecretKey.text.toString()


            val result = withContext(Dispatchers.IO) {
                val decodedMessage = decodeMessageUsingLsb(selectedImageUri!!)
                if (decodedMessage != null) {
                    try {
                        AESUtils.decrypt(decodedMessage, password) // Decrypt the message using the password
                    } catch (e: Exception) {
                        "Decryption failed. Incorrect password or corrupted data."
                    }
                } else {
                    null
                }
            }

            dialog.dismiss()

            if (result != null && result.isNotEmpty()) {
                tvDecodedMessage.text = "Decoded Message: $result"
            } else {
                tvDecodedMessage.text = "No hidden message found"
            }
        }
    }


    private suspend fun decodeMessageUsingLsb(imageUri: Uri): String? {
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Ensure the image is a PNG
            val convertedBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                val pngFile = convertToPng(bitmap)
                BitmapFactory.decodeFile(pngFile.absolutePath)
            } else {
                bitmap
            }

            decodeLsb(convertedBitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }


    private fun decodeLsb(bitmap: Bitmap): String? {
        val messageBits = mutableListOf<Int>()
        loop@ for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val blue = pixel and 0xFF
                messageBits.add(blue and 1)

                // Check if we have accumulated a complete byte (8 bits)
                if (messageBits.size % 8 == 0) {
                    val byte = messageBits.takeLast(8).reversed().fold(0) { acc, bit -> (acc shl 1) or bit }.toUByte()
                    if (byte.toInt() == 0) break@loop // Stop decoding at null-terminator
                }
            }
        }

        val messageBytes = messageBits.chunked(8)
            .map { it.fold(0) { acc, bit -> (acc shl 1) or bit } }
            .takeWhile { it != 0 } // Stop at null-terminator
            .map { it.toByte() }
            .toByteArray()
        Log.d(TAG, "decodeLsb: Decoded message bytes: ${messageBytes.joinToString(", ")}")
        return String(messageBytes, Charsets.UTF_8)

    }
    private fun extractLsbBits(bitmap: Bitmap): List<Int> {
        val bits = mutableListOf<Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val blue = pixel and 0xFF
                val lsb = blue and 1 // Extract the LSB
                bits.add(lsb)

                // Debugging output
                if (bits.size <= 64) { // Limit the output for readability
                    println("Pixel ($x, $y) - Blue: $blue, LSB: $lsb")
                }
            }
        }
        return bits
    }
    private fun groupBitsIntoBytes(bits: List<Int>): List<Byte> {
        val bytes = mutableListOf<Byte>()
        for (i in bits.indices step 8) {
            if (i + 7 < bits.size) { // Ensure we have 8 bits
                val byte = bits.subList(i, i + 8).reversed().fold(0) { acc, bit -> (acc shl 1) or bit }
                bytes.add(byte.toByte())

                // Debugging output
                println("Bits: ${bits.subList(i, i + 8)} -> Byte: $byte")
            }
        }
        return bytes
    }
    private fun buildMessageFromBytes(bytes: List<Byte>): String {
        val messageBytes = bytes.takeWhile { it != 0.toByte() } // Stop at null-terminator
        val message = String(messageBytes.toByteArray(), Charsets.UTF_8)

        // Debugging output
        println("Decoded message bytes: ${messageBytes.joinToString(", ")}")
        println("Decoded message: $message")

        return message
    }


    private fun convertToPng(bitmap: Bitmap): File {
        val pngFile = File(requireContext().cacheDir, "temp_image.png")
        FileOutputStream(pngFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        return pngFile
    }

    }







