package com.example.sillybilibili.util

/**
 * B 站当前的 av↔BV 转换（bilibili-API-collect 文档描述的现行算法，支持到 2^51）。
 * 2019 年旧算法的常量不同，这里只实现当前版本；转换失败返回 null。
 */
object BvConverter {

    private const val TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
    private const val XOR_CODE = 23_442_827_791_579L
    private const val MAX_AID = 2_251_799_813_685_248L // 2^51
    private const val MASK_CODE = MAX_AID - 1L
    private const val BASE = 58

    /** av 号 → BV 号；avid 不在合法范围时返回 null。 */
    fun avidToBv(avid: Long): String? {
        if (avid <= 0L || avid >= MAX_AID) return null
        var tmp = (MAX_AID or avid) xor XOR_CODE
        val chars = charArrayOf('B', 'V', '1', '0', '0', '0', '0', '0', '0', '0', '0', '0')
        var index = 11
        while (tmp > 0L && index >= 3) {
            chars[index] = TABLE[(tmp % BASE).toInt()]
            tmp /= BASE
            index--
        }
        // 字符位置交换
        chars[3] = chars[9].also { chars[9] = chars[3] }
        chars[4] = chars[7].also { chars[7] = chars[4] }
        return String(chars)
    }

    /** BV 号 → av 号；格式非法时返回 null。 */
    fun bvToAvid(bv: String): Long? {
        if (bv.length != 12 || !bv.startsWith("BV")) return null
        val chars = bv.toCharArray()
        chars[3] = chars[9].also { chars[9] = chars[3] }
        chars[4] = chars[7].also { chars[7] = chars[4] }
        var value = 0L
        for (i in 3..11) {
            val tableIndex = TABLE.indexOf(chars[i])
            if (tableIndex < 0) return null
            value = value * BASE + tableIndex
        }
        val avid = (value xor XOR_CODE) and MASK_CODE
        return avid.takeIf { it > 0L }
    }
}
