package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.db.Contact
import com.example.db.Message
import com.example.db.Transaction
import com.example.ui.CallState
import com.example.ui.ContactViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

enum class DashboardTab {
    CHATS,
    WALLET
}

@Composable
fun MainAppScreen(viewModel: ContactViewModel = viewModel()) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val selectedContactId by viewModel.selectedContactId.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAITyping by viewModel.isAITyping.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val apiNotification by viewModel.notificationMessage.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(DashboardTab.CHATS) }
    var showCreatePersonaSheet by remember { mutableStateOf(false) }

    val authContext = androidx.compose.ui.platform.LocalContext.current
    var pendingCallContact by remember { mutableStateOf<Contact?>(null) }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            val contact = pendingCallContact
            if (contact != null) {
                pendingCallContact = null
                viewModel.startCall(contact)
                if (!granted) {
                    viewModel.showNotification("Microphone permission is required for voice calls.")
                }
            }
        }
    )

    val initiateCallWithPermission = remember(audioPermissionLauncher, authContext, viewModel) {
        { contact: Contact ->
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                authContext,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasMicPermission) {
                viewModel.startCall(contact)
            } else {
                pendingCallContact = contact
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Scaffold with status elements
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen router
            Crossfade(
                targetState = when {
                    callState !is CallState.Idle -> "call"
                    selectedContactId != null -> "chat"
                    else -> "dashboard"
                },
                label = "screen_router"
            ) { screen ->
                when (screen) {
                    "call" -> {
                        CallView(callState = callState, viewModel = viewModel)
                    }
                    "chat" -> {
                        selectedContact?.let { contact ->
                            ChatConversationView(
                                contact = contact,
                                messages = chatMessages,
                                isTyping = isAITyping,
                                balance = balance,
                                onBack = { viewModel.selectContact(null) },
                                onSendMessage = { text -> viewModel.sendChatMessage(contact.id, text) },
                                onClearChat = { viewModel.clearChat(contact.id) },
                                onCall = { initiateCallWithPermission(contact) }
                            )
                        }
                    }
                    "dashboard" -> {
                        DashboardLayout(
                            contacts = contacts,
                            balance = balance,
                            transactions = transactions,
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onContactSelected = { viewModel.selectContact(it.id) },
                            onDeleteContact = { viewModel.deleteContact(it) },
                            onCreatePersonaClicked = { showCreatePersonaSheet = true },
                            viewModel = viewModel,
                            onCall = initiateCallWithPermission
                        )
                    }
                }
            }

            // Notification Overlay
            apiNotification?.let { toastText ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                        .scale(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Notification Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = toastText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Modal Create Persona form
    if (showCreatePersonaSheet) {
        CreatePersonaDialog(
            onDismiss = { showCreatePersonaSheet = false },
            onCreate = { name, desc, prompt, chatPrice, callPrice ->
                viewModel.createCustomContact(name, desc, prompt, chatPrice, callPrice)
                showCreatePersonaSheet = false
            }
        )
    }
}

// Sub-component rendering gradient brushes for high-end avatars
@Composable
fun getAvatarBrush(seed: String): Brush {
    val colors = when (seed.lowercase()) {
        "nova" -> listOf(Color(0xFF6366F1), Color(0xFF3B82F6))    // Indigo-Blue
        "fitness" -> listOf(Color(0xFFF43F5E), Color(0xFFE11D48)) // Rose-Red
        "chef" -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))    // Amber-Orange
        "mind" -> listOf(Color(0xFF10B981), Color(0xFF047857))    // Emerald-Green
        "finance" -> listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)) // Purple-Violet
        else -> {
            val hash = seed.hashCode()
            val r = (hash and 0xFF0000) shr 16
            val g = (hash and 0x00FF00) shr 8
            val b = hash and 0x0000FF
            val c1 = Color(r or 128, g or 100, b or 120, 255)
            val c2 = Color((g or 80), (b or 100), (r or 160), 255)
            listOf(c1, c2)
        }
    }
    return Brush.linearGradient(colors)
}

