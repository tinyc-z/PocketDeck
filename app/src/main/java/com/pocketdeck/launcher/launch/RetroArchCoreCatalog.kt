package com.pocketdeck.launcher.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** RetroArch 核心的简短说明类型（界面按类型映射为本地化文案）。 */
enum class CoreNote { Balanced, Accurate, Fast, Legacy, Hardware, ArcadeModern, ArcadeLegacy }

data class CoreInfo(
    val id: String,
    val displayName: String,
    val note: CoreNote?,
)

/**
 * RetroArch 核心易懂名称与说明（避免向用户直接展示 `snes9x2005_plus` 这类内部名）。
 */
object RetroArchCoreCatalog {
    private val INFO: Map<String, Pair<String, CoreNote?>> = mapOf(
        "snes9x" to ("Snes9x" to CoreNote.Balanced),
        "snes9x2010" to ("Snes9x 2010" to CoreNote.Fast),
        "snes9x2005" to ("Snes9x 2005" to CoreNote.Fast),
        "snes9x2005_plus" to ("Snes9x 2005 Plus" to CoreNote.Fast),
        "snes9x2002" to ("Snes9x 2002" to CoreNote.Fast),
        "bsnes" to ("bsnes" to CoreNote.Accurate),
        "bsnes_hd_beta" to ("bsnes HD" to CoreNote.Hardware),
        "mesen-s" to ("Mesen-S" to CoreNote.Accurate),
        "fceumm" to ("FCEUmm" to CoreNote.Balanced),
        "nestopia" to ("Nestopia UE" to CoreNote.Accurate),
        "quicknes" to ("QuickNES" to CoreNote.Fast),
        "mesen" to ("Mesen" to CoreNote.Accurate),
        "gambatte" to ("Gambatte" to CoreNote.Balanced),
        "sameboy" to ("SameBoy" to CoreNote.Accurate),
        "mgba" to ("mGBA" to CoreNote.Balanced),
        "gearboy" to ("Gearboy" to CoreNote.Fast),
        "vba_next" to ("VBA Next" to CoreNote.Fast),
        "vbam" to ("VBA-M" to CoreNote.Legacy),
        "gpsp" to ("gpSP" to CoreNote.Fast),
        "genesis_plus_gx" to ("Genesis Plus GX" to CoreNote.Balanced),
        "picodrive" to ("PicoDrive" to CoreNote.Fast),
        "swanstation" to ("SwanStation" to CoreNote.Balanced),
        "pcsx_rearmed" to ("PCSX ReARMed" to CoreNote.Fast),
        "mednafen_psx_hw" to ("Beetle PSX HW" to CoreNote.Hardware),
        "mednafen_psx" to ("Beetle PSX" to CoreNote.Accurate),
        "mupen64plus_next_gles3" to ("Mupen64Plus-Next (GLES3)" to CoreNote.Balanced),
        "mupen64plus_next_gles2" to ("Mupen64Plus-Next (GLES2)" to CoreNote.Fast),
        "parallel_n64" to ("ParaLLEl N64" to CoreNote.Accurate),
        "melonds" to ("melonDS" to CoreNote.Balanced),
        "melondsds" to ("melonDS DS" to CoreNote.Accurate),
        "desmume" to ("DeSmuME" to CoreNote.Fast),
        "citra" to ("Citra" to null),
        "citra2018" to ("Citra 2018" to CoreNote.Legacy),
        "flycast" to ("Flycast" to null),
        "yabasanshiro" to ("YabaSanshiro" to CoreNote.Balanced),
        "yabause" to ("Yabause" to CoreNote.Fast),
        "mednafen_saturn" to ("Beetle Saturn" to CoreNote.Accurate),
        "fbneo" to ("FinalBurn Neo" to CoreNote.ArcadeModern),
        "mame2003_plus" to ("MAME 2003-Plus" to CoreNote.ArcadeLegacy),
        "mame" to ("MAME" to CoreNote.Accurate),
        "mednafen_pce_fast" to ("Beetle PCE Fast" to CoreNote.Balanced),
        "mednafen_pce" to ("Beetle PCE" to CoreNote.Accurate),
        "mednafen_supergrafx" to ("Beetle SuperGrafx" to null),
        "mednafen_ngp" to ("Beetle NeoPop" to null),
        "mednafen_wswan" to ("Beetle Cygne" to null),
        "stella" to ("Stella" to CoreNote.Balanced),
        "stella2014" to ("Stella 2014" to CoreNote.Fast),
        "handy" to ("Handy" to CoreNote.Fast),
        "mednafen_lynx" to ("Beetle Lynx" to CoreNote.Accurate),
        "bluemsx" to ("blueMSX" to CoreNote.Balanced),
        "fmsx" to ("fMSX" to CoreNote.Fast),
        "vice_x64" to ("VICE x64" to CoreNote.Fast),
        "vice_x64sc" to ("VICE x64sc" to CoreNote.Accurate),
        "puae" to ("PUAE" to CoreNote.Balanced),
        "uae4arm" to ("UAE4ARM" to CoreNote.Fast),
        "opera" to ("Opera" to null),
        "dosbox_pure" to ("DOSBox Pure" to CoreNote.Balanced),
        "dosbox_svn" to ("DOSBox SVN" to CoreNote.Legacy),
        "scummvm" to ("ScummVM" to null),
        "easyrpg" to ("EasyRPG" to null),
        "retro8" to ("Retro8" to null),
        "fake08" to ("FAKE-08" to CoreNote.Fast),
        "tic80" to ("TIC-80" to null),
    )

