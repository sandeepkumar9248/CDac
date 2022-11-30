package com.peoplellink.p2psdk

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener


class LoginActivity : AppCompatActivity() {


    @RequiresApi(Build.VERSION_CODES.S)
    private val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        this.supportActionBar!!.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val userName = findViewById<EditText>(R.id.uname)
        val login = findViewById<Button>(R.id.btnClick)

        login.setOnClickListener { _: View? ->

            Dexter.withContext(this)
                .withPermissions(permissions).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {

                        if (report.areAllPermissionsGranted()) {
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.putExtra("name", userName.text.toString())
                            startActivity(intent)
                        } else {
                            showRationalDialogForPermissions()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        token?.continuePermissionRequest()

                    }
                }).withErrorListener {
                    Toast.makeText(this, it.name, Toast.LENGTH_SHORT).show()
                }.check()


        }
    }


    fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage(
            "It looks that you have turned off " +
                    "permissions required for these features. It can be enabled under " +
                    "applications settings"
        ).setPositiveButton("GO TO SETTINGS") { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", "com.peoplelink.invcandroid", null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
}