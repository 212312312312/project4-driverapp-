package com.taxiapp.driver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.taxiapp.driver.network.CarDto // <-- Используем CarDto

class CarAdapter(
    private val onCarClick: (CarDto) -> Unit // <-- CarDto
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {

    private var cars: List<CarDto> = emptyList() // <-- CarDto
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

        fun bind(car: CarDto) { // <-- CarDto
            tvModel.text = "${car.make} ${car.model}"
            tvPlate.text = car.plateNumber

            // Статус и цвет
            when (car.status) {
                "ACTIVE" -> {
                    tvStatus.text = "АКТИВНЕ"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                "PENDING" -> {
                    tvStatus.text = "НА ПЕРЕВІРЦІ"
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                }
                "REJECTED" -> {
                    tvStatus.text = "ВІДХИЛЕНО"
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                }
                else -> {
                    tvStatus.text = car.status ?: "Невідомо"
                    tvStatus.setTextColor(Color.GRAY)
                }
            }

            // Фото
            if (!car.photoUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(car.photoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_car)
                    .into(imgPhoto)
            } else {
                imgPhoto.setImageResource(R.drawable.ic_car)
            }

            // Активна ли машина?
            val isActive = (car.id == activeCarId)
            if (isActive) {
                imgCheck.visibility = View.VISIBLE
                tvAction.text = "Поточне авто"
                tvAction.setTextColor(Color.parseColor("#4CAF50"))
                itemView.setOnClickListener(null)
            } else {
                imgCheck.visibility = View.GONE
                if (car.status == "ACTIVE") {
                    tvAction.text = "Натисніть, щоб обрати"
                    tvAction.setTextColor(Color.parseColor("#2196F3"))
                    itemView.setOnClickListener { onCarClick(car) }
                } else {
                    tvAction.text = "Недоступно"
                    tvAction.setTextColor(Color.GRAY)
                    itemView.setOnClickListener(null)
                }
            }
        }
    }
}