package com.huangder.lumibooks.data.sync

/** Books whose file is in use; sync must not swap their body until they are closed. */
object WebdavOpenBooks {
    private val readers = mutableMapOf<String, Int>()
    @Volatile var listeningBookId: String? = null

    fun setOpen(bookId: String, open: Boolean) {
        synchronized(readers) {
            val count = (readers[bookId] ?: 0) + if (open) 1 else -1
            if (count > 0) readers[bookId] = count else readers.remove(bookId)
        }
    }

    fun isBusy(bookId: String): Boolean = bookId == listeningBookId || synchronized(readers) { bookId in readers }
}
