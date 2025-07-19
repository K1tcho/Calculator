

// MainActivity.kt
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

val Context.dataStore by preferencesDataStore(name = "settings")

@Composable
fun rememberAppSettings(): State<AppSettings> {
    val context = LocalContext.current
    val darkTheme = runBlocking {
        context.dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("dark_theme")] ?: true
        }.first()
    }
    val themeColor = runBlocking {
        context.dataStore.data.map { prefs ->
            prefs[intPreferencesKey("theme_color")] ?: 0
        }.first()
    }
    return remember { mutableStateOf(AppSettings(darkTheme, themeColor)) }
}

data class AppSettings(
    val darkTheme: Boolean,
    val themeColor: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettings = rememberAppSettings()
            val darkTheme = appSettings.value.darkTheme
            val themeColor = appSettings.value.themeColor

            QuantumCalcTheme(
                darkTheme = darkTheme,
                themeColor = themeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(appSettings)
                }
            }
        }
    }
}

@Composable
fun QuantumCalcTheme(
    darkTheme: Boolean,
    themeColor: Int,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeColor) {
        0 -> if (darkTheme) darkColorScheme(
            primary = Color(0xFF6C4DF6),
            secondary = Color(0xFF42A5F5),
            tertiary = Color(0xFF26C6DA),
            background = Color(0xFF121212)
        ) else lightColorScheme(
            primary = Color(0xFF6C4DF6),
            secondary = Color(0xFF42A5F5),
            tertiary = Color(0xFF26C6DA),
            background = Color(0xFFF5F5F5)
        )
        1 -> if (darkTheme) darkColorScheme(
            primary = Color(0xFFF06292),
            secondary = Color(0xFFBA68C8),
            tertiary = Color(0xFF4FC3F7),
            background = Color(0xFF121212)
        ) else lightColorScheme(
            primary = Color(0xFFF06292),
            secondary = Color(0xFFBA68C8),
            tertiary = Color(0xFF4FC3F7),
            background = Color(0xFFFFF9F9)
        )
        else -> if (darkTheme) darkColorScheme(
            primary = Color(0xFF4DB6AC),
            secondary = Color(0xFFAED581),
            tertiary = Color(0xFFFFD54F),
            background = Color(0xFF121212)
        ) else lightColorScheme(
            primary = Color(0xFF4DB6AC),
            secondary = Color(0xFFAED581),
            tertiary = Color(0xFFFFD54F),
            background = Color(0xFFF5FFF9)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            displayLarge = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp
            ),
            bodyLarge = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp
            )
        ),
        content = content
    )
}

@Composable
fun AppNavigation(appSettings: State<AppSettings>) {
    val navController = rememberNavController()
    val viewModel: CalculatorViewModel = viewModel()

    NavHost(navController = navController, startDestination = "calculator") {
        composable("calculator") {
            CalculatorScreen(navController, viewModel)
        }
        composable("converter") {
            ConverterScreen(navController, viewModel)
        }
        composable("history") {
            HistoryScreen(navController, viewModel)
        }
        composable("settings") {
            SettingsScreen(navController, appSettings)
        }
    }
}

// CALCULATOR VIEWMODEL
class CalculatorViewModel : androidx.lifecycle.ViewModel() {
    var displayValue by mutableStateOf("0")
    var storedValue by mutableStateOf(0.0)
    var currentOperation by mutableStateOf<Char?>(null)
    var lastButtonWasOperation by mutableStateOf(false)
    var isScientificMode by mutableStateOf(false)
    val calculationHistory = mutableStateListOf<String>()

    fun appendNumber(number: String) {
        if (lastButtonWasOperation) {
            displayValue = "0"
            lastButtonWasOperation = false
        }
        displayValue = if (displayValue == "0") number else displayValue + number
    }

    fun setOperation(operation: Char) {
        if (currentOperation == null) {
            storedValue = displayValue.toDoubleOrNull() ?: 0.0
        } else {
            performOperation(displayValue.toDoubleOrNull() ?: 0.0, currentOperation!!)
        }
        currentOperation = operation
    }

