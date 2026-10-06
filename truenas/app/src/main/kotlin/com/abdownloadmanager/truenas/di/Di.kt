package com.abdownloadmanager.truenas.di

import com.abdownloadmanager.truenas.integration.TrueNasIntegrationHandler
import com.abdownloadmanager.truenas.repository.TrueNasRepository
import com.abdownloadmanager.truenas.storage.TrueNasExtraDownloadItemSettings
import com.abdownloadmanager.truenas.storage.TrueNasExtraQueueSettings
import com.abdownloadmanager.truenas.utils.TrueNasInfo
import com.abdownloadmanager.truenas.utils.proxy.AutoConfigurableProxyProviderForTrueNas
import com.abdownloadmanager.truenas.utils.proxy.TrueNasSystemProxySelectorProvider
import com.abdownloadmanager.truenas.utils.proxy.TrueNasProxyCachingConfig
import com.abdownloadmanager.integration.Integration
import com.abdownloadmanager.integration.IntegrationHandler
import com.abdownloadmanager.integration.model.HLSDownloadCredentialsFromIntegration
import com.abdownloadmanager.integration.model.HttpDownloadCredentialsFromIntegration
import com.abdownloadmanager.integration.model.IDownloadCredentialsFromIntegration
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.downloaderinui.hls.HLSDownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.http.HttpDownloaderInUi
import com.abdownloadmanager.shared.repository.BaseTrueNasRepository
import com.abdownloadmanager.shared.storage.DnsSettings
import com.abdownloadmanager.shared.storage.ExtraDownloadSettingsStorage
import com.abdownloadmanager.shared.storage.ExtraQueueSettingsStorage
import com.abdownloadmanager.shared.storage.IDNSSettingsStorage
import com.abdownloadmanager.shared.storage.IExtraDownloadSettingsStorage
import com.abdownloadmanager.shared.storage.IExtraQueueSettingsStorage
import com.abdownloadmanager.shared.storage.ProxyDatastoreStorage
import com.abdownloadmanager.shared.storage.appsettings.TrueNasSettingsStorage
import com.abdownloadmanager.shared.storage.appsettings.BaseTrueNasSettingsStorage
import com.abdownloadmanager.shared.storage.appsettings.PlatformAppSettingsSchema
import com.abdownloadmanager.shared.storage.impl.DNSStorage
import com.abdownloadmanager.shared.util.AppHostNameVerifier
import com.abdownloadmanager.shared.util.AppSSLFactoryProvider
import com.abdownloadmanager.shared.util.ApiKeyUtil
import com.abdownloadmanager.shared.util.DefinedPaths
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.UserAgentProviderFromSettings
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.DefaultCategories
import com.abdownloadmanager.shared.util.category.DownloadManagerCategoryItemProvider
import com.abdownloadmanager.shared.util.category.ICategoryItemProvider
import com.abdownloadmanager.shared.util.category.CategoryStorage
import com.abdownloadmanager.shared.util.category.CategoryFileStorage
import com.abdownloadmanager.shared.util.di.BaseOKHttpClientQualifier
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorMapperRegistryFactory
import com.abdownloadmanager.shared.util.downloaderror.faileddownloads.FailedDownloadErrorStorageInMemory
import com.abdownloadmanager.shared.util.downloaderror.faileddownloads.FailedDownloads
import com.abdownloadmanager.shared.util.downloaderror.faileddownloads.IFailedDownloadErrorStorage
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionAction
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.abdownloadmanager.shared.util.ondownloadcompletion.OnDownloadCompletionActionRunner
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueEventAction
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueEventActionRunner
import com.abdownloadmanager.shared.util.autoremove.RemovedDownloadsFromDiskTracker
import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadManagerMinimalControl
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.db.DownloadFoldersRegistry
import ir.amirab.downloader.db.DownloadListFileStorage
import ir.amirab.downloader.db.DownloadQueueFileStorageDatabase
import ir.amirab.downloader.db.IDownloadListDb
import ir.amirab.downloader.db.IDownloadPartListDb
import ir.amirab.downloader.db.IDownloadQueueDatabase
import ir.amirab.downloader.db.PartListFileStorage
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.hls.HLSDownloader
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.monitor.DownloadItemStateFactory
import ir.amirab.downloader.monitor.DownloadMonitor
import ir.amirab.downloader.monitor.IDownloadMonitor
import ir.amirab.downloader.queue.ManualDownloadQueue
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.EmptyFileCreator
import ir.amirab.util.config.datastore.createSchemaBasedDatastore
import ir.amirab.util.config.datastore.kotlinxSerializationDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

