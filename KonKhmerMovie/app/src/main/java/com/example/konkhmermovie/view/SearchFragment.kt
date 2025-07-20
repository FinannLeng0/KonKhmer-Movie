package com.example.konkhmermovie.view

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.konkhmermovie.R
import com.example.konkhmermovie.adapter.SearchHistoryAdapter
import com.example.konkhmermovie.adapter.ThumbnailMovieAdapter
import com.example.konkhmermovie.databinding.FragmentSearchBinding
import com.example.konkhmermovie.model.Movie
import com.example.konkhmermovie.model.SearchHistoryItem
import com.google.android.material.snackbar.Snackbar
import com.google.common.reflect.TypeToken
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import android.os.Build

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ThumbnailMovieAdapter
    private val allMovies = mutableListOf<Movie>()

    private lateinit var historyAdapter: SearchHistoryAdapter
    private var searchHistoryList = mutableListOf<SearchHistoryItem>()

    private var snackbar: Snackbar? = null
    private var hasShownConnectedOnce = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val PREFS_NAME = "search_history_prefs"
    private val PREFS_KEY = "history_list"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.searchBar.clearFocus()
        applyCustomFontToAllText(binding.root)
        setupNetworkCallback()




        adapter = ThumbnailMovieAdapter { movie ->
            val imageToShow =
                if (movie.thumbnailUrl.isNotEmpty()) movie.thumbnailUrl else movie.imageUrl

            saveSearchToHistory(movie.title, imageToShow)

            val action = SearchFragmentDirections.actionSearchFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                imageToShow,
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        binding.searchRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.searchRecyclerView.adapter = adapter

        // --- Setup history RecyclerView ---
        searchHistoryList = getSearchHistory()?.toMutableList() ?: mutableListOf()
        historyAdapter = SearchHistoryAdapter(searchHistoryList) { item ->
            // On history item click: fill search bar & search
            binding.searchBar.setText(item.title)
            binding.searchBar.setSelection(item.title.length)
            hideSearchHistory()
            filterMovies(item.title)
        }

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = historyAdapter

        binding.noResultLayout.visibility = View.GONE
        binding.searchRecyclerView.visibility = View.GONE
        binding.historyRecyclerView.visibility = View.GONE
        binding.cancelText.visibility = View.GONE

        loadAllMovies()

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.searchBar.text.isNullOrEmpty()) {
                showSearchHistory()
                binding.noResultLayout.visibility = View.GONE
            } else {
                hideSearchHistory()
            }
        }

        binding.searchBar.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty()) {
                hideSearchHistory()
            }
        }

        binding.searchBar.setOnClickListener {
            if (binding.searchBar.text.isNullOrEmpty()) {
                showSearchHistory()
                binding.noResultLayout.visibility = View.GONE
            }
        }

        binding.cancelText.setOnClickListener {
            binding.searchBar.setText("")
            binding.searchBar.clearFocus()
            binding.cancelText.visibility = View.GONE
            hideSearchHistory()
            binding.noResultLayout.visibility = View.VISIBLE
            binding.searchRecyclerView.visibility = View.GONE
        }


        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}


            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                val text = query.toString()
                binding.cancelText.isVisible = text.isNotEmpty() || binding.searchBar.hasFocus()

                if (text.isEmpty()) {
                    //showSearchHistory()  -- Update to Next Version
                    binding.noResultLayout.visibility = View.GONE
                    binding.searchRecyclerView.visibility = View.GONE
                } else {
                    hideSearchHistory()
                    filterMovies(text)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (binding.searchBar.hasFocus() && binding.searchBar.text.isNullOrEmpty()) {
            showSearchHistory()
        } else {
            hideSearchHistory()
        }
        binding.cancelText.visibility = if (binding.searchBar.hasFocus() || !binding.searchBar.text.isNullOrEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }


    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()

                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true

                        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
                        Snackbar.make(coordinatorLayout, "Internet Connected", Snackbar.LENGTH_SHORT)
                            .setDuration(3000)
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
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetSnackbar() {
        if (snackbar?.isShown == true) return

        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
        snackbar = Snackbar.make(coordinatorLayout, "No Internet Connection", Snackbar.LENGTH_LONG)
            .setDuration(5000) // 👈 5 seconds
            .setAnchorView(R.id.bottom_navigation)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }

    private fun loadAllMovies() {
        allMovies.clear()
        val currentUser = FirebaseAuth.getInstance().currentUser

        val moviesRef = FirebaseDatabase.getInstance().getReference("movies")

        moviesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                for (categorySnapshot in snapshot.children) {
                    for (movieSnapshot in categorySnapshot.children) {
                        val movie = movieSnapshot.getValue(Movie::class.java)
                        if (movie != null) allMovies.add(movie)
                    }
                }

                if (currentUser != null) {
                    val videosRef = FirebaseDatabase.getInstance().getReference("videos")
                    videosRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (videoSnapshot in snapshot.children) {
                                val video = videoSnapshot.getValue(Movie::class.java)
                                if (video != null) allMovies.add(video)
                            }

                            _binding?.let {
                                filterMovies(it.searchBar.text.toString())
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            _binding?.let {
                                filterMovies(it.searchBar.text.toString())
                            }
                        }
                    })
                } else {
                    _binding?.let {
                        filterMovies(it.searchBar.text.toString())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    private fun filterMovies(query: String) {
        val filtered = if (query.isEmpty()) {
            emptyList()
        } else {
            allMovies.filter {
                it.title.startsWith(query, ignoreCase = true)
            }
        }

        if (filtered.isNotEmpty()) {
            adapter.submitList(filtered)
            binding.noResultLayout.visibility = View.GONE
            binding.searchRecyclerView.visibility = View.VISIBLE

        } else {
            adapter.submitList(emptyList())
            binding.noResultLayout.visibility = View.VISIBLE
            binding.searchRecyclerView.visibility = View.GONE
        }
    }



    private fun showSearchHistory() {
        val parent = binding.historyRecyclerView.parent as? ViewGroup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
            parent?.suppressLayout(true)
        }

        searchHistoryList = getSearchHistory().toMutableList()
        if (searchHistoryList.isNotEmpty()) {
            historyAdapter = SearchHistoryAdapter(searchHistoryList) { item ->
                binding.searchBar.setText(item.title)
                binding.searchBar.setSelection(item.title.length)
                hideSearchHistory()
                filterMovies(item.title)
            }
            binding.historyRecyclerView.visibility = View.VISIBLE
            binding.historyRecyclerView.adapter = historyAdapter
            binding.searchRecyclerView.visibility = View.GONE
            binding.noResultLayout.visibility = View.GONE
        } else {
            binding.historyRecyclerView.visibility = View.GONE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.historyRecyclerView.post {
                parent?.suppressLayout(false)
            }
        }
    }

    private fun hideSearchHistory() {
        val parent = binding.historyRecyclerView.parent as? ViewGroup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent?.suppressLayout(true)
        }

        binding.historyRecyclerView.visibility = View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.historyRecyclerView.post {
                parent?.suppressLayout(false)
            }
        }
    }



    private fun saveSearchToHistory(title: String, thumbnailUrl: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()

        val historyJson = prefs.getString(PREFS_KEY, "[]")
        val type = object : TypeToken<MutableList<SearchHistoryItem>>() {}.type
        val historyList: MutableList<SearchHistoryItem> = gson.fromJson(historyJson, type) ?: mutableListOf()

        // Remove duplicates
        historyList.removeAll { it.title.equals(title, ignoreCase = true) }

        // Add new on top
        historyList.add(0, SearchHistoryItem(title, thumbnailUrl))

        // max 10 items
        if (historyList.size > 11) {
            historyList.removeAt(historyList.size - 1)
        }

        prefs.edit().putString(PREFS_KEY, gson.toJson(historyList)).apply()
    }

    private fun getSearchHistory(): List<SearchHistoryItem> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()

        val historyJson = prefs.getString(PREFS_KEY, "[]")
        val type = object : TypeToken<List<SearchHistoryItem>>() {}.type

        return gson.fromJson(historyJson, type) ?: emptyList()
    }

    private fun applyCustomFontToAllText(view: View) {
        val typeface = ResourcesCompat.getFont(requireContext(), R.font.movie) ?: return
        when (view) {
            is TextView -> view.typeface = typeface
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyCustomFontToAllText(view.getChildAt(i))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
        }
    }
}
