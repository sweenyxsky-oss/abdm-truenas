package com.abdownloadmanager.desktop.di

import com.abdownloadmanager.desktop.actions.onevennts.CleanExtraSettingsOnDownloadFinish
import com.abdownloadmanager.shared.storage.IExtraDownloadSettingsStorage
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionAction
import ir.amirab.downloader.downloaditem.IDownloadItem
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.abdownloadmanager.shared.util.onqueuecompletion.NoopOnQueueCompletionActionProvider
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import ir.amirab.util.startup.AbstractStartupManager
import org.koin.dsl.module

/** TrueNAS uses container lifecycle management, not desktop startup or power actions. */
val headlessModule = module {
    includes(coreModule)
    single<AbstractStartupManager> {
        object : AbstractStartupManager() {
            override fun install() = Unit
            override fun uninstall() = Unit
        }
    }
    single<OnDownloadCompletionActionProvider> {
        val storage = get<IExtraDownloadSettingsStorage<*>>()
        object : OnDownloadCompletionActionProvider {
            override suspend fun getOnDownloadCompletionAction(downloadItem: IDownloadItem): List<OnDownloadCompletionAction> =
                listOf(CleanExtraSettingsOnDownloadFinish(storage))
        }
    }
    single<OnQueueCompletionActionProvider> { NoopOnQueueCompletionActionProvider() }
}