val downloaderModule = module {
    single<IDownloadQueueDatabase> {
        val definedPaths = get<DefinedPaths>()
        DownloadQueueFileStorageDatabase(
            queueFolder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.queuesDir),
            fileSaver = get(),
        )
    }

    single<IDownloadListDb> {
        val definedPaths = get<DefinedPaths>()
        DownloadListFileStorage(
            downloadListFolder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.downloadListDir),
            fileSaver = get(),
        )
    }

    single { TransactionalFileSaver(get()) }

    single<IDownloadPartListDb> {
        val definedPaths = get<DefinedPaths>()
        PartListFileStorage(
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.partsDir),
            get(),
        )
    }

    single<ir.amirab.downloader.utils.IDiskStat> { com.abdownloadmanager.shared.util.PlatformDiskStat()
    }

    single { QueueManager(get(), get()) }
    single { DownloadFoldersRegistry() }
    single { DownloadSettings(8) }

    single {
        com.abdownloadmanager.shared.util.proxy.ProxyManager(get())
    }.bind<ProxyStrategyProvider>()

    single { TrueNasProxyCachingConfig.default() }

    single<AutoConfigurableProxyProvider> {
        AutoConfigurableProxyProviderForTrueNas(get())
    }

    single<SystemProxySelectorProvider> {
        TrueNasSystemProxySelectorProvider(get())
    }

    single<UserAgentProvider> {
        UserAgentProviderFromSettings(get())
    }

    single<HttpDownloaderClient> {
        OkHttpHttpDownloaderClient(get(), get(), get(), get(), get())
    }

    single {
        val downloadSettings: DownloadSettings = get()
        EmptyFileCreator(
            diskStat = get(),
            useSparseFile = { downloadSettings.useSparseFileAllocation },
        )
    }

    single { HLSDownloader(inject()) }
    single { HLSDownloaderInUi(get()) }
    single { HttpDownloader(inject()) }
    single { HttpDownloaderInUi(get()) }

    single {
        DownloaderInUiRegistry().apply {
            add(get<HttpDownloaderInUi>())
            add(get<HLSDownloaderInUi>())
        }
    }.bind<DownloadItemStateFactory<IDownloadItem, DownloadJob>>()

    single {
        ir.amirab.downloader.DownloaderRegistry().apply {
            add(get<HttpDownloader>())
            add(get<HLSDownloader>())
        }
    }

    single {
        val definedPaths = get<DefinedPaths>()
        DownloadManager(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.downloadDataDir),
        )
    }.bind(DownloadManagerMinimalControl::class)

    single { ManualDownloadQueue(get(), get()) }

    single<IDownloadMonitor> {
        DownloadMonitor(
            downloadManager = get(),
            manualDownloadQueue = get(),
            downloadItemStateFactory = inject(),
        )
    }
}

val downloadSystemModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        get<DownloadFoldersRegistry>().registerAndGet(definedPaths.categoriesDir)
        CategoryFileStorage(
            file = definedPaths.categoriesFile.toFile(),
            fileSaver = get(),
        )
    }.bind<CategoryStorage>()

    single {
        DefaultCategories(
            getDefaultDownloadFolder = { get<BaseTrueNasSettingsStorage>().defaultDownloadFolder.value },
        )
    }

    single {
        DownloadManagerCategoryItemProvider(get())
    }.bind<ICategoryItemProvider>()

    single {
        CategoryManager(
            categoryStorage = get(),
            scope = get(),
            defaultCategoriesFactory = get(),
            categoryItemProvider = get(),
        )
    }

    single {
        DownloadSystem(
            get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(),
        )
    }

    single {
        val definedPaths = get<DefinedPaths>()
        ExtraDownloadSettingsStorage(
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.extraDownloadSettings),
            get(),
            TrueNasExtraDownloadItemSettings,
        )
    }.bind<IExtraDownloadSettingsStorage<*>>()

    single {
        val definedPaths = get<DefinedPaths>()
        ExtraQueueSettingsStorage(
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.extraQueueSettings),
            get(),
            TrueNasExtraQueueSettings,
        )
    }.bind<IExtraQueueSettingsStorage<*>>()

    single<OnDownloadCompletionActionProvider> {
        object : OnDownloadCompletionActionProvider {
            override suspend fun getOnDownloadCompletionAction(downloadItem: IDownloadItem): List<OnDownloadCompletionAction> =
                listOf(object : OnDownloadCompletionAction {
                    override suspend fun onDownloadCompleted(downloadItem: IDownloadItem) {
                        get<IExtraDownloadSettingsStorage<*>>().deleteExtraDownloadItemSettings(downloadItem.id)
                    }
                })
        }
    }

    single<OnQueueCompletionActionProvider> {
        object : OnQueueCompletionActionProvider {
            override suspend fun getOnQueueEventActions(queueId: Long): List<OnQueueEventAction> = emptyList()
        }
    }

    single {
        OnDownloadCompletionActionRunner(
            downloadManagerMinimalControl = get(),
            scope = get(),
            onDownloadCompletionActionProvider = get(),
        )
    }

    single {
        OnQueueEventActionRunner(
            queueManager = get(),
            scope = get(),
            onQueueCompletionActionProvider = get(),
        )
    }
}

