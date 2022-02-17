import com.alphaalexcompany.friendbot.LongPollPermissions
import com.alphaalexcompany.friendbot.VkBot
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.GroupActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.*
import org.litote.kmongo.KMongo
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    val parser = ArgParser("friend-bot")
    val groupId by parser.option(ArgType.Int, shortName = "g", description = "Id of group where bot is working")
        .required()
    val accessToken by parser.option(ArgType.String, shortName = "t", description = "VK Access token").required()
    val settings by parser.option(ArgType.Choice<LongPollPermissions>(), shortName = "s").multiple()
        .default(listOf(LongPollPermissions.MessageNew))
    parser.parse(args)

    val logFile = File(System.getenv("BOT_LOGS"))

    val vk = VkApiClient(HttpTransportClient.getInstance())
    val groupActor = GroupActor(groupId, accessToken)

    val longPollSettings = vk.groups().getLongPollSettings(groupActor, groupActor.groupId).execute()
    val longPollSettingsChange = vk.groups().setLongPollSettings(groupActor, groupActor.groupId).apiVersion(vk.version)
    if (!longPollSettings.isEnabled) {
        longPollSettingsChange.enabled(true)
    }
    for (permission in settings) {
        permission.value(longPollSettingsChange, true)
    }
    longPollSettingsChange.execute()

    embeddedServer(Netty, port = System.getenv("PORT").toInt()) {
        install(Authentication) {
            basic("auth-basic") {
                realm = "AlphaFriendBot"
                validate { credentials ->
                    if (credentials.name == System.getenv("BOT_LOGIN") &&
                        credentials.password == System.getenv("BOT_PASSWORD")
                    ) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }
        routing {
            authenticate("auth-basic") {
                get("/") {
                    call.respondText(
                        "It is %s".format(
                            ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
                        )
                    )
                }
                get("/api/log") {
                    call.respondFile(logFile)
                }
                get("/api/clear") {
                    logFile.printWriter().close()
                    call.respondText("cleared")
                }
            }
            get("/api/ping") {
                call.respondText("pong")
            }
        }
    }.start()

    VkBot(KMongo.createClient(System.getenv("MONGO_AUTH")), vk, groupActor).run()
}