    fun performOperation(value: Double, operation: Char) {
        when (operation) {
            '+' -> storedValue += value
            '-' -> storedValue -= value
            '*' -> storedValue *= value
            '/' -> storedValue /= value
            '%' -> storedValue %= value
        }
    }

    fun calculateResult() {
        if (currentOperation != null) {
            performOperation(displayValue.toDoubleOrNull() ?: 0.0, currentOperation!!)
            displayValue = storedValue.toString()
            calculationHistory.add("$storedValue $currentOperation $displayValue = $displayValue")
            currentOperation = null
        }
    }

    fun clear() {
        displayValue = "0"
        storedValue = 0.0
        currentOperation = null
        lastButtonWasOperation = false
    }

    fun backspace() {
        if (displayValue.length > 1) {
            displayValue = displayValue.dropLast(1)
        } else {
            displayValue = "0"
        }
    }

    fun scientificOperation(operation: String) {
        val value = displayValue.toDoubleOrNull() ?: 0.0
        val result = when (operation) {
            "sin" -> sin(value * (PI / 180))
            "cos" -> cos(value * (PI / 180))
            "tan" -> tan(value * (PI / 180))
            "log" -> log10(value)
            "ln" -> ln(value)
            "sqrt" -> sqrt(value)
            "pow" -> value * value
            "pi" -> PI
            "e" -> kotlin.math.exp(1.0)
            else -> value
        }
        displayValue = result.toString()
        calculationHistory.add("$operation($value) = $result")
    }
}

// CALCULATOR SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(navController: NavHostController, viewModel: CalculatorViewModel) {
    val displayValue by viewModel.displayValue
    val isScientificMode by viewModel.isScientificMode

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Top navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }

                Text(
                    text = "Quantum Calc",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )

                IconButton(
                    onClick = { navController.navigate("converter") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Converters")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scientific mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scientific Mode",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isScientificMode,
                    onCheckedChange = { viewModel.isScientificMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calculator buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scientific row (only in scientific mode)
                if (isScientificMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SciButton("sin", buttonColors) { viewModel.scientificOperation("sin") }
                        SciButton("cos", buttonColors) { viewModel.scientificOperation("cos") }
                        SciButton("tan", buttonColors) { viewModel.scientificOperation("tan") }
                        SciButton("log", buttonColors) { viewModel.scientificOperation("log") }
                        SciButton("ln", buttonColors) { viewModel.scientificOperation("ln") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SciButton("π", buttonColors) { viewModel.scientificOperation("pi") }
                        SciButton("e", buttonColors) { viewModel.scientificOperation("e") }
                        SciButton("x²", buttonColors) { viewModel.scientificOperation("pow") }
                        SciButton("√", buttonColors) { viewModel.scientificOperation("sqrt") }
                        SciButton("(", buttonColors) { viewModel.appendNumber("(") }
                    }
                }

                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcButton("C", buttonColors) { viewModel.clear() }
                    CalcButton("⌫", buttonColors) { viewModel.backspace() }
                    CalcButton("%", buttonColors) {
                        viewModel.setOperation('%')
                        viewModel.lastButtonWasOperation = true
                    }
                    CalcButton("÷", buttonColors) {
                        viewModel.setOperation('/')
                        viewModel.lastButtonWasOperation = true
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcButton("7", buttonColors) { viewModel.appendNumber("7") }
                    CalcButton("8", buttonColors) { viewModel.appendNumber("8") }
                    CalcButton("9", buttonColors) { viewModel.appendNumber("9") }
                    CalcButton("×", buttonColors) {
                        viewModel.setOperation('*')
                        viewModel.lastButtonWasOperation = true
                    }
                }

                // Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcButton("4", buttonColors) { viewModel.appendNumber("4") }
                    CalcButton("5", buttonColors) { viewModel.appendNumber("5") }
                    CalcButton("6", buttonColors) { viewModel.appendNumber("6") }
                    CalcButton("-", buttonColors) {
                        viewModel.setOperation('-')
                        viewModel.lastButtonWasOperation = true
                    }
                }

                // Row 4
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcButton("1", buttonColors) { viewModel.appendNumber("1") }
                    CalcButton("2", buttonColors) { viewModel.appendNumber("2") }
                    CalcButton("3", buttonColors) { viewModel.appendNumber("3") }
                    CalcButton("+", buttonColors) {
                        viewModel.setOperation('+')
                        viewModel.lastButtonWasOperation = true
                    }
                }

                // Row 5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CalcButton("±", buttonColors) {
                        viewModel.displayValue = (viewModel.displayValue.toDoubleOrNull()?.times(-1)?.toString() ?: "0")
                    }
                    CalcButton("0", buttonColors) { viewModel.appendNumber("0") }
                    CalcButton(".", buttonColors) {
                        if (!viewModel.displayValue.contains(".")) {
                            viewModel.appendNumber(".")
                        }
                    }
                    CalcButton("=", ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )) {
                        viewModel.calculateResult()
                    }
                }
            }
        }

        // History button
        FloatingActionButton(
            onClick = { navController.navigate("history") },
            containerColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.History, contentDescription = "History")
        }
    }
}

