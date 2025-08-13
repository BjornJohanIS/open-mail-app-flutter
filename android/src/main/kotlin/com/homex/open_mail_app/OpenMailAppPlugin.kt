package com.homex.open_mail_app

import android.content.Context
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class OpenMailAppPlugin : FlutterPlugin, MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var applicationContext: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "open_mail_app")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "openMailApp" -> {
                val opened = emailAppIntent(call.argument("nativePickerTitle") ?: "")
                result.success(opened)
            }
            "openSpecificMailApp" -> {
                val opened = specificEmailAppIntent(call.argument("name") ?: "")
                result.success(opened)
            }
            "composeNewEmailInMailApp" -> {
                val opened = composeNewEmailAppIntent(
                    call.argument("nativePickerTitle") ?: "",
                    call.argument("emailContent") ?: ""
                )
                result.success(opened)
            }
            "composeNewEmailInSpecificMailApp" -> {
                val opened = composeNewEmailInSpecificEmailAppIntent(
                    call.argument("name") ?: "",
                    call.argument("emailContent") ?: ""
                )
                result.success(opened)
            }
            "getMainApps" -> {
                val apps = getInstalledMailApps()
                result.success(Gson().toJson(apps))
            }
            else -> result.notImplemented()
        }
    }

    private fun emailAppIntent(@NonNull chooserTitle: String): Boolean {
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager

        val activities = packageManager.queryIntentActivities(emailIntent, 0)
        if (activities.isEmpty()) return false

        val firstPackageName = activities.first().activityInfo.packageName
        val chooserIntent = Intent.createChooser(packageManager.getLaunchIntentForPackage(firstPackageName), chooserTitle)

        val extraIntents = activities.drop(1).mapNotNull { activity ->
            packageManager.getLaunchIntentForPackage(activity.activityInfo.packageName)?.let { intent ->
                LabeledIntent(intent, activity.activityInfo.packageName, activity.loadLabel(packageManager), activity.icon)
            }
        }.toTypedArray()

        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents)
        chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(chooserIntent)
        return true
    }

    private fun composeNewEmailAppIntent(@NonNull chooserTitle: String, @NonNull contentJson: String): Boolean {
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val packageManager = applicationContext.packageManager

        val activities = packageManager.queryIntentActivities(emailIntent, 0)
        if (activities.isEmpty()) return false

        val firstActivity = activities.first()
        val chooserIntent = Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            type = "text/plain"
            setClassName(firstActivity.activityInfo.packageName, firstActivity.activityInfo.name)
            putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
            putExtra(Intent.EXTRA_TEXT, emailContent.body)
        }, chooserTitle)

        val extraIntents = activities.drop(1).map { activity ->
            LabeledIntent(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                type = "text/plain"
                setClassName(activity.activityInfo.packageName, activity.activityInfo.name)
                putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
                putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
                putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
                putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
                putExtra(Intent.EXTRA_TEXT, emailContent.body)
            }, activity.activityInfo.packageName, activity.loadLabel(packageManager), activity.icon)
        }.toTypedArray()

        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents)
        chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        applicationContext.startActivity(chooserIntent)
        return true
    }

    private fun specificEmailAppIntent(name: String): Boolean {
        val packageManager = applicationContext.packageManager
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        val activity = packageManager.queryIntentActivities(emailIntent, 0)
            .firstOrNull { it.loadLabel(packageManager) == name } ?: return false

        packageManager.getLaunchIntentForPackage(activity.activityInfo.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(this)
        } ?: return false

        return true
    }

    private fun composeNewEmailInSpecificEmailAppIntent(name: String, contentJson: String): Boolean {
        val emailContent = Gson().fromJson(contentJson, EmailContent::class.java)
        val packageManager = applicationContext.packageManager
        val activity = packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .firstOrNull { it.loadLabel(packageManager) == name } ?: return false

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            type = "text/plain"
            setClassName(activity.activityInfo.packageName, activity.activityInfo.name)
            putExtra(Intent.EXTRA_EMAIL, emailContent.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, emailContent.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, emailContent.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, emailContent.subject)
            putExtra(Intent.EXTRA_TEXT, emailContent.body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        applicationContext.startActivity(intent)
        return true
    }

    private fun getInstalledMailApps(): List<App> {
        val packageManager = applicationContext.packageManager
        val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
        return packageManager.queryIntentActivities(emailIntent, 0).map {
            App(it.loadLabel(packageManager).toString())
        }
    }
}

data class App(@SerializedName("name") val name: String)

data class EmailContent(
    @SerializedName("to") val to: List<String>,
    @SerializedName("cc") val cc: List<String>,
    @SerializedName("bcc") val bcc: List<String>,
    @SerializedName("subject") val subject: String,
    @SerializedName("body") val body: String
)