    fun info(id: String): CoreInfo {
        val key = id.trim().lowercase()
        INFO[key]?.let { (name, note) -> return CoreInfo(id, name, note) }
        val pretty = key.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return CoreInfo(id, pretty, null)
    }

    fun displayName(id: String): String = info(id).displayName

    private const val PREFS_NAME = "pocketdeck_retroarch_saf"
    private const val KEY_CORES_TREE_URI = "cores_tree_uri"

    fun saveSafCoresUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CORES_TREE_URI, uri.toString())
            .apply()
    }

    fun getSafCoresUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CORES_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /**
     * 通过已持久化的 SAF 目录权限与常见外部存储路径精准扫描已安装的 RetroArch `.so` 核心。
     */
    fun scanSafAndExternalCores(context: Context): Set<String> {
        val cores = LinkedHashSet<String>()

        // 1) SAF 持久化授权目录扫描
        getSafCoresUri(context)?.let { treeUri ->
            runCatching {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: continue
                        extractCoreIdFromSo(name)?.let { cores += it }
                    }
                }
            }
        }

        // 2) 自动探测常见外部 RetroArch 核心目录
        val sd = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val probeDirs = buildList {
            if (sd != null) {
                add(File(sd, "RetroArch/cores"))
                add(File(sd, "RetroArch/downloads"))
                add(File(sd, "Android/data/com.retroarch/files/cores"))
                add(File(sd, "Android/data/com.retroarch.aarch64/files/cores"))
                add(File(sd, "Android/data/com.retroarch.ra32/files/cores"))
            }
        }
        for (dir in probeDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile) extractCoreIdFromSo(f.name)?.let { cores += it }
            }
        }
        return cores
    }

    internal fun extractCoreIdFromSo(fileName: String): String? {
        if (!fileName.endsWith("_libretro_android.so", ignoreCase = true)) return null
        return fileName.dropLast("_libretro_android.so".length).takeIf { it.isNotBlank() }
    }
}

/**
 * 未知 App 启动方式自动探测：通过 PackageManager 查询它能否以 ACTION_VIEW 打开文件。
 */
object CustomLaunchProbe {
    data class Detection(val mode: CustomLaunchMode, val activityClass: String = "")

    fun detect(context: Context, pkg: String): Detection {
        val pm = context.packageManager
        fun canHandle(intent: Intent): Boolean =
            runCatching { pm.queryIntentActivities(intent, 0).isNotEmpty() }.getOrDefault(false)

        val contentIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://${context.packageName}.fileprovider/rom/game.bin"), "*/*")
            .setPackage(pkg)
        if (canHandle(contentIntent)) return Detection(CustomLaunchMode.ViewContentUri)

        val fileIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("file:///sdcard/game.bin"), "*/*")
            .setPackage(pkg)
        if (canHandle(fileIntent)) return Detection(CustomLaunchMode.ViewFileUri)

        return Detection(CustomLaunchMode.AppOnly)
    }
}
