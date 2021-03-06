package cn.net.polyglot.verticle.friend

import cn.net.polyglot.config.*
import cn.net.polyglot.verticle.im.IMTcpServerVerticle
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientSession
import io.vertx.kotlin.core.file.*
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch
import java.io.File.separator

class FriendVerticle : CoroutineVerticle() {

  private lateinit var webClient: WebClientSession

  override suspend fun start() {
    webClient = WebClientSession.create(WebClient.create(vertx))

    vertx.eventBus().consumer<JsonObject>(this::class.java.name) {
      launch { friend(it.body()) }
    }
  }

  private suspend fun friend(json: JsonObject) {

    val subtype = json.getString(SUBTYPE)
    val from = json.getString(ID)
    val to = json.getString(TO)
    if (from == null || to == null) {
      //不做处理，不需要反复确认，因为io层次一多，反复确认会导致代码和性能上的浪费，不值得花大力气去确保这点意外
      //确保错误情况不会影响系统便可
      return
    }

    when (subtype) {
      DELETE -> {
      }
      REQUEST -> {
        val dir = config.getString(DIR) + separator
        val fs = vertx.fileSystem()
        if (!from.contains("@")) {    //本地保存发送记录

          if (!fs.existsAwait("$dir$from$separator.send"))
            fs.mkdirsAwait("$dir$from$separator.send")
          if (fs.existsAwait("$dir$from$separator.send$separator$to.json")) {
            fs.deleteAwait("$dir$from$separator.send$separator$to.json")
          }
          fs.createFileAwait("$dir$from$separator.send$separator$to.json")
          fs.writeFileAwait("$dir$from$separator.send$separator$to.json", json.toBuffer())
        }
        if (to.contains("@")) {    //如果跨域，转发给你相应的服务器
          json.put(FROM, from + "@" + config.getString(HOST))//把from加上域名
          webClient.put(config.getInteger(HTTP_PORT), to.substringAfterLast("@"), "/$USER/$REQUEST")
            .sendJsonObject(json.put(TO, to.substringBeforeLast('@'))) {}
        } else {    //接受是其他服务器发送过来的请求

          fs.mkdirsAwait("$dir$to$separator.receive")
          if (fs.existsAwait("$dir$to$separator.receive$separator$from.json")) {
            fs.deleteAwait("$dir$to$separator.receive$separator$from.json")
          }
          fs.createFileAwait("$dir$to$separator.receive$separator$from.json")
          fs.writeFileAwait("$dir$to$separator.receive$separator$from.json", json.toBuffer().appendString(END))
          //尝试投递
          vertx.eventBus().send(IMTcpServerVerticle::class.java.name, json)
        }
      }
      RESPONSE -> {
        val dir = config.getString(DIR) + separator
        val fs = vertx.fileSystem()

        if (!from.contains("@")) {
          if (fs.existsAwait("$dir$from$separator.receive$separator$to.json")) {
            val requestJson = fs.readFileAwait("$dir$from$separator.receive$separator$to.json").toJsonObject()
            fs.deleteAwait("$dir$from$separator.receive$separator$to.json")//删除
            if (json.containsKey(ACCEPT)&&json.getBoolean(ACCEPT)) {
              if (!fs.existsAwait("$dir$from$separator$to")) {
                fs.mkdirsAwait("$dir$from$separator$to")
                val fileDir = "$dir$from$separator$to$separator$to.json"
                fs.createFileAwait(fileDir)
                fs.writeFileAwait(fileDir, JsonObject()
                  .put(ID, to)
                  .put(NICKNAME, requestJson.getString(NICKNAME) ?: to)
                  .toBuffer())
              }
            }
          } else {
            return //错误，没有收到好友请求，流程到此结束
          }
        }

        if (json.getString(TO).contains("@")) {
          json.put(FROM, from + "@" + config.getString(HOST))//把from加上域名
          webClient.put(config.getInteger(HTTP_PORT), to.substringAfterLast("@"), "/$USER/$RESPONSE")
            .sendJsonObject(json.put(TO, to.substringBeforeLast("@"))) {}
        } else {
          if (fs.existsAwait("$dir$to$separator.send$separator$from.json")) {
            fs.deleteAwait("$dir$to$separator.send$separator$from.json")
            if (json.containsKey(ACCEPT)&&json.getBoolean(ACCEPT)) {
              if (!fs.existsAwait("$dir$to$separator$from")) {
                fs.mkdirsAwait("$dir$to$separator$from")
                val fileDir1 = "$dir$to$separator$from$separator$from.json"
                fs.createFileAwait(fileDir1)
                fs.writeFileAwait(fileDir1, JsonObject()
                  .put(ID, from)
                  .put(NICKNAME, json.getString(NICKNAME) ?: from)
                  .toBuffer())
              }
              //尝试投递
              vertx.eventBus().send(IMTcpServerVerticle::class.java.name, json)
            }
          }
        }
      }
      else -> {
      }
    }
  }
}
