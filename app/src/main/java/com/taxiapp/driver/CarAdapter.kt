package com.taxiapp.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.CarDto
import com.taxiapp.driver.utils.toFullImageUrl

class CarAdapter(
    private val onCarClick: (CarDto) -> Unit
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    private var cars: List<CarDto> = emptyList()
    private var activeCarId: Long? = null

    fun submitList(newList: List<CarDto>, activeId: Long?) {
        cars = newList
        activeCarId = activeId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_car, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        holder.bind(cars[position])
    }

    override fun getItemCount(): Int = cars.size

    inner class CarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvModel: TextView = itemView.findViewById(R.id.tv_item_model)
        private val tvPlate: TextView = itemView.findViewById(R.id.tv_item_plate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_item_status)
        private val imgPhoto: ImageView = itemView.findViewById(R.id.img_car_item)
        private val imgCheck: ImageView = itemView.findViewById(R.id.img_check_active)
        private val tvAction: TextView = itemView.findViewById(R.id.tv_click_to_activate)

        fun bind(car: CarDto) {
            val context = itemView.context
            tvModel.text = "${car.make} ${car.model}"
            tvPlate.text = car.plateNumber

            val textPrimaryColor = ContextCompat.getColor(context, R.color.driver_text_primary)
            val textSecondaryColor = ContextCompat.getColor(context, R.color.driver_text_secondary)
            val neonTealColor = ContextCompat.getColor(context, R.color.driver_neon_teal)
            val errorRedColor = ContextCompat.getColor(context, R.color.driver_error_red)

            when (car.status) {
                "ACTIVE" -> {
                    tvStatus.text = "АКТИВНЕ"
                    tvStatus.setTextColor(neonTealColor)
                }
                "PENDING" -> {
                    tvStatus.text = "НА ПЕРЕВІРЦІ"
                    tvStatus.setTextColor(Color.parseColor("#FFD600"))
                }
                "REJECTED" -> {
                    tvStatus.text = "ВІДХИЛЕНО"
                    tvStatus.setTextColor(errorRedColor)
                }
                else -> {
                    tvStatus.text = car.status ?: "Невідомо"
                    tvStatus.setTextColor(textSecondaryColor)
                }
            }

            val fullPhotoUrl = toFullImageUrl(car.photoUrl)
            if (!fullPhotoUrl.isNullOrEmpty()) {
                imgPhoto.clearColorFilter()
                Glide.with(context)
                    .load(fullPhotoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_car)
                    .error(R.drawable.ic_car)
                    .into(imgPhoto)
            } else {
                imgPhoto.setImageResource(R.drawable.ic_car)
                imgPhoto.setColorFilter(textSecondaryColor)
            }

            val isActive = (car.id == activeCarId)
            if (isActive) {
                imgCheck.visibility = View.VISIBLE
                tvAction.text = "Поточне авто"
                tvAction.setTextColor(textPrimaryColor)
                itemView.setOnClickListener(null)
            } else {
                imgCheck.visibility = View.GONE
                if (car.status == "ACTIVE") {
                    tvAction.text = "Натисніть, щоб обрати"
                    tvAction.setTextColor(neonTealColor)
                    itemView.setOnClickListener { onCarClick(car) }
                } else {
                    tvAction.text = "Недоступно"
                    tvAction.setTextColor(textSecondaryColor)
                    itemView.setOnClickListener(null)
                }
            }
        }
    }
}