// ----------------------------------------------------
// SCREEN 1: THE MAIN DASHBOARD
// ----------------------------------------------------
@Composable
fun DashboardLayout(
    contacts: List<Contact>,
    balance: Double,
    transactions: List<Transaction>,
    currentTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    onContactSelected: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onCreatePersonaClicked: () -> Unit,
    viewModel: ContactViewModel,
    onCall: (Contact) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // App Header and Balance
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wixlo",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Messaging, calls & wallet",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick balance widget (taps to directly open Wallet tab)
            Card(
                onClick = { onTabSelected(DashboardTab.WALLET) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Coins",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "₦${String.format("%.2f", balance)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp)
        ) {
            DashboardTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val containerColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val label = when (tab) {
                    DashboardTab.CHATS -> "Chats"
                    DashboardTab.WALLET -> "Wallet"
                }
                val icon = when (tab) {
                    DashboardTab.CHATS -> Icons.Default.Send
                    DashboardTab.WALLET -> Icons.Default.Star
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 10.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = contentColor)
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
            }
        }

        // Selected Tab Content Display
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Crossfade(targetState = currentTab, label = "tab_router") { tab ->
                when (tab) {
                    DashboardTab.CHATS -> {
                        PersonasView(
                            contacts = contacts,
                            onContactSelected = onContactSelected,
                            onDeleteContact = onDeleteContact,
                            onCreatePersonaClicked = onCreatePersonaClicked
                        )
                    }
                    DashboardTab.WALLET -> {
                        WalletView(
                            balance = balance,
                            transactions = transactions,
                            onAddFunds = { viewModel.addFunds(it) }
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: PERSONAS & GUIDES VIEW
// ----------------------------------------------------

@Composable
fun PersonasView(
    contacts: List<Contact>,
    onContactSelected: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onCreatePersonaClicked: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = contacts.filter { contact ->
        contact.isAIPersona && (
            contact.name.contains(searchQuery, ignoreCase = true) ||
            contact.description.contains(searchQuery, ignoreCase = true)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // SEARCH & CATEGORIES BAR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // High-fidelity search bar input fields
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { 
                        Text(
                            text = "Search chats...", 
                            fontSize = 13.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_search_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )

            }

            // MAIN LIST CONTAINER
            if (contacts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading conversations...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (filteredContacts.isEmpty()) {
                // Premium Empty State visual
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "No matches",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Matching Contacts Found", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Try refiltering your search input keyword.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title section
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONVERSATIONS (${filteredContacts.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                    .clickable { onCreatePersonaClicked() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create new",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Add custom",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    items(filteredContacts, key = { it.id }) { contact ->
                        PersonaContactItem(
                            contact = contact,
                            onSelect = { onContactSelected(contact) },
                            onDelete = { onDeleteContact(contact) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonaContactItem(
    contact: Contact,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact?") },
            text = { Text("Are you sure you want to delete ${contact.name}? This will permanently delete all stored local message history for this contact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Infinite pulsing active glows for modern live design aesthetic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_online")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = {
                    showDeleteDialog = true
                }
            )
            .testTag("contact_card_${contact.name.lowercase()}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient styled modern avatar with integrated pulsing badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(getAvatarBrush(contact.avatarSeed)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                
                // Pulsing high-fidelity active connection indicator for AIs
                if (contact.isAIPersona) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer animated halo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseScale)
                                .graphicsLayer(alpha = pulseAlpha)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        // Inner solid dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = contact.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Rounded design tag for identity
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (contact.isAIPersona) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(30.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (contact.isAIPersona) "AI Persona" else "Address Card",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (contact.isAIPersona) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Text(
                    text = contact.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // High fidelity compact badges inside secure flow
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chat rate tag
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Text Rate", modifier = Modifier.size(9.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "Text: ₦${String.format("%.2f", contact.chatPrice)}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    // Call rate tag
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Call Rate", modifier = Modifier.size(9.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "Call: ₦${String.format("%.2f", contact.callPrice)}/m", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Chevron arrow Indicator
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Open Session",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ----------------------------------------------------
// TAB 2: ACTIVE WALLET & activity LOGS
// ----------------------------------------------------
@Composable
fun WalletView(
    balance: Double,
    transactions: List<Transaction>,
    onAddFunds: (Double) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High fidelity visual fintech card representing User Credit Balance
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFFEC4899),
                                Color(0xFF6366F1)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIGITAL WALLET BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "₦${String.format("%.2f", balance)}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Wixlo Wallet",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STATUS: ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "SECURED WALLET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Deposit option
        item {
            Column {
                Text(
                    text = "Deposit Credits",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(500.0, 1000.0, 2000.0).forEach { amount ->
                        Button(
                            onClick = { onAddFunds(amount) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_add_credit_$amount"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "+₦$amount", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Ledger list
        item {
            Text(
                text = "Transaction History",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No history transactions noted.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(transactions, key = { it.id }) { log ->
                LedgerLogItem(log)
            }
        }
    }
}

@Composable
fun LedgerLogItem(log: Transaction) {
    val dateString = remember(log.timestamp) {
        val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        formatter.format(Date(log.timestamp))
    }

    val isDeposit = log.amount > 0
    val amountColor = if (isDeposit) Color(0xFF10B981) else MaterialTheme.colorScheme.error
    val prefix = if (isDeposit) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon representing type
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDeposit) Color(0xFF10B981).copy(alpha = 0.15f)
                        else if (log.type == "call") Color(0xFF3B82F6).copy(alpha = 0.15f)
                        else Color(0xFF8B5CF6).copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDeposit) Icons.Default.Add
                    else if (log.type == "call") Icons.Default.Call
                    else Icons.Default.Send,
                    contentDescription = log.type,
                    tint = if (isDeposit) Color(0xFF10B981)
                    else if (log.type == "call") Color(0xFF3B82F6)
                    else Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (log.type) {
                        "deposit" -> "Deposited Funds"
                        "text" -> "Message to ${log.contactName}"
                        "call" -> "Call with ${log.contactName}"
                        else -> "Transfer to ${log.contactName}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (log.type == "call" && log.durationSec > 0) {
                        "Duration: ${formatDuration(log.durationSec)} • $dateString"
                    } else {
                        "Sent • $dateString"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$prefix₦${String.format("%.2f", log.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = amountColor
            )
        }
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// ----------------------------------------------------
// SCREEN 2: MAIN CONVERSATION/CHAT SCREEN
// ----------------------------------------------------
@Composable
fun ChatConversationView(
    contact: Contact,
    messages: List<Message>,
    isTyping: Boolean,
    balance: Double,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onCall: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Auto-scroll on new messages
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Row Chat Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back")) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back")
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Avatar badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getAvatarBrush(contact.avatarSeed)),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.take(2).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isTyping) "typing..." else "₦${String.format("%.2f", contact.chatPrice)}/message",
                    fontSize = 11.sp,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Voice call
            IconButton(onClick = onCall, modifier = Modifier.testTag("btn_call")) {
                Icon(imageVector = Icons.Default.Call, contentDescription = "Simulate Voice Call", tint = MaterialTheme.colorScheme.primary)
            }

            // Options drop menu for wipe messages
            var showOptions by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showOptions = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear chat") },
                        onClick = {
                            onClearChat()
                            showOptions = false
                        }
                    )
                }
            }
        }

        // Horizontal billing reminder bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₦${String.format("%.2f", contact.chatPrice)}/message",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Funds: ₦${String.format("%.2f", balance)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubbleItem(message = msg, contact = contact)
            }

            if (isTyping) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 64.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text("Writing response...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Bottom input board
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Type a message...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("msg_input_field"),
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput)
                                textInput = ""
                                focusManager.clearFocus()
                            }
                        }),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput)
                                textInput = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_send_message"),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: Message, contact: Contact) {
    val isUser = message.sender == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(getAvatarBrush(contact.avatarSeed)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(contact.name.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .testTag("chat_bubble_${if (isUser) "user" else "contact"}")
            ) {
                Text(
                    text = message.encryptedContent,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 3: HIGH FIDELITY CALL VIEW
// ----------------------------------------------------
@Composable
fun CallView(
    callState: CallState,
    viewModel: ContactViewModel
) {
    if (callState is CallState.Idle) return

    val contact = when (callState) {
        is CallState.Outgoing -> callState.contact
        is CallState.Connected -> callState.contact
        is CallState.Disconnected -> callState.contact
        else -> return
    }

    val spokenCaption by viewModel.spokenCaption.collectAsStateWithLifecycle()
    val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val isListeningForSpeech by viewModel.isListeningForSpeech.collectAsStateWithLifecycle()

    // Infinite pulsating background radial halo representing visual assistant sound canvas
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val opacityPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_opacity"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1016))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top billing ticker call cost alert
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Safe", tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                    Text(
                        text = "SECURE VOICE CONNECTION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic live accrued charge calculations
            val secondsInCall = if (callState is CallState.Connected) callState.durationSec else 0
            val accruedCost = (secondsInCall / 60.0) * contact.callPrice
            Text(
                text = "Accrued Cost: ₦${String.format("%.4f", accruedCost)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primaryContainer
            )
            Text(
                text = "Rate: ₦${String.format("%.2f", contact.callPrice)}/minute",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Animated Call Monogram Badge
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Dynamic physical amplitude waves expanding behind initials
            if (callState is CallState.Connected) {
                // Layer 1: Ambient passive breathing cycle flow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scalePulse)
                        .clip(CircleShape)
                        .background(Color(0xFF8B5CF6).copy(alpha = opacityPulse * 0.4f))
                )

                // Layer 2: Live voice amplitude reaction (Primary Core Glow)
                val reactiveScale1 by animateFloatAsState(
                    targetValue = 1.0f + (micAmplitude * 1.6f),
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                    label = "reactive_scale_primary"
                )
                Box(
                    modifier = Modifier
                        .size(125.dp)
                        .scale(reactiveScale1)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1).copy(alpha = 0.25f + micAmplitude * 0.4f))
                )

                // Layer 3: Live voice amplitude dynamic border ring (Secondary Echo)
                val reactiveScale2 by animateFloatAsState(
                    targetValue = 1.0f + (micAmplitude * 2.6f),
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 250f),
                    label = "reactive_scale_secondary"
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(reactiveScale2)
                        .clip(CircleShape)
                        .border(
                            width = (1.2f + micAmplitude * 2.0f).dp,
                            color = Color(0xFF10B981).copy(alpha = 0.15f + micAmplitude * 0.5f),
                            shape = CircleShape
                        )
                )
            }

            // Central Monogram circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(getAvatarBrush(contact.avatarSeed)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(2).uppercase(),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Caller state detail, captions, suggestions, and text input grouped together
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = contact.name,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White
            )

            Text(
                text = when (callState) {
                    is CallState.Outgoing -> "Connecting..."
                    is CallState.Connected -> "Connected: " + formatDuration(callState.durationSec)
                    is CallState.Disconnected -> "Ended • Cost: ₦${String.format("%.2f", callState.totalCost)}"
                    else -> ""
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (callState is CallState.Connected) Color(0xFF10B981) else Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Live Spoken Caption Overlay
            if (callState is CallState.Connected && spokenCaption.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = spokenCaption,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // High-Fidelity horizontal chips for recommended talking topics
            if (callState is CallState.Connected) {
                val chips = when {
                    contact.name.contains("Nova", true) -> listOf(
                        "Review secure code snippet", 
                        "Explain coroutines vs threads", 
                        "Best storage encryption level"
                    )
                    contact.name.contains("Marcus", true) -> listOf(
                        "Suggest indoor cardio HIIT routine", 
                        "What post-workout snacks to eat?", 
                        "How can I relieve sore muscles?"
                    )
                    contact.name.contains("Sophia", true) -> listOf(
                        "Fast 15-minute dinner suggestion", 
                        "Help write a healthy grocery list", 
                        "Substitute cream in baking recipes"
                    )
                    contact.name.contains("Elena", true) -> listOf(
                        "Guide me in 1-minute deep breaths", 
                        "Explain a simple stress grounder", 
                        "Mindfulness mantra for today"
                    )
                    contact.name.contains("Aurelius", true) -> listOf(
                        "Marcus Aurelius's view on wealth", 
                        "To make choices under pressure", 
                        "Offer a stoic wealth building tip"
                    )
                    else -> listOf(
                        "Tell me a daily focus habit to try", 
                        "Introduce your core goals", 
                        "How can you assist my productivity?"
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    items(chips) { chipText ->
                        AssistChip(
                            onClick = { viewModel.submitCallVoiceInput(chipText) },
                            label = { Text(chipText, color = MaterialTheme.colorScheme.primaryContainer, fontSize = 11.sp, maxLines = 1) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White
                            ),
                            border = AssistChipDefaults.assistChipBorder(borderColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Custom "Speak / Send Voice Message" interactive input field
            if (callState is CallState.Connected) {
                var voiceMessageText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = voiceMessageText,
                        onValueChange = { voiceMessageText = it },
                        placeholder = {
                            Text(
                                if (isListeningForSpeech) "Listening… speak now" else "Tap mic or type to talk…",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 52.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.07f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                            focusedIndicatorColor = Color(0xFF6366F1),
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (voiceMessageText.trim().isNotEmpty()) {
                                    viewModel.submitCallVoiceInput(voiceMessageText)
                                    voiceMessageText = ""
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = { viewModel.toggleCallSpeechInput() },
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (isListeningForSpeech) Color(0xFF10B981) else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isListeningForSpeech) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Speak to assistant",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (voiceMessageText.trim().isNotEmpty()) {
                                viewModel.submitCallVoiceInput(voiceMessageText)
                                voiceMessageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF6366F1), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice message input",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Controllers list
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.toggleCallSpeechInput() },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (isListeningForSpeech) Color(0xFF10B981).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isListeningForSpeech) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Voice input",
                    tint = Color.White
                )
            }

            // End Call action circle (turns to gray close button when disconnected to prevent getting stuck)
            val isEnded = callState is CallState.Disconnected
            FloatingActionButton(
                onClick = { viewModel.endCall() },
                modifier = Modifier
                    .size(68.dp)
                    .testTag("btn_end_call"),
                containerColor = if (isEnded) Color(0xFF4B5563) else Color(0xFFEF4444),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isEnded) Icons.Default.Close else Icons.Default.Call,
                    contentDescription = if (isEnded) "Dismiss" else "Hangup call",
                    modifier = Modifier
                        .size(32.dp)
                        .scale(if (isEnded) 1f else -1f)
                )
            }

            // Info guide profile trigger
            IconButton(
                onClick = { viewModel.showNotification(contact.description) },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Person, contentDescription = "Persona Details", tint = Color.White)
            }
        }
    }
}

// ----------------------------------------------------
// CREATE PERSONA / CONTACT MODAL DIALOG
// ----------------------------------------------------
@Composable
fun CreatePersonaDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var chatPriceText by remember { mutableStateOf("5") }
    var callPriceText by remember { mutableStateOf("15") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add contact",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create a custom AI contact with messaging and call rates.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Persona Name (e.g., Grumpy Coach)") },
                    modifier = Modifier.fillMaxWidth().testTag("form_input_name"),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Pitch/Description") },
                    modifier = Modifier.fillMaxWidth().testTag("form_input_description"),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("AI personality prompt") },
                    placeholder = { Text("You are a helpful assistant who...", fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .testTag("form_input_prompt"),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                // Billing input rates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatPriceText,
                        onValueChange = { chatPriceText = it },
                        label = { Text("Text price (₦)") },
                        modifier = Modifier.weight(1f).testTag("form_input_text_price"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )

                    OutlinedTextField(
                        value = callPriceText,
                        onValueChange = { callPriceText = it },
                        label = { Text("Call price (₦/m)") },
                        modifier = Modifier.weight(1f).testTag("form_input_call_price"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val finalChatPrice = chatPriceText.toDoubleOrNull() ?: 5.0
                            val finalCallPrice = callPriceText.toDoubleOrNull() ?: 15.0
                            onCreate(name, description, systemPrompt, finalChatPrice, finalCallPrice)
                        },
                        enabled = name.isNotBlank() && description.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("form_btn_create")
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

