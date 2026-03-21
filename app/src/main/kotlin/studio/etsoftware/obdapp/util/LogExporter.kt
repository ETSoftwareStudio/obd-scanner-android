package studio.etsoftware.obdapp.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LogExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun export(
            uri: Uri,
            content: String,
        ): Result<Unit> {
            return runCatching {
                withContext(Dispatchers.IO) {
                    val writer =
                        context.contentResolver
                            .openOutputStream(uri)
                            ?.bufferedWriter(Charsets.UTF_8)
                            ?: error("Unable to open destination for export")

                    writer.use {
                        it.write(content)
                        it.flush()
                    }
                }
            }
        }
    }
