package com.proxydegil.proxyapp.utils

import java.io.DataOutputStream

object RootHelper {
    fun isRootAvailable(): Boolean {
        return execute(listOf("id"))
    }

    fun execute(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (command in commands) {
                os.writeBytes("$command\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            val result = process.waitFor()
            os.close()
            result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
