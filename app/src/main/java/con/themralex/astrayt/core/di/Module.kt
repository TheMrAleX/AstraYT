package con.themralex.astrayt.core.di
import android.content.Context
import androidx.room.Room
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.youtube.*
import dagger.*
import dagger.hilt.*
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context):AstraDatabase=Room.databaseBuilder(context,AstraDatabase::class.java,"astrayt.db").build()
    @Provides fun dao(database: AstraDatabase)=database.dao()
    @Provides @Singleton fun client():OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(false).dispatcher(Dispatcher().apply{maxRequests=24;maxRequestsPerHost=16}).connectionPool(ConnectionPool(16,5,TimeUnit.MINUTES)).build()
    @Provides @Singleton fun backend(backend:NewPipeBackend):YoutubeBackend=backend
}
