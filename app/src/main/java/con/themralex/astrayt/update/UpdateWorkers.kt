package con.themralex.astrayt.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint @InstallIn(SingletonComponent::class) interface UpdateDependencies {fun updates():AppUpdates}
class UpdateCheckWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {EntryPointAccessors.fromApplication(applicationContext,UpdateDependencies::class.java).updates().check();return Result.success()}
}
class UpdateDownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {EntryPointAccessors.fromApplication(applicationContext,UpdateDependencies::class.java).updates().transfer(id.toString());return Result.success()}
}
