package com.example.stegvault

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
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

class EncodingFragment : Fragment() {

    private lateinit var ivPickImage: ImageView
    private lateinit var tvSelectedImage: TextView
    private lateinit var etMessageToEncode: TextInputEditText
    private lateinit var etSecretKey: TextInputEditText
    private lateinit var etConfirmSecretKey: TextInputEditText
    private lateinit var btnEncodeMessage: Button

    private var selectedImageUri: Uri? = null
    private var encodedImageFile: File? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_encoding, container, false)

        ivPickImage = view.findViewById(R.id.ivPickImage)
        tvSelectedImage = view.findViewById(R.id.tvSelectedImage)
        etMessageToEncode = view.findViewById(R.id.etMessageToEncode)
        etSecretKey = view.findViewById(R.id.etSecretKey)
        etConfirmSecretKey = view.findViewById(R.id.etConfirmSecretKey)
        btnEncodeMessage = view.findViewById(R.id.btnEncodeMessage)

        ivPickImage.setOnClickListener {
            pickImageFromGallery()
        }

        btnEncodeMessage.setOnClickListener {
            if (selectedImageUri != null && etMessageToEncode.text.toString().isNotEmpty() && etSecretKey.text.toString().isNotEmpty()) {
                performEncodingWithCoroutine()
            } else {
                Toast.makeText(requireContext(), "Select an image, enter a message, and a password", Toast.LENGTH_SHORT).show()
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
            ivPickImage.setImageURI(selectedImageUri)
            tvSelectedImage.text = "Image Selected"
        }
    }

    private fun performEncodingWithCoroutine() {
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

            // Encrypt the message using AES
            val encryptedMessage = try {
                AESUtils.encrypt(message, password)
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Encryption failed", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                encodeMessageInExif(selectedImageUri!!, encryptedMessage)
            }

            dialog.dismiss()

            if (result != null) {
                encodedImageFile = result
                showEncodedImageDialog()
            } else {
                Toast.makeText(requireContext(), "Encoding failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun encodeMessageInExif(imageUri: Uri, message: String): File? {
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri)
            val tempFile = File(requireContext().cacheDir, "temp_image.jpg")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val exifInterface = ExifInterface(tempFile.absolutePath)
            exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, message)
            exifInterface.saveAttributes()

            tempFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun showEncodedImageDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_encoded_image, null)
        val ivEncodedImage: ImageView = dialogView.findViewById(R.id.ivEncodedImage)
        val btnSave: Button = dialogView.findViewById(R.id.btnSave)

        encodedImageFile?.let {
            ivEncodedImage.setImageURI(Uri.fromFile(it))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnSave.setOnClickListener {
            encodedImageFile?.let { file ->
                val savedFile = saveImageToLocalStorage(requireContext(), file, "encoded_image.jpg")
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

    private fun saveImageToLocalStorage(context: Context, file: File, fileName: String): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it).use { outputStream ->
                    file.inputStream().copyTo(outputStream!!)
                }
                File(uri.path)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val savedFile = File(downloadsDir, fileName)
            try {
                file.copyTo(savedFile, overwrite = true)
                savedFile
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }
}