@Composable
fun CalcButton(
    text: String,
    colors: ButtonColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = colors,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .shadow(8.dp, shape = CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                ),
                shape = CircleShape
            ),
        shape = CircleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SciButton(
    text: String,
    colors: ButtonColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = colors,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .shadow(4.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// CONVERTER SCREEN
@Composable
fun ConverterScreen(navController: NavHostController, viewModel: CalculatorViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Length", "Weight", "Temperature", "Currency", "Area", "Volume", "Speed")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Back button
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Universal Converter",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tab selector
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Converter content
        when (selectedTab) {
            0 -> LengthConverter()
            1 -> WeightConverter()
            2 -> TemperatureConverter()
            3 -> CurrencyConverter()
            4 -> AreaConverter()
            5 -> VolumeConverter()
            6 -> SpeedConverter()
        }
    }
}

@Composable
fun LengthConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Meters") }
    var outputUnit by remember { mutableStateOf("Feet") }
    val units = listOf("Meters", "Feet", "Inches", "Centimeters", "Yards", "Miles", "Kilometers")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val meters = when (fromUnit) {
                "Meters" -> value
                "Feet" -> value * 0.3048
                "Inches" -> value * 0.0254
                "Centimeters" -> value * 0.01
                "Yards" -> value * 0.9144
                "Miles" -> value * 1609.34
                "Kilometers" -> value * 1000
                else -> value
            }

            when (toUnit) {
                "Meters" -> meters
                "Feet" -> meters / 0.3048
                "Inches" -> meters / 0.0254
                "Centimeters" -> meters * 100
                "Yards" -> meters / 0.9144
                "Miles" -> meters / 1609.34
                "Kilometers" -> meters / 1000
                else -> meters
            }
        }
    )
}

@Composable
fun WeightConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Kilograms") }
    var outputUnit by remember { mutableStateOf("Pounds") }
    val units = listOf("Kilograms", "Pounds", "Ounces", "Grams", "Stones", "Tons")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val kg = when (fromUnit) {
                "Kilograms" -> value
                "Pounds" -> value * 0.453592
                "Ounces" -> value * 0.0283495
                "Grams" -> value * 0.001
                "Stones" -> value * 6.35029
                "Tons" -> value * 1000
                else -> value
            }

            when (toUnit) {
                "Kilograms" -> kg
                "Pounds" -> kg / 0.453592
                "Ounces" -> kg / 0.0283495
                "Grams" -> kg * 1000
                "Stones" -> kg / 6.35029
                "Tons" -> kg / 1000
                else -> kg
            }
        }
    )
}

@Composable
fun TemperatureConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Celsius") }
    var outputUnit by remember { mutableStateOf("Fahrenheit") }
    val units = listOf("Celsius", "Fahrenheit", "Kelvin")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val celsius = when (fromUnit) {
                "Celsius" -> value
                "Fahrenheit" -> (value - 32) * 5 / 9
                "Kelvin" -> value - 273.15
                else -> value
            }

            when (toUnit) {
                "Celsius" -> celsius
                "Fahrenheit" -> (celsius * 9 / 5) + 32
                "Kelvin" -> celsius + 273.15
                else -> celsius
            }
        }
    )
}

