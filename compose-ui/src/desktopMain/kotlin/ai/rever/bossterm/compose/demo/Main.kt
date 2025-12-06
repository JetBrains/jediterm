package ai.rever.bossterm.compose.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.rever.bossterm.compose.settings.SettingsManager
import ai.rever.bossterm.compose.tabs.TabBar
import ai.rever.bossterm.compose.tabs.TabController
import ai.rever.bossterm.compose.ui.ProperTerminal

fun main() = application {
    // Window title state that will be updated by active tab's window title
    val windowTitleState = remember { mutableStateOf("BossTerm Compose - Multiple Terminal Tabs") }

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitleState.value
    ) {
        TerminalApp(
            onExit = ::exitApplication,
            onWindowTitleChange = { newTitle ->
                windowTitleState.value = newTitle
            }
        )
    }
}

@Composable
fun TerminalApp(
    onExit: () -> Unit,
    onWindowTitleChange: (String) -> Unit = {}
) {
    // Settings integration
    val settingsManager = remember { SettingsManager.instance }
    val settings by settingsManager.settings.collectAsState()

    // Load MesloLGS NF (Nerd Font) once and share across all tabs
    // This is expensive (~2.5MB font file) so we only want to do it once
    val nerdFont = remember {
        try {
            println("INFO: Loading shared MesloLGSNF font for all tabs...")
            val fontStream = object {}.javaClass.classLoader?.getResourceAsStream("fonts/MesloLGSNF-Regular.ttf")
                ?: throw IllegalStateException("Font resource not found: fonts/MesloLGSNF-Regular.ttf")

            // Create temp file from InputStream (Skiko classloader workaround)
            val tempFile = java.io.File.createTempFile("MesloLGSNF", ".ttf")
            tempFile.deleteOnExit()
            fontStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("INFO: Loaded shared MesloLGSNF font from: ${tempFile.absolutePath}")
            FontFamily(
                androidx.compose.ui.text.platform.Font(
                    file = tempFile,
                    weight = FontWeight.Normal
                )
            )
        } catch (e: Exception) {
            println("ERROR: Failed to load shared MesloLGSNF font: ${e.message}")
            e.printStackTrace()
            FontFamily.Monospace  // Fallback to system monospace
        }
    }

    // Create tab controller
    val tabController = remember {
        TabController(
            settings = settings,
            onLastTabClosed = onExit
        )
    }

    // Initialize with one tab on first composition
    LaunchedEffect(Unit) {
        if (tabController.tabs.isEmpty()) {
            println("INFO: Creating initial terminal tab...")
            // Note: onProcessExit is now handled by TabController.initializeTerminalSession
            // TabController auto-closes tabs when shell exits, callback is optional for custom logic
            tabController.createTab()
        }
    }

    // Tab UI layout
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab bar at top
        TabBar(
            tabs = tabController.tabs,
            activeTabIndex = tabController.activeTabIndex,
            onTabSelected = { index -> tabController.switchToTab(index) },
            onTabClosed = { index -> tabController.closeTab(index) },
            onNewTab = {
                // Inherit working directory from active tab
                val workingDir = tabController.getActiveWorkingDirectory()
                // TabController handles auto-close on exit, onProcessExit callback is optional
                tabController.createTab(workingDir = workingDir)
            }
        )

        // Render active terminal tab
        if (tabController.tabs.isNotEmpty()) {
            val activeTab = tabController.tabs[tabController.activeTabIndex]

            // Observe active tab's window title (OSC 2) and update main window title bar
            LaunchedEffect(activeTab) {
                activeTab.display.windowTitleFlow.collect { newTitle ->
                    if (newTitle.isNotEmpty()) {
                        onWindowTitleChange(newTitle)
                    }
                }
            }

            ProperTerminal(
                tab = activeTab,
                isActiveTab = true,
                sharedFont = nerdFont,
                onTabTitleChange = { newTitle ->
                    activeTab.title.value = newTitle
                },
                onNewTab = {
                    // Inherit working directory from active tab (Phase 5)
                    val workingDir = tabController.getActiveWorkingDirectory()
                    tabController.createTab(workingDir = workingDir)
                },
                onNewPreConnectTab = {
                    // Demo: Realistic pre-connection flow (Cmd/Ctrl+Shift+T)
                    tabController.createTabWithPreConnect { questioner ->
                        // Step 1: Ask for connection type using dropdown
                        val connectionType = questioner.questionSelection(
                            prompt = "Select connection type:",
                            options = listOf(
                                ai.rever.bossterm.compose.ConnectionState.SelectOption("ssh", "SSH Connection"),
                                ai.rever.bossterm.compose.ConnectionState.SelectOption("db", "Database"),
                                ai.rever.bossterm.compose.ConnectionState.SelectOption("k8s", "Kubernetes Pod"),
                                ai.rever.bossterm.compose.ConnectionState.SelectOption("local", "Local Shell")
                            )
                        )
                        if (connectionType == null) {
                            return@createTabWithPreConnect null
                        }

                        when (connectionType) {
                            "ssh" -> {
                                // SSH Connection Flow - uses native ssh command
                                val host = questioner.questionVisible("SSH Host:", "")
                                if (host.isNullOrBlank()) return@createTabWithPreConnect null

                                val port = questioner.questionVisible("Port:", "22")
                                if (port.isNullOrBlank()) return@createTabWithPreConnect null

                                val username = questioner.questionVisible("Username:", System.getProperty("user.name"))
                                if (username.isNullOrBlank()) return@createTabWithPreConnect null

                                questioner.showMessage("Connecting to $username@$host:$port...")

                                // Use native ssh command - handles password/key auth interactively
                                TabController.PreConnectConfig(
                                    command = "/usr/bin/ssh",
                                    arguments = listOf("-p", port, "$username@$host"),
                                    workingDir = tabController.getActiveWorkingDirectory()
                                )
                            }
                            "db" -> {
                                // Database Connection Flow - uses native CLI tools
                                val dbType = questioner.questionVisible("Database type (mysql/postgres/mongo):", "postgres")
                                if (dbType.isNullOrBlank()) return@createTabWithPreConnect null

                                val host = questioner.questionVisible("Database Host:", "localhost")
                                if (host.isNullOrBlank()) return@createTabWithPreConnect null

                                val port = questioner.questionVisible("Port:", when(dbType.lowercase()) {
                                    "mysql" -> "3306"
                                    "postgres" -> "5432"
                                    "mongo" -> "27017"
                                    else -> "5432"
                                })
                                if (port.isNullOrBlank()) return@createTabWithPreConnect null

                                val username = questioner.questionVisible("Username:", when(dbType.lowercase()) {
                                    "postgres" -> "postgres"
                                    else -> "root"
                                })
                                if (username.isNullOrBlank()) return@createTabWithPreConnect null

                                val database = questioner.questionVisible("Database name:", "")
                                if (database.isNullOrBlank()) return@createTabWithPreConnect null

                                questioner.showMessage("Connecting to $dbType://$host:$port/$database...")

                                // Use native DB CLI - handles password interactively
                                when (dbType.lowercase()) {
                                    "mysql" -> TabController.PreConnectConfig(
                                        command = "mysql",
                                        arguments = listOf("-h", host, "-P", port, "-u", username, "-p", database),
                                        workingDir = tabController.getActiveWorkingDirectory()
                                    )
                                    "postgres" -> TabController.PreConnectConfig(
                                        command = "psql",
                                        arguments = listOf("-h", host, "-p", port, "-U", username, "-d", database),
                                        workingDir = tabController.getActiveWorkingDirectory()
                                    )
                                    "mongo" -> TabController.PreConnectConfig(
                                        command = "mongosh",
                                        arguments = listOf("--host", host, "--port", port, "-u", username, database),
                                        workingDir = tabController.getActiveWorkingDirectory()
                                    )
                                    else -> TabController.PreConnectConfig(
                                        command = "psql",
                                        arguments = listOf("-h", host, "-p", port, "-U", username, "-d", database),
                                        workingDir = tabController.getActiveWorkingDirectory()
                                    )
                                }
                            }
                            "k8s" -> {
                                // Kubernetes Connection Flow - uses kubectl exec
                                val namespace = questioner.questionVisible("Namespace:", "default")
                                if (namespace.isNullOrBlank()) return@createTabWithPreConnect null

                                val pod = questioner.questionVisible("Pod name:", "")
                                if (pod.isNullOrBlank()) return@createTabWithPreConnect null

                                val container = questioner.questionVisible("Container (leave empty for default):", "")

                                val shell = questioner.questionVisible("Shell:", "/bin/sh")
                                if (shell.isNullOrBlank()) return@createTabWithPreConnect null

                                questioner.showMessage("Connecting to pod $pod in namespace $namespace...")

                                // Use kubectl exec for real connection
                                val args = mutableListOf("exec", "-it", "-n", namespace, pod)
                                if (!container.isNullOrBlank()) {
                                    args.addAll(listOf("-c", container))
                                }
                                args.addAll(listOf("--", shell))

                                TabController.PreConnectConfig(
                                    command = "kubectl",
                                    arguments = args,
                                    workingDir = tabController.getActiveWorkingDirectory()
                                )
                            }
                            "local" -> {
                                // Local Shell - just start with custom shell selection
                                val shell = questioner.questionVisible(
                                    "Shell:",
                                    System.getenv("SHELL") ?: "/bin/bash"
                                )
                                if (shell.isNullOrBlank()) return@createTabWithPreConnect null

                                questioner.showMessage("Starting $shell...")

                                TabController.PreConnectConfig(
                                    command = shell,
                                    arguments = listOf("-l"),
                                    workingDir = tabController.getActiveWorkingDirectory()
                                )
                            }
                            else -> {
                                questioner.showMessage("Invalid selection. Starting default shell...")
                                TabController.PreConnectConfig(
                                    command = System.getenv("SHELL") ?: "/bin/bash",
                                    arguments = emptyList(),
                                    workingDir = tabController.getActiveWorkingDirectory()
                                )
                            }
                        }
                    }
                },
                onCloseTab = {
                    // Close current tab (Phase 5)
                    tabController.closeTab(tabController.activeTabIndex)
                },
                onNextTab = {
                    // Switch to next tab (Phase 5)
                    tabController.nextTab()
                },
                onPreviousTab = {
                    // Switch to previous tab (Phase 5)
                    tabController.previousTab()
                },
                onSwitchToTab = { index ->
                    // Switch to specific tab by index (Phase 5)
                    if (index in tabController.tabs.indices) {
                        tabController.switchToTab(index)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
