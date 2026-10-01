package com.example.employeepos2

data class InventoryJob(
    val jobNumber: String,
    val customer: String,
    val city: String,
    val poNumber: String,
    val description: String,
    val notes: String?,
    val items: List<InventoryPart>
)

data class InventoryPart(
    val name: String,
    var qtyNeeded: Int,
    val qtyInStock: Int,
    val description: String?,
    var isPicked: Boolean = false
)