package com.arwora.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.utils.SoundEffectManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeBillGeneratorScreen(
    navController: NavController,
    initialPlatform: String = "bKash Bill"
) {
    val context = LocalContext.current
    var selectedPlatform by remember { mutableStateOf(initialPlatform) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Edit, 1 = Preview

    // Bill / Receipt Input State
    var billerName by remember { mutableStateOf("DESCO Electricity Bill") }
    var accountNumber by remember { mutableStateOf("01712345678") }
    var amountText by remember { mutableStateOf("৳ 1,850.00") }
    var trxId by remember { mutableStateOf("BK98421054") }
    var feeText by remember { mutableStateOf("৳ 0.00") }
    var referenceText by remember { mutableStateOf("Home Utility") }
    var timestampText by remember {
        mutableStateOf(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
    }
    var billStatus by remember { mutableStateOf("Successful") }

    val platforms = listOf("bKash Bill", "Nagad Bill", "Mobile Recharge", "Bank Receipt")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$selectedPlatform Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = if (selectedTab == 0) 1 else 0
                    }) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = "Toggle Preview"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 0
                    },
                    text = { Text("Edit Bill Data", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 1
                    },
                    text = { Text("Live Receipt", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (selectedTab == 0) {
                // EDIT BILL FORM
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Template Selector
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Select Receipt Template", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    platforms.forEach { plat ->
                                        FilterChip(
                                            selected = selectedPlatform == plat,
                                            onClick = {
                                                selectedPlatform = plat
                                                SoundEffectManager.playClick()
                                            },
                                            label = { Text(plat, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Form Fields
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Transaction Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                                OutlinedTextField(
                                    value = billerName,
                                    onValueChange = { billerName = it },
                                    label = { Text("Recipient / Provider Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = accountNumber,
                                    onValueChange = { accountNumber = it },
                                    label = { Text("Account / Phone Number") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = amountText,
                                        onValueChange = { amountText = it },
                                        label = { Text("Amount") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = feeText,
                                        onValueChange = { feeText = it },
                                        label = { Text("Fee") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = trxId,
                                        onValueChange = { trxId = it },
                                        label = { Text("Transaction ID (TrxID)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            trxId = "TXN" + (10000000..99999999).random().toString()
                                            SoundEffectManager.playClick()
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Randomize TrxID")
                                    }
                                }

                                OutlinedTextField(
                                    value = referenceText,
                                    onValueChange = { referenceText = it },
                                    label = { Text("Reference / Note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = timestampText,
                                    onValueChange = { timestampText = it },
                                    label = { Text("Timestamp") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    item {
                        ArworaPrimaryPillButton(
                            text = "Generate & Preview Receipt",
                            onClick = {
                                selectedTab = 1
                                SoundEffectManager.playClick()
                            }
                        )
                    }
                }
            } else {
                // LIVE RECEIPT PREVIEW
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    RenderFakeBillReceipt(
                        platform = selectedPlatform,
                        billerName = billerName,
                        accountNumber = accountNumber,
                        amountText = amountText,
                        feeText = feeText,
                        trxId = trxId,
                        referenceText = referenceText,
                        timestampText = timestampText,
                        billStatus = billStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderFakeBillReceipt(
    platform: String,
    billerName: String,
    accountNumber: String,
    amountText: String,
    feeText: String,
    trxId: String,
    referenceText: String,
    timestampText: String,
    billStatus: String
) {
    val context = LocalContext.current
    val headerColor = when (platform) {
        "bKash Bill" -> Color(0xFFE2136E) // bKash Pink
        "Nagad Bill" -> Color(0xFFD12027) // Nagad Orange/Red
        "Mobile Recharge" -> Color(0xFF00A859) // Green
        else -> Color(0xFF1E3A8A) // Bank Blue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Receipt Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = headerColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$platform Successful",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Amount Display
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Paid Amount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = headerColor
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // Details Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ReceiptRow(label = "Recipient / Biller", value = billerName)
                ReceiptRow(label = "Account / Number", value = accountNumber)
                ReceiptRow(label = "Transaction ID", value = trxId, isBold = true)
                ReceiptRow(label = "Charge / Fee", value = feeText)
                ReceiptRow(label = "Reference", value = referenceText)
                ReceiptRow(label = "Status", value = billStatus, valueColor = Color(0xFF00A859))
            }

            // Barcode Graphic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    val barWidth = size.width / 40f
                    for (i in 0 until 40) {
                        if (i % 2 == 0 || i % 5 == 0) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(i * barWidth, 0f),
                                size = Size(barWidth * 0.7f, size.height)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Receipt generated for demo purposes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Save / Copy Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        Toast.makeText(context, "Receipt $trxId saved successfully", Toast.LENGTH_SHORT).show()
                        SoundEffectManager.playClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Receipt", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}
