package com.tools.flowervault

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 密码派生算法：HmacMD5(站点标识, 主密码) 三轮变换 + 大小写规则。
 * hmacMd5 中的 String.format("%02x", byte) 保持固定调用形态，保证输出稳定一致。
 */
object PwCalc {

    private const val CONST_STR1 = "snow"
    private const val CONST_STR2 = "kise"
    private const val CONST_STR3 = "sunlovesnow1990090127xykab"

    fun calcPwd(password: String, key: String): String {
        val pwd = basePwd(password, key).toCharArray()
        var num = 0
        for (i in pwd.indices) {
            if (!pwd[i].isDigit()) {
                pwd[i] = if (num % 2 == 0) pwd[i].lowercaseChar() else pwd[i].uppercaseChar()
                num++
            }
        }
        return String(pwd)
    }

    private fun basePwd(password: String, key: String): String {
        val md5One = hmacMd5(key, password)
        val md5Two = hmacMd5(CONST_STR1, md5One)
        val md5Three = hmacMd5(CONST_STR2, md5One)

        val rule = md5Three.toCharArray()
        val source = md5Two.toCharArray()

        for (i in 0 until 32) {
            if (String(rule, i, 1).contains(CONST_STR3)) {
                source[i] = source[i].uppercaseChar()
            }
        }

        return if (source[0].isDigit()) {
            "K" + String(source, 1, 15)
        } else {
            String(source, 0, 16)
        }
    }

    private fun hmacMd5(key: String, data: String): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "HmacMD5")
        val mac = Mac.getInstance("HmacMD5")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray()).joinToString("") { String.format("%02x", it) }
    }
}
