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
    @Provides @Singleton fun database(@ApplicationContext context: Context):AstraDatabase=Room.databaseBuilder(context,AstraDatabase::class.java,"astrayt.db").addMigrations(object:androidx.room.migration.Migration(1,2){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("ALTER TABLE videos ADD COLUMN likeCount INTEGER NOT NULL DEFAULT -1");db.execSQL("ALTER TABLE videos ADD COLUMN removedDurationMs INTEGER NOT NULL DEFAULT 0")}},object:androidx.room.migration.Migration(2,3){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){
        db.execSQL("ALTER TABLE downloads ADD COLUMN playlistId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE videos ADD COLUMN standalone INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_playlistId ON downloads (playlistId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, sourceUrl TEXT NOT NULL, thumbnail TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_entries (playlistId INTEGER NOT NULL, ytId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, ytId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_entries_ytId ON playlist_entries (ytId)")
    }}).build()
    @Provides fun dao(database: AstraDatabase)=database.dao()
    @Provides @Singleton fun client():OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).retryOnConnectionFailure(false).dispatcher(Dispatcher().apply{maxRequests=24;maxRequestsPerHost=16}).connectionPool(ConnectionPool(16,5,TimeUnit.MINUTES)).build()
    @Provides @Singleton fun backend(backend:NewPipeBackend):YoutubeBackend=backend
}
