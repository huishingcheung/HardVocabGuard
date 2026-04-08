package com.jnu.hardvocabguard.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore 必须保持单例（同一个文件同时只能有一个 DataStore 实例）。
 */
val Context.settingsDataStore by preferencesDataStore(name = "settings")

