package com.example.stegvault

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.SecretKey

class LsbEncodingFragment : Fragment() {

    private lateinit var ivPickImage: ImageView
    private lateinit var tvSelectedImage: TextView
    private lateinit var etMessageToEncode: TextInputEditText
    private lateinit var etSecretKey: EditText
    private lateinit var etConfirmSecretKey: EditText
    private lateinit var btnEncodeMessage: Button

    private var selectedImageUri: Uri? = null
    private var encodedImageBitmap: Bitmap? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_lsb_encoding, container, false)

        ivPickImage = view.findViewById(R.id.ivPickImage)
        tvSelectedImage = view.findViewById(R.id.tvSelectedImage)
        etMessageToEncode = view.findViewById(R.id.etMessageToEncode)
        btnEncodeMessage = view.findViewById(R.id.btnEncodeMessage)
        etSecretKey=view.findViewById(R.id.etSecretKey)
        etConfirmSecretKey= view.findViewById(R.id.etConfirmSecretKey)

        ivPickImage.setOnClickListener {
            pickImageFromGallery()
        }

        btnEncodeMessage.setOnClickListener {
            if (selectedImageUri != null && etMessageToEncode.text.toString().isNotEmpty()) {
                performLsbEncodingWithCoroutine()
            } else {
                Toast.makeText(requireContext(), "Select an image and enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/png"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            ivPickImage.setImageURI(selectedImageUri)
            tvSelectedImage.text = "Image Selected"
        }
    }

    private fun performLsbEncodingWithCoroutine() {
        val progressDialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null)
        val progressBar: ProgressBar = progressDialogView.findViewById(R.id.progressBar)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(progressDialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        // Launch a coroutine to perform encoding
        lifecycleScope.launch {
            val password = etSecretKey.text.toString()
            val confirmPassword = etConfirmSecretKey.text.toString()



            if (password != confirmPassword) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val message = etMessageToEncode.text.toString()
            val encryptedMessage = try {
                AESUtils.encrypt(message, password) // Encrypt the message using the provided password
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Encryption failed", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                encodeMessageUsingLsb(selectedImageUri!!, encryptedMessage)
            }

            dialog.dismiss()

            if (result != null) {
                encodedImageBitmap = result
                showEncodedImageDialog()
            } else {
                Toast.makeText(requireContext(), "Encoding failed", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private suspend fun encodeMessageUsingLsb(imageUri: Uri, message: String): Bitmap? {
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Convert the bitmap to PNG if it is not already in PNG format
            val pngFile = convertToPng(originalBitmap)

            // Use the PNG image for encoding
            val pngBitmap = BitmapFactory.decodeFile(pngFile.absolutePath)
            encodeLsb(pngBitmap, message)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }




    private fun encodeLsb(bitmap: Bitmap, message: String): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val messageBytes = message.toByteArray(Charsets.UTF_8) + byteArrayOf(0) // Add null-terminator
        Log.d(TAG, "encodeLsb: Encoded message bytes: ${messageBytes.joinToString(", ")}")
        val messageBits = messageBytes.flatMap { byte ->
            (7 downTo 0).map { (byte.toInt() shr it) and 1 } // Extract bits from most significant to least significant
        }

        var bitIndex = 0
        loop@ for (y in 0 until mutableBitmap.height) {
            for (x in 0 until mutableBitmap.width) {
                if (bitIndex >= messageBits.size) break@loop

                val pixel = mutableBitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                // Modify the least significant bit of the blue channel
                val newBlue = (blue and 0xFE) or messageBits[bitIndex]
                val newPixel = (0xFF shl 24) or (red shl 16) or (green shl 8) or newBlue

                mutableBitmap.setPixel(x, y, newPixel)
                bitIndex++
            }
        }

        return mutableBitmap
    }


    private fun showEncodedImageDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_encoded_image, null)
        val ivEncodedImage: ImageView = dialogView.findViewById(R.id.ivEncodedImage)
        val btnSave: Button = dialogView.findViewById(R.id.btnSave)

        // Display the encoded image in the ImageView
        encodedImageBitmap?.let {
            ivEncodedImage.setImageBitmap(it)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnSave.setOnClickListener {
            encodedImageBitmap?.let { bitmap ->
                val savedFile = saveImageToLocalStorage(requireContext(), bitmap, "lsb_encoded_image.jpg")
                if (savedFile != null) {
                    Toast.makeText(requireContext(), "Image saved: ${savedFile.path}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun convertToPng(bitmap: Bitmap): File {
        val pngFile = File(requireContext().cacheDir, "temp_image.png")
        FileOutputStream(pngFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        return pngFile
    }

    private fun saveImageToLocalStorage(context: Context, bitmap: Bitmap, fileName: String): File? {
        val pngFileName = if (fileName.endsWith(".png")) fileName else fileName.replaceAfterLast('.', "png")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (API level 29) and above
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pngFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
                File(uri.path) // Return a dummy file object
            }
        } else {
            // For Android 9 (API level 28) and below
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val savedFile = File(downloadsDir, pngFileName)
            try {
                FileOutputStream(savedFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                savedFile
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

}
