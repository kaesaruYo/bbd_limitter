package com.example.bbdlimitter

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bbdlimitter.data.LocationEntity
import com.example.bbdlimitter.data.ProductEntity
import com.example.bbdlimitter.repository.InventoryRepository
import com.example.bbdlimitter.worker.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = MainViewModel.factory(this)
            )
            MainScreen(vm)
        }
        scheduleReminderWorker()
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "expiry_reminder_daily",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

data class MainUiState(
    val products: List<ProductEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList()
)

class MainViewModel(private val repository: InventoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeProducts(), repository.observeLocations()) { products, locations ->
                MainUiState(products, locations)
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            if (_state.value.locations.isEmpty()) repository.addLocation("冷蔵庫")
        }
    }

    fun addLocation(name: String) {
        viewModelScope.launch { repository.addLocation(name) }
    }

    fun addProduct(
        name: String,
        janCode: String,
        quantity: Int,
        locationId: Long,
        expiryDate: LocalDate,
        isBestBefore: Boolean,
        remindDays: Int,
        isProduce: Boolean,
        shelfLifeDays: Int?
    ) {
        viewModelScope.launch {
            repository.addProduct(
                name,
                janCode,
                quantity,
                locationId,
                expiryDate,
                isBestBefore,
                remindDays,
                isProduce,
                shelfLifeDays
            )
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            val payload = repository.exportJson()
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(payload.toByteArray())
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
            repository.importJson(raw)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(InventoryRepository(context.applicationContext)) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var jan by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var expiry by remember { mutableStateOf(LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_DATE)) }
    var remindBefore by remember { mutableStateOf("3") }
    var isBestBefore by remember { mutableStateOf(true) }
    var isProduce by remember { mutableStateOf(false) }
    var shelfLifeDays by remember { mutableStateOf("5") }
    var locationName by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportData(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importData(context, it) }
    }

    if (Build.VERSION.SDK_INT >= 33) {
        val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("賞味/消費期限チェック") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("商品登録", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("商品名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = jan, onValueChange = { jan = it }, label = { Text("JANコード（任意）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("個数") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("期限 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remindBefore, onValueChange = { remindBefore = it }, label = { Text("何日前に通知") }, modifier = Modifier.fillMaxWidth())
                Row {
                    Checkbox(checked = isBestBefore, onCheckedChange = { isBestBefore = it })
                    Text("賞味期限（OFFで消費期限）", modifier = Modifier.padding(top = 12.dp))
                }
                Row {
                    Checkbox(checked = isProduce, onCheckedChange = { isProduce = it })
                    Text("野菜・果物（保存目安を使う）", modifier = Modifier.padding(top = 12.dp))
                }
                if (isProduce) {
                    OutlinedTextField(value = shelfLifeDays, onValueChange = { shelfLifeDays = it }, label = { Text("保存目安日数") }, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = {
                    val targetExpiry = if (isProduce) {
                        LocalDate.now().plusDays(shelfLifeDays.toLongOrNull() ?: 5L)
                    } else {
                        LocalDate.parse(expiry)
                    }
                    val defaultLocation = state.locations.firstOrNull()?.id ?: 1L
                    vm.addProduct(
                        name = name,
                        janCode = jan,
                        quantity = quantity.toIntOrNull() ?: 1,
                        locationId = defaultLocation,
                        expiryDate = targetExpiry,
                        isBestBefore = isBestBefore,
                        remindDays = remindBefore.toIntOrNull() ?: 3,
                        isProduce = isProduce,
                        shelfLifeDays = shelfLifeDays.toIntOrNull()
                    )
                }) { Text("追加") }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("保管場所", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = locationName, onValueChange = { locationName = it }, label = { Text("保管場所名") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (locationName.isNotBlank()) vm.addLocation(locationName) }) { Text("保管場所を追加") }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("bbd_limitter_backup.json") }) { Text("エクスポート") }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("インポート") }
                }
            }

            item {
                Text("登録済み商品", style = MaterialTheme.typography.titleMedium)
            }

            items(state.products) { product ->
                val location = state.locations.firstOrNull { it.id == product.locationId }?.name ?: "不明"
                val type = if (product.isBestBefore) "賞味" else "消費"
                Column {
                    Text("${product.name} x${product.quantity}")
                    Text("$type 期限: ${LocalDate.ofEpochDay(product.expiryDateEpochDay)} / 場所: $location")
                    product.photoUrl?.let { Text("画像URL: $it") }
                }
            }
        }
    }
}