// Currency API Service
interface CurrencyApiService {
    @GET("latest")
    suspend fun getExchangeRates(
        @Query("base") base: String
    ): CurrencyResponse
}

data class CurrencyResponse(
    @SerializedName("base") val base: String,
    @SerializedName("rates") val rates: Map<String, Double>
)

object CurrencyApi {
    private const val BASE_URL = "https://api.exchangerate-api.com/v4/"

    val retrofitService: CurrencyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CurrencyApiService::class.java)
    }
}

@Composable
fun CurrencyConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("USD") }
    var outputUnit by remember { mutableStateOf("EUR") }
    var exchangeRates by remember { mutableStateOf(emptyMap<String, Double>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val units = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR")

    LaunchedEffect(Unit) {
        try {
            val response = CurrencyApi.retrofitService.getExchangeRates("USD")
            exchangeRates = response.rates
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }
    } else {
        ConverterTemplate(
            inputValue = inputValue,
            inputUnit = inputUnit,
            outputUnit = outputUnit,
            units = units,
            onInputChange = { inputValue = it },
            onInputUnitChange = { inputUnit = it },
            onOutputUnitChange = { outputUnit = it },
            convert = { value, fromUnit, toUnit ->
                val usdValue = if (fromUnit == "USD") value else value / (exchangeRates[fromUnit] ?: 1.0)
                usdValue * (exchangeRates[toUnit] ?: 1.0)
            }
        )
    }
}

@Composable
fun AreaConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Square Meters") }
    var outputUnit by remember { mutableStateOf("Square Feet") }
    val units = listOf("Square Meters", "Square Feet", "Acres", "Hectares", "Square Miles")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val sqMeters = when (fromUnit) {
                "Square Meters" -> value
                "Square Feet" -> value * 0.092903
                "Acres" -> value * 4046.86
                "Hectares" -> value * 10000
                "Square Miles" -> value * 2589988.11
                else -> value
            }

            when (toUnit) {
                "Square Meters" -> sqMeters
                "Square Feet" -> sqMeters / 0.092903
                "Acres" -> sqMeters / 4046.86
                "Hectares" -> sqMeters / 10000
                "Square Miles" -> sqMeters / 2589988.11
                else -> sqMeters
            }
        }
    )
}

@Composable
fun VolumeConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Liters") }
    var outputUnit by remember { mutableStateOf("Gallons") }
    val units = listOf("Liters", "Gallons", "Cubic Meters", "Cubic Feet", "Milliliters")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val liters = when (fromUnit) {
                "Liters" -> value
                "Gallons" -> value * 3.78541
                "Cubic Meters" -> value * 1000
                "Cubic Feet" -> value * 28.3168
                "Milliliters" -> value * 0.001
                else -> value
            }

            when (toUnit) {
                "Liters" -> liters
                "Gallons" -> liters / 3.78541
                "Cubic Meters" -> liters / 1000
                "Cubic Feet" -> liters / 28.3168
                "Milliliters" -> liters * 1000
                else -> liters
            }
        }
    )
}

@Composable
fun SpeedConverter() {
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("km/h") }
    var outputUnit by remember { mutableStateOf("mph") }
    val units = listOf("km/h", "mph", "m/s", "knots", "ft/s")

    ConverterTemplate(
        inputValue = inputValue,
        inputUnit = inputUnit,
        outputUnit = outputUnit,
        units = units,
        onInputChange = { inputValue = it },
        onInputUnitChange = { inputUnit = it },
        onOutputUnitChange = { outputUnit = it },
        convert = { value, fromUnit, toUnit ->
            val kmh = when (fromUnit) {
                "km/h" -> value
                "mph" -> value * 1.60934
                "m/s" -> value * 3.6
                "knots" -> value * 1.852
                "ft/s" -> value * 1.09728
                else -> value
            }

            when (toUnit) {
                "km/h" -> kmh
                "mph" -> kmh / 1.60934
                "m/s" -> kmh / 3.6
                "knots" -> kmh / 1.852
                "ft/s" -> kmh / 1.09728
                else -> kmh
            }
        }
    )
}

