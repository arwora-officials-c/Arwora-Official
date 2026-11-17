package com.arwora.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.SoundEffectManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeCalculatorScreen(onBack: () -> Unit) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    // Live update
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = LocalDateTime.now()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Age Calculator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Calculate Your Age",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select your birth date to see how long you've been on Earth!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Date Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Birth Date",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            showDatePicker = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select")
                    }
                }
            }

            // Results Section
            val birthDateTime = selectedDate.atStartOfDay()
            val age = Period.between(selectedDate, currentTime.toLocalDate())
            
            // Total stats
            val totalDays = ChronoUnit.DAYS.between(birthDateTime, currentTime)
            val totalHours = ChronoUnit.HOURS.between(birthDateTime, currentTime)
            val totalMinutes = ChronoUnit.MINUTES.between(birthDateTime, currentTime)
            val totalSeconds = ChronoUnit.SECONDS.between(birthDateTime, currentTime)
            
            // Birthday Countdown
            val today = currentTime.toLocalDate()
            var nextBirthday = selectedDate.withYear(today.year)
            if (nextBirthday.isBefore(today) || (nextBirthday.isEqual(today) && currentTime.toLocalTime().isAfter(java.time.LocalTime.MIDNIGHT))) {
                nextBirthday = nextBirthday.plusYears(1)
            }
            val nextBirthdayDateTime = nextBirthday.atStartOfDay()
            val countdown = Duration.between(currentTime, nextBirthdayDateTime)
            
            val countdownDays = countdown.toDays()
            val countdownHours = countdown.toHoursPart()
            val countdownMinutes = countdown.toMinutesPart()
            val countdownSeconds = countdown.toSecondsPart()

            // Yearly progress towards next birthday
            val prevBirthday = nextBirthday.minusYears(1).atStartOfDay()
            val totalCycleSecs = Duration.between(prevBirthday, nextBirthdayDateTime).seconds.coerceAtLeast(1)
            val elapsedSecs = Duration.between(prevBirthday, currentTime).seconds
            val progress = (elapsedSecs.toFloat() / totalCycleSecs.toFloat()).coerceIn(0f, 1f)

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Your Live Age", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                        AgeComponentCard("Years", age.years.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                        AgeComponentCard("Months", age.months.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                        AgeComponentCard("Days", age.days.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                    }

                    // REAL-TIME METRICS CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Exact Total Time Lived (Real-Time)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            TimeMetricRow("Total Days", totalDays.toString())
                            TimeMetricRow("Total Hours", totalHours.toString())
                            TimeMetricRow("Total Minutes", totalMinutes.toString())
                            TimeMetricRow("Total Seconds", totalSeconds.toString(), isHighlight = true)
                        }
                    }

                    // CELEBRATORY BIRTHDAY COUNTDOWN CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Upcoming Birthday Countdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.Cake,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                CountdownUnitCard("Days", countdownDays.toString(), MaterialTheme.colorScheme.primary)
                                CountdownUnitCard("Hours", countdownHours.toString(), MaterialTheme.colorScheme.secondary)
                                CountdownUnitCard("Mins", countdownMinutes.toString(), MaterialTheme.colorScheme.tertiary)
                                CountdownUnitCard("Secs", countdownSeconds.toString(), MaterialTheme.colorScheme.primary)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            ExpressiveWavyLinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                height = 10.dp
                            )
                            
                            Text(
                                text = "Year Progress to Birthday: ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AgeComponentCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TimeMetricRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isHighlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CountdownUnitCard(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
            modifier = Modifier.width(64.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.padStart(2, '0'),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.8f)
        )
    }
}

