package com.mengchuangbox.input

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.net.URL
import java.util.concurrent.Executors

class SkinAdapter(
    private var list: List<Skin>,
    private val onSkinClick: (Skin) -> Unit
) : RecyclerView.Adapter<SkinAdapter.Holder>() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadExecutor = Executors.newFixedThreadPool(2)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_skin, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val skin = list[position]
        holder.name.text = skin.name
        holder.preview.setImageDrawable(null)
        holder.preview.tag = skin.id
        when {
            !skin.previewUrl.isNullOrBlank() -> {
                loadExecutor.execute {
                    var bm: android.graphics.Bitmap? = null
                    try {
                        URL(skin.previewUrl).openStream().use { stream ->
                            bm = BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) { }
                    val bitmap = bm
                    val id = skin.id
                    mainHandler.post {
                        if (holder.preview.tag == id && bitmap != null) {
                            holder.preview.setImageBitmap(bitmap)
                        }
                    }
                }
            }
            !skin.previewPath.isNullOrBlank() -> {
                val f = File(skin.previewPath)
                if (f.exists()) {
                    BitmapFactory.decodeFile(skin.previewPath)?.let { holder.preview.setImageBitmap(it) }
                }
            }
        }
        holder.itemView.setOnClickListener { onSkinClick(skin) }
    }

    override fun getItemCount(): Int = list.size

    fun setItems(newList: List<Skin>) {
        list = newList
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val preview: ImageView = v.findViewById(R.id.skin_preview)
        val name: TextView = v.findViewById(R.id.skin_name)
    }
}
