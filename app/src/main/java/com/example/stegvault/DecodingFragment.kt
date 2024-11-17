package com.example.stegvault

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import java.io.IOException
import java.io.InputStream

class DecodingFragment : Fragment() {

    private lateinit var ivPickImageForDecoding: ImageView
    private lateinit var tvDecodedMessage: TextView
    private lateinit var etSecretKey: TextInputEditText
    private lateinit var btnDecodeMessage: Button

    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_decoding, container, false)

        ivPickImageForDecoding = view.findViewById(R.id.ivPickImageForDecoding)
        tvDecodedMessage = view.findViewById(R.id.tvDecodedMessage)
        etSecretKey = view.findViewById(R.id.etSecretKey)
        btnDecodeMessage = view.findViewById(R.id.btnDecodeMessage)

        ivPickImageForDecoding.setOnClickListener {
            pickImageFromGallery()
        }

        btnDecodeMessage.setOnClickListener {
            if (selectedImageUri != null) {
                performDecodingWithCoroutine()
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

    private fun performDecodingWithCoroutine() {
        val progressDialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null)
        val progressBar: ProgressBar = progressDialogView.findViewById(R.id.progressBar)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(progressDialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        // Launch a coroutine to perform decoding
        lifecycleScope.launch {
            val password = etSecretKey.text.toString()
            if (password.isEmpty()) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Please enter a password", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                val decodedMessage = decodeMessageFromExif()
                if (decodedMessage != null) {
                    try {
                        AESUtils.decrypt(decodedMessage, password) // Decrypt using the password
                    } catch (e: Exception) {
                        "Decryption failed. Incorrect password or corrupted data."
                    }
                } else {
                    null
                }
            }

            dialog.dismiss()

            if (!result.isNullOrEmpty()) {
                tvDecodedMessage.text = "Decoded Message: $result"
            } else {
                tvDecodedMessage.text = "No hidden message found in EXIF data"
            }
        }
    }

    private fun decodeMessageFromExif(): String? {
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(selectedImageUri!!)
            inputStream?.use {
                val exifInterface = ExifInterface(it)
                exifInterface.getAttribute(ExifInterface.TAG_USER_COMMENT)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
