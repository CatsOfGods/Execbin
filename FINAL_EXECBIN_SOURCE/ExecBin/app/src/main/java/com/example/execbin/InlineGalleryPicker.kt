package com.example.execbin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

/**
 * Telegram-style inline gallery picker.
 * Shows a grid of device photos, allows multi-select, and supports captions.
 *
 * Usage:
 *   val picker = InlineGalleryPicker(context, isDark, accentColor, ...)
 *   picker.show(parentContainer) // slides up from bottom
 *   picker.onSend = { photos, caption -> ... }
 */
class InlineGalleryPicker(
    private val context: Context,
    private val isDarkTheme: Boolean,
    private val accentColor: Int,
    private val bgColor: Int,
    private val cardBg: Int,
    private val textColor: Int,
    private val subTextColor: Int
) {
    /** Called when user taps Send with selected photo bytes + caption */
    var onSend: ((List<ByteArray>, String) -> Unit)? = null
    /** Called when picker is dismissed */
    var onDismiss: (() -> Unit)? = null

    private var pickerView: View? = null
    private val selectedUris = mutableListOf<Uri>()
    private val allPhotoUris = mutableListOf<Uri>()
    private var adapter: PhotoGridAdapter? = null
    private var sendBtn: TextView? = null
    private var countLabel: TextView? = null
    private var captionInput: EditText? = null

    private fun dp(px: Int): Int = (px * context.resources.displayMetrics.density).toInt()

    private fun createRoundedBg(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Show the gallery picker overlaid on the given parent FrameLayout.
     * Takes up the bottom ~60% of the screen.
     */
    fun show(parent: FrameLayout) {
        if (pickerView != null) return

        loadPhotos()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- Semi-transparent overlay tap to dismiss ----
        val overlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#80000000"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.35f
            )
            setOnClickListener { dismiss(parent) }
        }
        root.addView(overlay)

        // ---- Picker container ----
        val picker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(if (isDarkTheme) Color.parseColor("#1C1F3A") else Color.parseColor("#FAFBFF"))
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f
                )
                if (!isDarkTheme) {
                    setStroke(dp(1), Color.parseColor("#C8CDD8"))
                }
            }
            background = bg
            if (!isDarkTheme) elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.65f
            )
        }

        // ---- Handle bar ----
        val handleBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
        }
        val handle = View(context).apply {
            val bg = GradientDrawable().apply {
                setColor(if (isDarkTheme) Color.parseColor("#4A4D6A") else Color.parseColor("#A0A8B8"))
                cornerRadius = dp(3).toFloat()
            }
            background = bg
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(5)).apply {
                gravity = Gravity.CENTER
            }
        }
        handleBar.addView(handle)
        picker.addView(handleBar)

        // ---- Header: title + count ----
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        header.addView(TextView(context).apply {
            text = S.get("gallery")
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        countLabel = TextView(context).apply {
            text = ""
            setTextColor(accentColor)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            visibility = View.GONE
        }
        header.addView(countLabel!!)
        picker.addView(header)

        // ---- Photo grid ----
        val recycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(dp(4), 0, dp(4), 0)
            clipToPadding = false
        }
        adapter = PhotoGridAdapter()
        recycler.adapter = adapter
        picker.addView(recycler)

        if (allPhotoUris.isEmpty()) {
            picker.addView(TextView(context).apply {
                text = S.get("no_photos_found")
                setTextColor(subTextColor)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            })
        }

        // ---- Bottom: Caption + Send ----
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            val bg = GradientDrawable().apply {
                setColor(if (isDarkTheme) Color.parseColor("#151830") else Color.parseColor("#EEF0F6"))
            }
            background = bg
        }

        captionInput = EditText(context).apply {
            hint = S.get("add_caption")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 14f
            val inputBg = GradientDrawable().apply {
                setColor(if (isDarkTheme) Color.parseColor("#252850") else Color.parseColor("#FFFFFF"))
                cornerRadius = dp(20).toFloat()
                if (!isDarkTheme) {
                    setStroke(dp(1), Color.parseColor("#C8CDD8"))
                }
            }
            background = inputBg
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 3
        }
        bottomBar.addView(captionInput!!)

        sendBtn = TextView(context).apply {
            text = "➤"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
            }
            background = bg
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8) }
            visibility = View.GONE
            setOnClickListener {
                if (selectedUris.isNotEmpty()) {
                    showPreviewBeforeSend(parent)
                }
            }
        }
        bottomBar.addView(sendBtn!!)
        picker.addView(bottomBar)

        root.addView(picker)
        pickerView = root
        parent.addView(root)

        // Slide-up animation
        root.translationY = root.height.toFloat().coerceAtLeast(1000f)
        root.animate().translationY(0f).setDuration(250).start()
    }

    fun dismiss(parent: FrameLayout) {
        val view = pickerView ?: return
        view.animate().translationY(view.height.toFloat()).setDuration(200).withEndAction {
            parent.removeView(view)
            pickerView = null
            selectedUris.clear()
            onDismiss?.invoke()
        }.start()
    }

    fun isShowing(): Boolean = pickerView != null

    private fun showPreviewBeforeSend(parent: FrameLayout) {
        val caption = captionInput?.text?.toString()?.trim() ?: ""

        // Build fullscreen preview overlay
        val overlay = FrameLayout(context).apply {
            tag = "image_preview_overlay"
            setBackgroundColor(Color.parseColor("#E0000000"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(16), dp(40), dp(16), dp(20))
        }

        // Title
        content.addView(TextView(context).apply {
            text = S.get("preview") + " (${selectedUris.size})"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        // Image preview area - horizontal scrolling for multiple images
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isHorizontalScrollBarEnabled = false
        }
        val imageRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        for (uri in selectedUris) {
            val imgView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val imgSize = if (selectedUris.size == 1) {
                    (context.resources.displayMetrics.widthPixels - dp(48))
                } else {
                    (context.resources.displayMetrics.widthPixels * 0.7).toInt()
                }
                layoutParams = LinearLayout.LayoutParams(imgSize, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    rightMargin = if (selectedUris.size > 1) dp(8) else 0
                }
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#20FFFFFF"))
                }
                background = bg
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                    }
                }
            }
            // Load preview
            Thread {
                val bmp = loadThumbnailFull(uri)
                if (bmp != null) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        imgView.setImageBitmap(bmp)
                    }
                }
            }.start()
            imageRow.addView(imgView)
        }
        scrollView.addView(imageRow)
        content.addView(scrollView)

        // Caption display
        if (caption.isNotEmpty()) {
            content.addView(TextView(context).apply {
                text = "💬 $caption"
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(4))
            })
        }

        // Buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        // Cancel
        btnRow.addView(TextView(context).apply {
            text = S.get("cancel")
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#444444"))
            }
            background = bg
            setPadding(dp(32), dp(12), dp(32), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(16) }
            setOnClickListener {
                parent.removeView(overlay)
            }
        })
        // Send
        btnRow.addView(TextView(context).apply {
            text = S.get("send") + " ➤"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(accentColor)
            }
            background = bg
            setPadding(dp(32), dp(12), dp(32), dp(12))
            setOnClickListener {
                parent.removeView(overlay)
                val photoBytes = selectedUris.mapNotNull { uri -> loadImageBytes(uri) }
                if (photoBytes.isNotEmpty()) {
                    onSend?.invoke(photoBytes, caption)
                }
                dismiss(parent)
            }
        })
        content.addView(btnRow)
        overlay.addView(content)
        parent.addView(overlay)
    }

    private fun loadThumbnailFull(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream.close()

            val targetSize = 800
            val scale = maxOf(opts.outWidth, opts.outHeight) / targetSize
            val sampleSize = if (scale > 1) Integer.highestOneBit(scale) else 1

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeStream(inputStream2, null, opts2)
            inputStream2.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun loadPhotos() {
        allPhotoUris.clear()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (it.moveToNext() && count < 200) {
                val id = it.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                allPhotoUris.add(uri)
                count++
            }
        }
    }

    private fun loadImageBytes(uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()
            // Compress if large
            if (bytes.size > 500_000) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                bitmap.recycle()
                out.toByteArray()
            } else bytes
        } catch (e: Exception) {
            null
        }
    }

    private fun loadThumbnail(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream.close()

            val targetSize = 200
            val scale = maxOf(opts.outWidth, opts.outHeight) / targetSize
            val sampleSize = if (scale > 1) Integer.highestOneBit(scale) else 1

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bmp = BitmapFactory.decodeStream(inputStream2, null, opts2)
            inputStream2.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun updateSelection() {
        val count = selectedUris.size
        if (count > 0) {
            countLabel?.text = S.get("selected_count", count)
            countLabel?.visibility = View.VISIBLE
            sendBtn?.visibility = View.VISIBLE
        } else {
            countLabel?.visibility = View.GONE
            sendBtn?.visibility = View.GONE
        }
    }

    // ================================================================
    // RecyclerView Adapter for photo grid
    // ================================================================
    inner class PhotoGridAdapter : RecyclerView.Adapter<PhotoGridAdapter.VH>() {

        inner class VH(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
            val imageView: ImageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val checkOverlay: FrameLayout = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#40000000"))
                visibility = View.GONE
            }
            val checkBadge: TextView = TextView(context).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }
                background = bg
                val size = dp(26)
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(6)
                    rightMargin = dp(6)
                }
            }

            init {
                frame.addView(imageView)
                frame.addView(checkOverlay)
                frame.addView(checkBadge)
                checkBadge.visibility = View.GONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val size = (context.resources.displayMetrics.widthPixels - dp(12)) / 3
            val frame = FrameLayout(context).apply {
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(if (isDarkTheme) Color.parseColor("#252850") else Color.parseColor("#D8DCE6"))
                }
                background = bg
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
                    }
                }
                clipChildren = true
                layoutParams = RecyclerView.LayoutParams(size, size).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                    bottomMargin = dp(4)
                }
            }
            return VH(frame)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = allPhotoUris[position]
            val isSelected = selectedUris.contains(uri)
            val selIndex = selectedUris.indexOf(uri)

            // Load thumbnail async
            holder.imageView.setImageBitmap(null)
            holder.imageView.tag = uri.toString()
            Thread {
                val bmp = loadThumbnail(uri)
                if (bmp != null) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        if (holder.imageView.tag == uri.toString()) {
                            holder.imageView.setImageBitmap(bmp)
                        }
                    }
                }
            }.start()

            // Selection state
            holder.checkOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.checkBadge.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                holder.checkBadge.text = "${selIndex + 1}"
            }

            holder.frame.setOnClickListener {
                if (selectedUris.contains(uri)) {
                    selectedUris.remove(uri)
                } else {
                    if (selectedUris.size < 10) {
                        selectedUris.add(uri)
                    }
                }
                notifyDataSetChanged()
                updateSelection()
            }
        }

        override fun getItemCount() = allPhotoUris.size
    }
}
