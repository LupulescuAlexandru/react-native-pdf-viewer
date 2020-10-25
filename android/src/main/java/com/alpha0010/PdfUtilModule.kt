package com.alpha0010

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.*
import java.io.*
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

class PdfUtilModule(reactContext: ReactApplicationContext, private val pdfMutex: Lock) : ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String {
    return "RNPdfUtil"
  }

  /**
   * Extract a bundled asset and return its absolute path.
   */
  @ReactMethod
  fun unpackAsset(source: String, promise: Promise) {
    val file = File(reactApplicationContext.cacheDir, source)
    if (!file.exists()) {
      val asset: InputStream
      try {
        asset = reactApplicationContext.assets.open(source)
      } catch (e: IOException) {
        promise.reject(e)
        return
      }

      val output = FileOutputStream(file)
      val buffer = ByteArray(1024)
      var size = asset.read(buffer)
      while (size != -1) {
        output.write(buffer, 0, size)
        size = asset.read(buffer)
      }
      output.close()
      asset.close()
    }

    promise.resolve(file.absolutePath)
  }

  /**
   * Get the number of pages of a pdf.
   */
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  @ReactMethod
  fun getPageCount(source: String, promise: Promise) {
    val file = File(source)
    val fd: ParcelFileDescriptor
    try {
      fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: FileNotFoundException) {
      promise.reject("ENOENT", e)
      return
    }

    val pageCount = pdfMutex.withLock {
      val renderer = try {
        PdfRenderer(fd)
      } catch (e: Exception) {
        fd.close()
        promise.reject(e)
        return
      }
      val res = renderer.pageCount
      renderer.close()
      return@withLock res
    }
    fd.close()

    promise.resolve(pageCount)
  }

  /**
   * Get the dimensions of every page.
   */
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  @ReactMethod
  fun getPageSizes(source: String, promise: Promise) {
    val file = File(source)
    val fd = try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: FileNotFoundException) {
      promise.reject("ENOENT", e)
      return
    }

    val pageSizes = pdfMutex.withLock {
      val renderer = try {
        PdfRenderer(fd)
      } catch (e: Exception) {
        fd.close()
        promise.reject(e)
        return
      }
      // Read dimensions (in pdf units) of all pages.
      val pages = Arguments.createArray()
      for (pageNum in 0 until renderer.pageCount) {
        val pdfPage = try {
          renderer.openPage(pageNum)
        } catch (e: Exception) {
          renderer.close()
          fd.close()
          promise.reject(e)
          return
        }

        val pageDim = Arguments.createMap()
        pageDim.putInt("height", pdfPage.height)
        pageDim.putInt("width", pdfPage.width)
        pages.pushMap(pageDim)

        pdfPage.close()
      }
      renderer.close()
      return@withLock pages
    }
    fd.close()

    promise.resolve(pageSizes)
  }
}