@Composable
fun ConverterTemplate(
    inputValue: String,
    inputUnit: String,
    outputUnit: String,
    units: List<String>,
    onInputChange: (String) -> Unit,
    onInputUnitChange: (String) -> Unit,
    onOutputUnitChange: (String) -> Unit,
    convert: (Double, String, String) -> Double
) {
    val outputValue = try {
        if (inputValue.isNotEmpty()) {
            convert(inputValue.toDouble(), inputUnit, outputUnit).toString().take(10)
        } else {
            ""
        }
    } catch (e: Exception) {
        "Invalid input"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Input section
        ConverterSection(
            value = inputValue,
            unit = inputUnit,
            units = units,
            onValueChange = onInputChange,
            onUnitChange = onInputUnitChange,
            label = "Input"
        )

        // Swap button
        IconButton(
            onClick = {
                val temp = inputUnit
                onInputUnitChange(outputUnit)
                onOutputUnitChange(temp)
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = "Swap units",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        // Output section
        ConverterSection(
            value = outputValue,
            unit = outputUnit,
            units = units,
            onValueChange = { /* Read-only */ },
            onUnitChange = onOutputUnitChange,
            label = "Output",
            isEditable = false
        )
    }
}

@Composable
fun ConverterSection(
    value: String,
    unit: String,
    units: List<String>,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    label: String,
    isEditable: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Value input
            OutlinedTextField(
                value = value,
                onValueChange = { if (isEditable) onValueChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                ),
                singleLine = true,
                readOnly = !isEditable,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )

            // Unit selector
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { expanded = true }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    units.forEach { unitItem ->
                        DropdownMenuItem(
                            text = { Text(unitItem) },
                            onClick = {
                                onUnitChange(unitItem)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// HISTORY SCREEN
@Composable
fun HistoryScreen(navController: NavHostController, viewModel: CalculatorViewModel) {
    val history by viewModel.calculationHistory

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Back button
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Calculation History",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
        )

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No history yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history.reversed()) { item ->
                    HistoryItem(item)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(calculation: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = "History",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                text = calculation,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// SETTINGS SCREEN
@Composable
fun SettingsScreen(navController: NavHostController, appSettings: State<AppSettings>) {
    val context = LocalContext.current
    val darkTheme = appSettings.value.darkTheme
    val themeColor = appSettings.value.themeColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Back button
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "App Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 24.dp)
        )

        // Theme settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Dark mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Theme",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = {
                            runBlocking {
                                context.dataStore.edit { settings ->
                                    settings[booleanPreferencesKey("dark_theme")] = it
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color theme selector
                Text(
                    text = "Theme Color",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ThemeColorOption(0, "Purple", themeColor == 0) {
                        runBlocking {
                            context.dataStore.edit { settings ->
                                settings[intPreferencesKey("theme_color")] = 0
                            }
                        }
                    }

                    ThemeColorOption(1, "Pink", themeColor == 1) {
                        runBlocking {
                            context.dataStore.edit { settings ->
                                settings[intPreferencesKey("theme_color")] = 1
                            }
                        }
                    }

                    ThemeColorOption(2, "Green", themeColor == 2) {
                        runBlocking {
                            context.dataStore.edit { settings ->
                                settings[intPreferencesKey("theme_color")] = 2
                            }
                        }
                    }
                }
            }
        }

        // Other settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Quantum Calculator v2.0",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "The ultimate calculator with scientific functions, unit conversion, and real-time currency rates",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ThemeColorOption(index: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val color = when (index) {
        0 -> if (MaterialTheme.colorScheme.isDark) Color(0xFF6C4DF6) else Color(0xFF6C4DF6)
        1 -> if (MaterialTheme.colorScheme.isDark) Color(0xFFF06292) else Color(0xFFF06292)
        else -> if (MaterialTheme.colorScheme.isDark) Color(0xFF4DB6AC) else Color(0xFF4DB6AC)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}