@file:JvmName("ConfigLoader")

package cn.net.polyglot.config

import cn.net.polyglot.config.FileSystemConstants.USER_DIR
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.config.ConfigRetrieverOptions
import io.vertx.kotlin.config.ConfigStoreOptions
import io.vertx.kotlin.core.json.JsonObject

/**
 * @author zxj5470
 * @date 2018/7/8
 */
val fileStore = ConfigStoreOptions(
  type = "file",
  format = "json",
  config = JsonObject(
    "path" to "config.json"
  ))

val options = ConfigRetrieverOptions(stores = arrayListOf(fileStore))

const val DEFAULT_PORT = 8080
val defaultJsonObject = JsonObject(
  "port" to DEFAULT_PORT
)

/**
 * if port is 0, vert.x will find a vacant port to bind.
 * @param port Int in Range [0,65535]
 * @return Boolean
 */
private fun checkPortValid(port: Int): Boolean {
  return port in 0..65535
}

fun checkPortValidFromConfig(config: JsonObject): Boolean {
  return try {
    config.getInteger("port").let { checkPortValid(it) }
  } catch (e: Exception) {
    false
  }
}

/**
 * port++
 * @param config JsonObject
 */
fun portInc(config: JsonObject) {
  val port = config.getInteger("port", DEFAULT_PORT) + 1
  config.put("port", port)
}

fun makeAppDirs(vertx: Vertx) {
  val fs = vertx.fileSystem()
  fs.mkdirs(USER_DIR) {
    if (it.succeeded()) {
      println("create $USER_DIR success")
    } else {
      fs.exists(USER_DIR) {
        if (it.succeeded()) {
          if (it.result()) {
            println("$USER_DIR already exists")
          } else {
            println("cannot create $USER_DIR")
          }
        }
      }
    }
  }
}

fun makeDirsBlocking(vertx: Vertx){
  val fs =  vertx.fileSystem()
  fs.mkdirsBlocking(USER_DIR)
}