val coroutineModule = module {
    single { CoroutineScope(SupervisorJob()) }
}

val jsonModule = module {
    single {
        val downloaderRegistry: ir.amirab.downloader.DownloaderRegistry by inject()
        Json {
            encodeDefaults = true
            prettyPrint = true
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                polymorphic(IDownloadItem::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadItemClass, it.downloadItemSerializer)
                    }
                    defaultDeserializer { HttpDownloadItem.serializer() }
                }
                polymorphic(IDownloadCredentials::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadCredentialsClass, it.downloadCredentialsSerializer)
                    }
                    defaultDeserializer { HttpDownloadCredentials.serializer() }
                }
                polymorphic(IDownloadCredentialsFromIntegration::class) {
                    subclass(HttpDownloadCredentialsFromIntegration::class, HttpDownloadCredentialsFromIntegration.serializer())
                    subclass(HLSDownloadCredentialsFromIntegration::class, HLSDownloadCredentialsFromIntegration.serializer())
                    defaultDeserializer { HttpDownloadCredentialsFromIntegration.serializer() }
                }
            }
        }
    }
}

val integrationModule = module {
    single<IntegrationHandler> { TrueNasIntegrationHandler() }
    single { Integration(get(), get(), get(), TrueNasInfo.isInDebugMode()) }
}

val appModule = module {
    includes(downloaderModule)
    includes(downloadSystemModule)
    includes(coroutineModule)
    includes(jsonModule)
    includes(integrationModule)

    single { TrueNasInfo.definedPaths }.bind<DefinedPaths>()

    single {
        TrueNasRepository(
            get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }.apply {
        bind<BaseTrueNasRepository>()
    }

    single {
        val definedPaths = get<DefinedPaths>()
        ProxyDatastoreStorage(
            kotlinxSerializationDataStore(
                definedPaths.proxySettingsFile.toFile(),
                get(),
                com.abdownloadmanager.shared.util.proxy.ProxyData::default,
            )
        )
    }.bind<com.abdownloadmanager.shared.util.proxy.IProxyStorage>()

    single {
        TrueNasSettingsStorage(
            createSchemaBasedDatastore(
                get<DefinedPaths>().appSettingsFile.toFile(),
                get(),
                PlatformAppSettingsSchema,
            )
        )
    }.apply {
        bind<BaseTrueNasSettingsStorage>()
    }

    single {
        RemovedDownloadsFromDiskTracker(get(), get(), get())
    }

    single {
        AppSSLFactoryProvider(get<BaseTrueNasSettingsStorage>().ignoreSSLCertificates)
    }

    single {
        AppHostNameVerifier(
            delegateHostnameVerifier = OkHostnameVerifier,
            ignoreHostNameVerification = get<BaseTrueNasSettingsStorage>().ignoreSSLCertificates,
        )
    }

    single<IDNSSettingsStorage> {
        DNSStorage(
            kotlinxSerializationDataStore(
                get<DefinedPaths>().dnsSettingsFile.toFile(),
                get(),
                ::DnsSettings,
            )
        )
    }.bind(com.abdownloadmanager.shared.util.dns.DnsOptionProvider::class)

    single<OkHttpClient>(BaseOKHttpClientQualifier) {
        val ssl = get<AppSSLFactoryProvider>()
        val verifier = get<AppHostNameVerifier>()
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = Int.MAX_VALUE
                maxRequestsPerHost = Int.MAX_VALUE
            })
            .sslSocketFactory(ssl.createSSLSocketFactory(), ssl.trustManager)
            .hostnameVerifier(verifier)
            .build()
    }

    single<com.abdownloadmanager.shared.util.dns.AppDns> {
        com.abdownloadmanager.shared.util.dns.AppDns(get<OkHttpClient>(BaseOKHttpClientQualifier), get())
    }

    single<OkHttpClient> {
        get<OkHttpClient>(BaseOKHttpClientQualifier)
            .newBuilder()
            .dns(get())
            .build()
    }
    single {
        DownloadErrorMapperRegistryFactory().createRegistry()
    }

    single<IFailedDownloadErrorStorage> {
        FailedDownloadErrorStorageInMemory()
    }

    single {
        FailedDownloads(get(), get(), get(), get())
    }
}

object Di : KoinComponent {
    fun boot() {
        startKoin {
            modules(appModule)
        }
    }
}
