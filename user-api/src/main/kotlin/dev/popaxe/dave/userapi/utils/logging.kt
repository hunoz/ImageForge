package dev.popaxe.dave.userapi.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KClass

object Logging {

    fun logger(clazz: String): KLogger {
        return KotlinLogging.logger(clazz)
    }

    fun logger(clazz: KClass<*>): KLogger {
        return logger(clazz.qualifiedName ?: clazz.java.name)
    }

    fun logger(clazz: Class<*>): KLogger {
        return logger(clazz.name)
    }
}
