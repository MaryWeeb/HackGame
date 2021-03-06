package com.martmists.hackgame.server.database.dataholders.vfs

import com.martmists.hackgame.common.entities.TextColor
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception

@Serializable
data class VFSDirectory @JvmOverloads constructor(val name: String, var directories: List<VFSDirectory> = emptyList(), var files: List<VFSFile> = emptyList(), var isReadOnly: Boolean = false) {
    @Synchronized
    fun addDir(name: String) : VFSDirectory {
        if (name == "root") {
            throw IllegalArgumentException("Cannot create dir with name 'root'")
        }

        if (directories.any { it.name == name }) {
            throw FileAlreadyExistsException(File(name))
        }

        return VFSDirectory(name).also {
            directories = listOf(*directories.toTypedArray(), it)
        }
    }

    @Synchronized
    fun getDir(name: String) : VFSDirectory {
        return directories.firstOrNull { it.name == name } ?: throw FileNotFoundException(name)
    }

    @Synchronized
    fun getOrCreateDir(name: String) : VFSDirectory {
        return directories.firstOrNull { it.name == name } ?: addDir(name)
    }

    @Synchronized
    fun addFile(file: String) : VFSFile {
        return VFSFile(file, "").also {
            files = listOf(*files.toTypedArray(), it)
        }
    }

    @Synchronized
    fun getFile(file: String) : VFSFile {
        return files.firstOrNull { it.filename == file } ?: throw FileNotFoundException(name)
    }

    @Synchronized
    fun getOrCreateFile(name: String) : VFSFile {
        return files.firstOrNull { it.filename == name } ?: addFile(name)
    }

    @Synchronized
    fun getDirByPath(path: String) : VFSDirectory {
        var dir = this
        for (sub in path.split("/")) {
            dir = dir.getDir(sub)
        }
        return dir
    }

    @Synchronized
    fun getFileByPath(path: String) : VFSFile {
        var dir = this
        val parts = path.split("/").toMutableList()
        val file = parts.removeLast()
        for (sub in parts) {
            dir = dir.getDir(sub)
        }
        return dir.getFile(file)
    }

    @Synchronized
    fun generateView(isRoot: Boolean = true) : String {
        var buf = "${TextColor.ANSI.YELLOW}$name${TextColor.ANSI.WHITE}\n"
        directories.forEachIndexed { index, directory ->

            val text = directory.generateView(false)
            text.split("\n").forEachIndexed { i, line ->
                val c = when {
                    i == 0 && index == directories.lastIndex -> "└──"
                    i == 0 && index != directories.lastIndex -> "├──"
                    i != 0 && index == directories.lastIndex -> "   "
                    i != 0 && index != directories.lastIndex -> "│  "
                    else -> error("Should never happen")
                }
                buf += "$c$line\n"
            }
        }
        files.forEachIndexed { index, file ->
            val c = if (index == files.lastIndex) "└" else "├"
            buf += "$c── ${file.filename}\n"
        }
        return buf.stripTrailing()
    }

    @Synchronized
    fun removeFile(file: String) {
        files = files.toMutableList().also { it.removeIf { itt -> itt.filename == file && !itt.isReadOnly } }.toList()
    }

    @Synchronized
    fun removeDir(dir: String) {
        directories = directories.toMutableList().also { it.removeIf { itt -> itt.name == dir && !itt.isReadOnly } }.toList()
    }

    companion object {
        fun empty() = VFSDirectory("root")
    }
}
