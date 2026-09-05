package com.xiaoiubao.suixinji.data

import java.util.concurrent.locks.ReentrantLock

// Serialize database mutations, backup snapshots and reminder delivery in this process.
object DataAccess {
    val lock = ReentrantLock()
}
