package com.fortexer.zup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Official Myket intents, exactly as documented in the Myket developer knowledge base
 * (myket.ir/kb, section "Myket Intents"):
 *
 *  - open app page : myket://details?id=PACKAGE_NAME
 *  - rate/comment  : myket://comment?id=PACKAGE_NAME
 *  - developer apps: myket://developer/PACKAGE_NAME   (package of any app of that developer)
 *
 * Every call is restricted to the Myket package, checks that Myket is installed first and
 * never throws. The caller decides what to show when it returns a non-OK result.
 */
object Myket {

    const val MARKET_PACKAGE = "ir.mservices.market"

    enum class Result { OK, NOT_INSTALLED, FAILED }

    fun isInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(MARKET_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Throwable) {
        false
    }

    fun openAppPage(context: Context): Result =
        launch(context, "myket://details?id=${context.packageName}")

    fun openComment(context: Context): Result =
        launch(context, "myket://comment?id=${context.packageName}")

    fun openDeveloperApps(context: Context): Result =
        launch(context, "myket://developer/${context.packageName}")

    private fun launch(context: Context, uri: String): Result {
        if (!isInstalled(context)) return Result.NOT_INSTALLED
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setPackage(MARKET_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.OK
        } catch (e: ActivityNotFoundException) {
            Result.FAILED
        } catch (e: Throwable) {
            Result.FAILED
        }
    }
}
