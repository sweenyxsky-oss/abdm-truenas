package com.abdownloadmanager.desktop.di

import com.abdownloadmanager.desktop.DesktopAddDownloadDialogManager
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.DesktopDownloadDialogManager
import com.abdownloadmanager.shared.pagemanager.EditDownloadDialogManager
import com.abdownloadmanager.shared.pagemanager.FileChecksumDialogManager
import com.abdownloadmanager.shared.pagemanager.NotificationSender
import com.abdownloadmanager.shared.pagemanager.PerHostSettingsPageManager
import com.abdownloadmanager.shared.pagemanager.QueuePageManager
import com.abdownloadmanager.desktop.PowerActionManager
import com.abdownloadmanager.desktop.actions.onevennts.DesktopOnDownloadCompletionActionProvider
import com.abdownloadmanager.desktop.actions.onevennts.DesktopOnQueueEventActionProvider
import com.abdownloadmanager.desktop.pages.category.DesktopCategoryDialogManager
import com.abdownloadmanager.desktop.pages.settings.FontManager
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.desktop.storage.*
import com.abdownloadmanager.shared.util.ui.theme.ISystemThemeDetector
import com.abdownloadmanager.desktop.utils.*
import com.abdownloadmanager.desktop.utils.renderapi.CustomRenderApi
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ir.amirab.downloader.db.*
import com.abdownloadmanager.shared.pagemanager.SettingsPageManager
import com.abdownloadmanager.shared.storage.ISelectQueueStorage
import com.abdownloadmanager.shared.storage.SelectQueueSettings
import com.abdownloadmanager.shared.storage.impl.SelectQueueStorage
import com.abdownloadmanager.shared.ui.widget.NotificationManager
import com.abdownloadmanager.shared.util.DesktopSystemThemeDetector
import com.abdownloadmanager.shared.util.*
import kotlinx.coroutines.*
import org.koin.dsl.bind
import org.koin.dsl.module
import com.abdownloadmanager.shared.util.category.*
import com.abdownloadmanager.shared.util.keepawake.KeepAwakeManager
import com.abdownloadmanager.shared.util.keepawake.platformKeepAwake
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.arkivanov.essenty.lifecycle.Lifecycle
import ir.amirab.util.config.datastore.kotlinxSerializationDataStore

/** Desktop windows and OS integrations; never loaded by TrueNAS. */
val desktopGuiModule = module {
    includes(updaterModule, startUpModule, nativeMessagingModule)
    single<OnDownloadCompletionActionProvider> {
        DesktopOnDownloadCompletionActionProvider(get())
    }
    single<OnQueueCompletionActionProvider> {
        DesktopOnQueueEventActionProvider(get())
    }
    single<ISystemThemeDetector> {
        DesktopSystemThemeDetector()
    }
    single {
        ThemeManager(get(), get(), get())
    }
    single {
        FontManager(get())
    }
    single {
        val definedPaths = get<DesktopDefinedPaths>()
        PageStatesStorage(
            kotlinxSerializationDataStore(
                definedPaths.pageStatesStorageFile.toFile(),
                get(),
                PageStatesModel::default,
            )
        )
    }
    single {
        val lifecycle = LifecycleRegistry(
            Lifecycle.State.RESUMED
        )
        val context = DefaultComponentContext(lifecycle)
        runBlocking {
            withContext(Dispatchers.Main) {
                AppComponent(context)
            }
        }
    }.apply {
        bind<DesktopDownloadDialogManager>()
        bind<DesktopAddDownloadDialogManager>()
        bind<DesktopCategoryDialogManager>()
        bind<EditDownloadDialogManager>()
        bind<FileChecksumDialogManager>()
        bind<QueuePageManager>()
        bind<NotificationSender>()
        bind<DownloadItemOpener>()
        bind<PerHostSettingsPageManager>()
        bind<PowerActionManager>()
        bind<SettingsPageManager>()
    }
    single {
        KeepAwakeManager(
            platformKeepAwake(),
            get(),
            get(),
        )
    }
    single<ISelectQueueStorage> {
        val definedPaths = get<DesktopDefinedPaths>()
        SelectQueueStorage(
            kotlinxSerializationDataStore<SelectQueueSettings>(
                definedPaths.selectQueueSettingsFile.toFile(),
                get(),
                ::SelectQueueSettings,
            )
        )
    }
    single { NotificationManager() }

    single {
        val definedPaths = get<DesktopDefinedPaths>()
        CustomRenderApi(definedPaths.renderApiFile)
    }
}
