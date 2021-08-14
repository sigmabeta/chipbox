package net.sigmabeta.chipbox.repository

sealed class Data<out DataType> {
    data class Failed<DataType>(
        val message: String
    ) : Data<DataType>()

    object Loading : Data<Nothing>()

    object Empty : Data<Nothing>()

    data class Succeeded<DataType>(
        val data: DataType
    ) : Data<DataType>()
}