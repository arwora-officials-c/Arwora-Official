package com.arwora.feature.clone.engine

import android.content.Context
import com.arwora.feature.clone.data.CloneRecord
import java.io.File

class VirtualContainerEngine : CloneEngine {
    override fun isSupported(context: Context): Boolean = false // Stub as requested
    
    override fun createClone(context: Context, basePackage: String, options: CloneOptions): CloneRecord? {
        // Stub implementation
        return null
    }

    override fun launch(context: Context, clone: CloneRecord) {}
    override fun clearCache(context: Context, clone: CloneRecord) {}
    override fun delete(context: Context, clone: CloneRecord) {}
}

class WorkProfileEngine : CloneEngine {
    override fun isSupported(context: Context): Boolean = false
    override fun createClone(context: Context, basePackage: String, options: CloneOptions): CloneRecord? = null
    override fun launch(context: Context, clone: CloneRecord) {}
    override fun clearCache(context: Context, clone: CloneRecord) {}
    override fun delete(context: Context, clone: CloneRecord) {}
}

class RepackagerEngine : CloneEngine {
    override fun isSupported(context: Context): Boolean = false
    override fun createClone(context: Context, basePackage: String, options: CloneOptions): CloneRecord? = null
    override fun launch(context: Context, clone: CloneRecord) {}
    override fun clearCache(context: Context, clone: CloneRecord) {}
    override fun delete(context: Context, clone: CloneRecord) {}
}
