package com.huangder.lumibooks.ui.reader

import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDexArgumentBudgetTest {
    @Test
    fun generatedReaderMethodsLeaveRoomForDexArgumentRegisters() {
        // The failing release method captured 241 parameters / 248 words. Stay
        // comfortably below the 255-word invoke encoding limit, including longs
        // and doubles, so adding a callback cannot recreate that fragile shape.
        // ART verification of the minified release is still required separately.
        val facades = listOf("ReaderScreenKt", "ContinuousScrollReaderOrientationKt",
            "MarkdownNativeReaderOrientationKt", "EpubWebViewReaderOrientationKt", "ReaderOrientationContentKt")
        val oversized = facades.flatMap { name -> Class.forName(
            "com.huangder.lumibooks.ui.reader.$name", false, javaClass.classLoader).declaredMethods.toList()
        }.mapNotNull { method ->
            val receiver = if (Modifier.isStatic(method.modifiers)) 0 else 1
            val words = method.parameterTypes.fold(receiver) { count, parameter ->
                count + if (parameter == java.lang.Long.TYPE || parameter == java.lang.Double.TYPE) 2 else 1
            }
            if (words > 192) "${method.declaringClass.simpleName}.${method.name}: $words argument words" else null
        }
        assertTrue(
            "Split reader composable captures before exceeding the 192-word budget:\n" +
                oversized.joinToString("\n"),
            oversized.isEmpty()
        )
    }
}
