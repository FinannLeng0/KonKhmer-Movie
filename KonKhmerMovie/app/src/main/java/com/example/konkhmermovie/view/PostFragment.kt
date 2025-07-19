package com.example.konkhmermovie.view

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentPostBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PostFragment : Fragment() {

    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var selectedVideoUri: Uri? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val storageRef by lazy { FirebaseStorage.getInstance().reference }
    private val dbRef by lazy { FirebaseDatabase.getInstance().reference.child("videos") }

    private var snackbar: Snackbar? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasShownConnectedOnce = false

    private var uploadingDialog: AlertDialog? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                binding.imagePreview.setImageURI(it)
                binding.textUploadThumbnail.text = "Thumbnail selected"
                binding.imagePreview.setBackgroundColor(Color.TRANSPARENT)
            }
        }

    private val videoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedVideoUri = it
                binding.videoPreview.setVideoURI(it)
                binding.videoPreview.seekTo(100)
                binding.buttonPlayVideo.visibility = View.VISIBLE
                binding.textUploadVideo.visibility = View.GONE
                binding.videoPreview.setBackgroundColor(Color.TRANSPARENT)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupNetworkCallback()

        if (auth.currentUser == null) {
            Toast.makeText(requireContext(), "Please login to upload videos", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_postFragment_to_profileFragment)
            return
        }

        binding.imagePreview.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.textUploadThumbnail.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.textUploadVideo.setOnClickListener { videoPickerLauncher.launch("video/*") }

        binding.videoPreview.setOnClickListener {
            if (binding.videoPreview.isPlaying) {
                binding.videoPreview.pause()
                binding.buttonPlayVideo.visibility = View.VISIBLE
            } else {
                binding.videoPreview.start()
                binding.buttonPlayVideo.visibility = View.GONE
            }
        }

        binding.buttonPlayVideo.setOnClickListener {
            binding.videoPreview.start()
            binding.buttonPlayVideo.visibility = View.GONE
        }

        binding.videoPreview.setOnPreparedListener { mediaPlayer ->
            val videoWidth = mediaPlayer.videoWidth
            val videoHeight = mediaPlayer.videoHeight
            val containerWidth = binding.videoContainer.width

            if (videoWidth > 0 && videoHeight > 0 && containerWidth > 0) {
                val aspectRatio = videoHeight.toFloat() / videoWidth
                val newHeight = (containerWidth * aspectRatio).toInt()

                val videoParams = binding.videoPreview.layoutParams
                videoParams.height = newHeight
                binding.videoPreview.layoutParams = videoParams

                val containerParams = binding.videoContainer.layoutParams
                containerParams.height = newHeight
                binding.videoContainer.layoutParams = containerParams
            }

            binding.buttonPlayVideo.visibility = View.VISIBLE
        }

        binding.buttonUpload.setOnClickListener { uploadVideo() }
    }

    private fun uploadVideo() {
        val title = binding.editTitle.text.toString().trim()
        val desc = binding.editDescription.text.toString().trim()

        if (title.isEmpty() || desc.isEmpty() || selectedImageUri == null || selectedVideoUri == null) {
            Toast.makeText(requireContext(), "Please fill all fields and select thumbnail/video", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return

        binding.buttonUpload.isEnabled = false
        showUploadingDialog()

        val videoId = UUID.randomUUID().toString()
        val thumbnailRef = storageRef.child("thumbnails/$videoId.jpg")
        val videoRef = storageRef.child("videos/$videoId.mp4")

        lifecycleScope.launch {
            try {
                Log.d("PostFragment", "Start uploading thumbnail")
                val thumbUploadTask = thumbnailRef.putFile(selectedImageUri!!)
                thumbUploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                    binding.textUploadStatus.text = "Uploading thumbnail... $progress%"
                }
                val thumbResult = thumbUploadTask.await()
                Log.d("PostFragment", "Thumbnail uploaded")

                val thumbUrl = thumbResult.storage.downloadUrl.await().toString()
                Log.d("PostFragment", "Thumbnail URL: $thumbUrl")

                val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
                Log.d("PostFragment", "Start uploading video")
                val videoUploadTask = videoRef.putFile(selectedVideoUri!!, metadata)
                videoUploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                    binding.textUploadStatus.text = "Uploading video... $progress%"
                }
                val videoResult = videoUploadTask.await()
                Log.d("PostFragment", "Video uploaded")

                val videoUrl = videoResult.storage.downloadUrl.await().toString()
                Log.d("PostFragment", "Video URL: $videoUrl")

                val videoData = mapOf(
                    "title" to title,
                    "description" to desc,
                    "thumbnailUrl" to thumbUrl,
                    "videoUrl" to videoUrl,
                    "userId" to uid,
                    "timestamp" to System.currentTimeMillis() //current time in milliseconds
                )

                dbRef.child(videoId).setValue(videoData).await()
                Log.d("PostFragment", "Database updated with video data")

                dismissUploadingDialog()
                Toast.makeText(requireContext(), "Video uploaded successfully!", Toast.LENGTH_SHORT).show()

                binding.buttonUpload.text = "Post Now"
                binding.buttonUpload.isEnabled = false

                resetForm()

                binding.buttonUpload.postDelayed({
                    binding.buttonUpload.text = "Post Now"
                    binding.buttonUpload.isEnabled = true
                    binding.textUploadStatus.visibility = View.GONE
                }, 3000)

            } catch (e: Exception) {
                Log.e("PostFragment", "Upload failed", e)
                dismissUploadingDialog()
                binding.buttonUpload.isEnabled = true
                binding.textUploadStatus.visibility = View.GONE
                showError("Upload failed: ${e.message}")
            }
        }

    }

    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            .setAction("Retry") { uploadVideo() }
            .show()
    }

    private fun resetForm() {
        _binding?.editTitle?.setText("")
        _binding?.editDescription?.setText("")

        _binding?.imagePreview?.setImageDrawable(null)
        _binding?.imagePreview?.setBackgroundColor(Color.DKGRAY)

        _binding?.videoPreview?.stopPlayback()
        _binding?.videoPreview?.setVideoURI(null)
        _binding?.videoPreview?.setBackgroundColor(Color.DKGRAY)

        _binding?.textUploadThumbnail?.text = "Upload Thumbnail"
        _binding?.textUploadVideo?.text = "Upload Video"
        _binding?.textUploadVideo?.visibility = View.VISIBLE
        _binding?.textUploadStatus?.visibility = View.GONE
        _binding?.buttonPlayVideo?.visibility = View.GONE

        selectedImageUri = null
        selectedVideoUri = null
    }
    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()
                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true
                        Snackbar.make(requireView(), "Internet Connected", Snackbar.LENGTH_SHORT)
                            .setAnchorView(R.id.bottom_navigation)
                            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success_green))
                            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                            .show()
                    }
                }
            }

            override fun onLost(network: Network) {
                activity?.runOnUiThread {
                    hasShownConnectedOnce = false
                    showNoInternetSnackbar()
                }
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
        if (!isNetworkConnected()) {
            hasShownConnectedOnce = false
            showNoInternetSnackbar()
        } else {
            hasShownConnectedOnce = true
        }
    }

    private fun isNetworkConnected(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetSnackbar() {
        if (snackbar?.isShown == true) return
        snackbar = Snackbar.make(requireView(), "No Internet Connection", Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.bottom_navigation)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }

    private fun showUploadingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_uploading, null)
        uploadingDialog = AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        uploadingDialog?.show()
    }

    private fun dismissUploadingDialog() {
        uploadingDialog?.dismiss()
    }

    override fun onPause() {
        super.onPause()
        binding.videoPreview.pause()
    }

    override fun onStop() {
        super.onStop()
        binding.videoPreview.stopPlayback()
    }

    override fun onDestroyView() {
        snackbar?.dismiss()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
        uploadingDialog?.dismiss()
        binding.videoPreview.stopPlayback()
        _binding = null
        super.onDestroyView()
    }
}