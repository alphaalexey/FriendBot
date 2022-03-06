package com.alphaalexcompany.friendbot

import com.alphaalexcompany.friendbot.`object`.*
import com.alphaalexcompany.friendbot.`object`.Chat
import com.google.gson.Gson
import com.mongodb.client.MongoClient
import com.vk.api.sdk.callback.longpoll.CallbackApiLongPoll
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.GroupActor
import com.vk.api.sdk.objects.callback.MessageAllow
import com.vk.api.sdk.objects.callback.MessageDeny
import com.vk.api.sdk.objects.callback.MessageNew
import com.vk.api.sdk.objects.callback.MessageTypingState
import com.vk.api.sdk.objects.messages.*
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.system.exitProcess

class VkBot(
    mongoClient: MongoClient,
    client: VkApiClient,
    private val actor: GroupActor
) : CallbackApiLongPoll(client, actor) {
    private val database = mongoClient.getDatabase("friend-bot")
    private val chats = database.getCollection<Chat>()
    private val interests = database.getCollection<Interest>()
    private val users = database.getCollection<User>()
    private val translations = database.getCollection<Translation>()

    private fun work(user: User, translation: Translation, message: MessageNew, payload: Payload) {
        if (message.message.text.lowercase() == translation.data["message_stuck"]) {
            user.command = null
            users.updateOne(user)
            message.message.text = ""
            work(user, translation, message, Payload().setCommand(Command.Start.value))
        } else {
            when (payload.command) {
                Command.Start.value, Command.Menu.value -> {
                    if (user.age != 0) {
                        client.messages().send(actor)
                            .keyboard(createMenuKeyboard(user, translation))
                            .message(translation.data["message_menu"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                    } else {
                        work(user, translation, message, Payload().setCommand(Command.SetAge.value))
                    }
                }
                Command.FillForm.value -> {
                    user.command = InputCommand.SetInterest
                    users.updateOne(user)
                    val userInterests = interests.find(Interest::userId eq user.id)
                    val emptyInterests = interests.find(and(Interest::userId eq user.id, Interest::data eq ""))
                    if (emptyInterests.none()) {
                        val hasNoInterests: Boolean = userInterests.none()
                        client.messages().send(actor)
                            .keyboard(
                                if (hasNoInterests) {
                                    createEmptyKeyboard()
                                } else {
                                    createCancelKeyboard(
                                        translation,
                                        Payload().setCommand(Command.FillFormCancel.value)
                                    )
                                }
                            )
                            .message(translation.data["message_current_interests"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        if (hasNoInterests) {
                            client.messages().send(actor)
                                .message(translation.data["message_no_interests"])
                                .peerId(user.id)
                                .randomId(Random.nextInt())
                                .execute()
                        } else {
                            userInterests.forEach { interest ->
                                client.messages().send(actor)
                                    .keyboard(createRemoveKeyboard(translation, interest.id.toString()))
                                    .message("${translation.data[interest.theme.value]}\n- ${interest.data}")
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        client.messages().send(actor)
                            .message(translation.data["message_add_interest"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        client.messages().send(actor)
                            .message(Theme.values().joinToString("\n") { theme ->
                                "${theme.ordinal + 1}. ${translation.data[theme.value]}"
                            })
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                    } else {
                        user.command = InputCommand.SetInterestData
                        users.updateOne(user)
                        client.messages().send(actor)
                            .keyboard(createEmptyKeyboard())
                            .message(translation.data["message_has_empty_interests"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        client.messages().send(actor)
                            .keyboard(createEmptyKeyboard())
                            .message(translation.data[emptyInterests.first()?.theme?.value])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        client.messages().send(actor)
                            .message(translation.data["message_set_interest_data_" + emptyInterests.first()?.theme?.value])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                    }
                }
                Command.FillFormCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, Payload().setCommand(Command.Menu.value))
                }
                Command.RemoveInterest.value -> {
                    if (interests.deleteOneById(ObjectId(payload.data)).deletedCount == 1L) {
                        client.messages().send(actor)
                            .message(translation.data["message_removed"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                    }
                }
                Command.FormsUsers.value -> {
                    val userInterests = interests.find(Interest::userId eq user.id)
                    if (userInterests.none()) {
                        client.messages().send(actor)
                            .message(translation.data["message_fill_form"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        work(user, translation, message, Payload().setCommand(Command.FillForm.value))
                    } else {
                        client.messages().send(actor)
                            .message(translation.data["message_searching"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                        work(user, translation, message, Payload().setCommand(Command.Search.value))
                    }
                }
                Command.Search.value -> {
                    val userInterests = interests.find(Interest::userId eq user.id)
                    val userThemes = userInterests.map { it.theme }
                    val suitableInterests = interests.find(
                        and(
                            Interest::userId ne user.id,
                            Interest::userId nin user.liked,
                            Interest::theme `in` userThemes,
                            Interest::data ne ""
                        )
                    )
                    var found = false
                    suitableInterests.shuffled().forEach { suitableInterest ->
                        val userId = suitableInterest.userId
                        val foundResponse = client.users().get(actor).userIds(userId.toString()).execute()
                        if (foundResponse.size == 1) {
                            found = true
                            val foundUser = foundResponse[0]
                            users.findOneById(userId)?.let { userData ->
                                client.messages().send(actor)
                                    .message("${foundUser.firstName} (${userData.age})")
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                                interests.find(
                                    and(
                                        Interest::userId eq userId,
                                        Interest::theme `in` userThemes,
                                        Interest::data ne ""
                                    )
                                ).shuffled().first().let { interest ->
                                    client.messages().send(actor)
                                        .keyboard(
                                            createSearchKeyboard(
                                                translation,
                                                GSON.toJson(SearchData(userId, interest.theme))
                                            )
                                        )
                                        .message("${translation.data[interest.theme.value]}\n- ${interest.data}")
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                                return
                            }
                        }
                    }
                    if (!found) {
                        client.messages().send(actor)
                            .keyboard(createMenuKeyboard(user, translation))
                            .message(translation.data["message_search_empty"])
                            .peerId(user.id)
                            .randomId(Random.nextInt())
                            .execute()
                    }
                }
                Command.Like.value -> {
                    val searchData = GSON.fromJson(payload.data, SearchData::class.java)
                    val userId = searchData.userId
                    user.liked.add(userId)
                    users.updateOne(user)
                    client.messages().send(actor)
                        .message(translation.data["message_liked_user"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                    users.findOneById(userId)?.let { likedUser ->
                        if (user.id in likedUser.liked) {
                            client.messages().send(actor)
                                .message(translation.data["message_both_liked"])
                                .peerId(user.id)
                                .randomId(Random.nextInt())
                                .execute()
                            client.messages().send(actor)
                                .message(
                                    "${
                                        client.users().get(actor).userIds(userId.toString()).execute()[0].firstName
                                    } (${likedUser.age})\n@id$userId"
                                )
                                .peerId(user.id)
                                .randomId(Random.nextInt())
                                .execute()
                            if (!likedUser.stopped) {
                                client.messages().send(actor)
                                    .message(translation.data["message_both_liked"])
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                                client.messages().send(actor)
                                    .message(
                                        "${
                                            client.users().get(actor).userIds(user.id.toString()).execute()[0].firstName
                                        } (${user.age})\n@id${user.id}"
                                    )
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        } else {
                            if (!likedUser.stopped) {
                                client.messages().send(actor)
                                    .keyboard(
                                        createLikedKeyboard(
                                            translation,
                                            GSON.toJson(SearchData(user.id, searchData.theme))
                                        )
                                    )
                                    .message(translation.data["message_liked"])
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                    }
                    work(user, translation, message, Payload().setCommand(Command.Search.value))
                }
                Command.ViewLike.value -> {
                    val searchData = GSON.fromJson(payload.data, SearchData::class.java)
                    val userId = searchData.userId
                    val foundResponse = client.users().get(actor).userIds(userId.toString()).execute()
                    if (foundResponse.size == 1) {
                        val foundUser = foundResponse[0]
                        users.findOneById(userId)?.let { userData ->
                            interests.find(
                                and(
                                    Interest::userId eq userId,
                                    Interest::theme eq searchData.theme,
                                    Interest::data ne ""
                                )
                            ).firstOrNull()?.let { interest ->
                                client.messages().send(actor)
                                    .keyboard(createSearchKeyboard(translation, payload.data))
                                    .message("${foundUser.firstName} (${userData.age})")
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                                client.messages().send(actor)
                                    .keyboard(createSearchKeyboard(translation, GSON.toJson(interest)))
                                    .message("${translation.data[interest.theme.value]}\n- ${interest.data}")
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                    }
                }
                Command.FormsChats.value -> {
                    user.command = InputCommand.SearchChat
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(
                            createCancelKeyboard(
                                translation,
                                Payload().setCommand(Command.FormsChatsCancel.value)
                            )
                        )
                        .message(translation.data["message_forms_choose"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                    client.messages().send(actor)
                        .message(Theme.values().joinToString("\n") { theme ->
                            "${theme.ordinal + 1}. ${translation.data[theme.value]}"
                        })
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.FormsChatsCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, Payload().setCommand(Command.Menu.value))
                }
                Command.SetAge.value -> {
                    user.command = InputCommand.SetAge
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createEmptyKeyboard())
                        .message(translation.data["message_set_age"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.Admin.value -> {
                    client.messages().send(actor)
                        .keyboard(createAdminKeyboard(user, translation))
                        .message(translation.data["message_admin"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.RemoveAdmin.value -> {
                    user.command = InputCommand.RemoveAdmin
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(
                            createCancelKeyboard(translation, Payload().setCommand(Command.RemoveAdminCancel.value))
                        )
                        .message(translation.data["message_remove_admin"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.SetAdmin.value -> {
                    user.command = InputCommand.SetAdmin
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createCancelKeyboard(translation, Payload().setCommand(Command.SetAdminCancel.value)))
                        .message(translation.data["message_set_admin"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.RemoveAdminCancel.value, Command.SetAdminCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, Payload().setCommand(Command.Admin.value))
                }
                Command.Stop.value -> {
                    client.messages().send(actor)
                        .keyboard(createMenuKeyboard(user, translation))
                        .message(translation.data["message_stop"])
                        .peerId(user.id)
                        .randomId(Random.nextInt())
                        .execute()
                    exitProcess(0)
                }
                else -> {
                    when (user.command) {
                        InputCommand.RemoveAdmin -> {
                            try {
                                val adminId: Int = message.message.text.toInt()
                                val admin: User = users.findOneById(adminId)!!
                                if (UserLevel.ADMIN <= admin.level && admin.level < user.level) {
                                    admin.level = UserLevel.USER
                                    users.updateOne(admin)
                                    if (!admin.stopped) {
                                        client.messages().send(actor)
                                            .keyboard(createMenuKeyboard(user, translation))
                                            .message(translation.data["message_removed_admin"])
                                            .peerId(adminId)
                                            .randomId(Random.nextInt())
                                            .execute()
                                    }
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_remove_admin_success"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_remove_admin_forbidden"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                                user.command = null
                                users.updateOne(user)
                            } catch (_: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_remove_admin_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SetAdmin -> {
                            try {
                                val adminId: Int = message.message.text.toInt()
                                val admin: User = users.findOneById(adminId)!!
                                if (admin.level < UserLevel.ADMIN && user.level > UserLevel.ADMIN) {
                                    admin.level = UserLevel.ADMIN
                                    users.updateOne(admin)
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_set_admin_success"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_set_admin_forbidden"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                                user.command = null
                                users.updateOne(user)
                            } catch (_: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_admin_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SetAge -> {
                            try {
                                val age: Int = message.message.text.toInt()
                                if (age in 12..122) {
                                    user.age = age
                                    user.command = null
                                    users.updateOne(user)
                                    client.messages().send(actor)
                                        .keyboard(createMenuKeyboard(user, translation))
                                        .message(translation.data["message_set_age_success"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_age_incorrect"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } catch (e: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_age_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SetInterest -> {
                            try {
                                val index: Int = message.message.text.toInt() - 1
                                val themes = Theme.values()
                                if (index in themes.indices) {
                                    interests.insertOne(Interest(user.id, themes[index]))
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_interest_data_" + themes[index].value])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                    user.command = InputCommand.SetInterestData
                                    users.updateOne(user)
                                } else {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_interest_incorrect"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } catch (e: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_interest_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SetInterestData -> {
                            if (message.message.text.isNotEmpty()) {
                                interests.findOne(Interest::data eq "")?.let { interest ->
                                    interest.data = message.message.text
                                    interests.updateOne(interest)
                                    user.command = null
                                    users.updateOne(user)
                                    work(user, translation, message, Payload().setCommand(Command.FormsUsers.value))
                                } ?: run {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_interest_data_failed"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } else {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_interest_data_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SearchChat -> {
                            try {
                                val index: Int = message.message.text.toInt() - 1
                                val themes = Theme.values()
                                if (index in themes.indices) {
                                    val chat = chats.find(Chat::theme eq themes[index]).map { group ->
                                        client.messages().getConversationsById(actor, group.id)
                                            .execute().items.first()
                                    }.filter { chat -> chat.chatSettings.membersCount < 20 }.randomOrNull()
                                    if (chat != null) {
                                        client.messages().send(actor)
                                            .keyboard(createMenuKeyboard(user, translation))
                                            .message(translation.data["message_set_chat_success"])
                                            .peerId(user.id)
                                            .randomId(Random.nextInt())
                                            .execute()
                                        client.messages().send(actor)
                                            .message(
                                                client.messages().getInviteLink(actor, chat.peer.id).execute().link
                                            )
                                            .peerId(user.id)
                                            .randomId(Random.nextInt())
                                            .execute()
                                    } else {
                                        client.messages().send(actor)
                                            .keyboard(createMenuKeyboard(user, translation))
                                            .message(translation.data["message_set_chat_no_left"])
                                            .peerId(user.id)
                                            .randomId(Random.nextInt())
                                            .execute()
                                    }
                                    user.command = null
                                    users.updateOne(user)
                                } else {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_chat_incorrect"])
                                        .peerId(user.id)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } catch (e: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_chat_failed"])
                                    .peerId(user.id)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        else -> {
                            client.messages().send(actor)
                                .message(translation.data["message_unknown"])
                                .peerId(user.id)
                                .randomId(Random.nextInt())
                                .execute()
                        }
                    }
                }
            }
        }
    }

    override fun messageAllow(groupId: Int?, message: MessageAllow?) {
        super.messageAllow(groupId, message)

        message?.userId?.let { userId ->
            val user = client.users().get(actor).userIds(userId.toString()).execute().first()
            LOG.info("{} {}(id{}) разрешил доступ.", user.firstName, user.lastName, userId)

            users.findOneById(userId)?.let { botUser ->
                botUser.stopped = false
                users.updateOne(botUser)

                translations.findOneById(botUser.lang)?.let { translation ->
                    botUser.command = null
                    users.updateOne(botUser)
                    client.messages().send(actor)
                        .keyboard(createMenuKeyboard(botUser, translation))
                        .message(translation.data["message_return"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
            } ?: run {
                users.insertOne(User(userId))
            }
        }
    }

    override fun messageDeny(groupId: Int?, message: MessageDeny?) {
        super.messageDeny(groupId, message)

        message?.userId?.let { userId ->
            val user = client.users().get(actor).userIds(userId.toString()).execute().first()
            LOG.info("{} {}(id{}) запретил доступ.", user.firstName, user.lastName, userId)

            users.updateOneById(userId, setValue(User::stopped, true))
        }
    }

    override fun messageNew(groupId: Int?, message: MessageNew?) {
        super.messageNew(groupId, message)

        message?.message?.peerId?.let { peerId ->
            if (peerId in 0..1999999999) {
                val user = client.users().get(actor).userIds(peerId.toString()).execute().first()
                LOG.info("{} {}(id{}) сказал: {}", user.firstName, user.lastName, peerId, message.message?.text)

                var isNewUser = false

                var botUser = users.findOneById(peerId)
                if (botUser == null) {
                    isNewUser = true
                    botUser = User(peerId)
                    users.insertOne(botUser)
                }

                translations.findOneById(botUser.lang)?.let { translation ->
                    work(
                        botUser,
                        translation,
                        message,
                        if (isNewUser) {
                            Payload()
                                .setCommand(Command.Start.value)
                        } else {
                            GSON.fromJson(message.message.payload ?: GSON.toJson(Payload()), Payload::class.java)
                        }
                    )
                }
            }
        }
    }

    override fun messageReply(groupId: Int?, message: Message?) {
        super.messageReply(groupId, message)

        message?.peerId?.let { userId ->
            val user = client.users().get(actor).userIds(userId.toString()).execute().first()
            LOG.info(
                "{} {}(id{}) получил ответ на сообщение({}): {}",
                user.firstName, user.lastName, userId,
                message.id, message.text
            )
        }
    }

    override fun messageTypingState(groupId: Int?, message: MessageTypingState?) {
        super.messageTypingState(groupId, message)

        message?.fromId?.let { peerId ->
            if (-peerId != groupId) {
                val user = client.users().get(actor).userIds(peerId.toString()).execute().first()
                LOG.info(
                    "{} {}(id{}) {}.",
                    user.firstName, user.lastName, peerId,
                    message.state
                )
                client.messages().setActivity(actor)
                    .groupId(groupId)
                    .type(SetActivityType.TYPING)
                    .peerId(peerId)
                    .execute()
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(VkBot::class.java)
        private val GSON: Gson = Gson()

        @JvmStatic
        private fun createEmptyKeyboard(): Keyboard {
            return Keyboard().setButtons(listOf())
        }

        @JvmStatic
        private fun createMenuKeyboard(user: User, translation: Translation): Keyboard {
            val buttons =
                arrayListOf(
                    listOf(
                        KeyboardButton()
                            .setAction(
                                KeyboardButtonAction()
                                    .setLabel(translation.data["label_forms_users"])
                                    .setPayload(GSON.toJson(Payload().setCommand(Command.FormsUsers.value)))
                                    .setType(TemplateActionTypeNames.TEXT)
                            ),
                        KeyboardButton()
                            .setAction(
                                KeyboardButtonAction()
                                    .setLabel(translation.data["label_forms_chats"])
                                    .setPayload(GSON.toJson(Payload().setCommand(Command.FormsChats.value)))
                                    .setType(TemplateActionTypeNames.TEXT)
                            )
                    )
                )
            val row: ArrayList<KeyboardButton> = arrayListOf()
            if (user.level >= UserLevel.ADMIN) {
                row.add(
                    KeyboardButton()
                        .setAction(
                            KeyboardButtonAction()
                                .setLabel(translation.data["label_admin"])
                                .setPayload(GSON.toJson(Payload().setCommand(Command.Admin.value)))
                                .setType(TemplateActionTypeNames.TEXT)
                        )
                        .setColor(KeyboardButtonColor.POSITIVE)
                )
            }
            row.add(
                KeyboardButton()
                    .setAction(
                        KeyboardButtonAction()
                            .setLabel(translation.data["label_fill_form"])
                            .setPayload(GSON.toJson(Payload().setCommand(Command.FillForm.value)))
                            .setType(TemplateActionTypeNames.TEXT)
                    )
                    .setColor(KeyboardButtonColor.PRIMARY)
            )
            buttons.add(row)

            return Keyboard().setButtons(buttons)
        }

        @JvmStatic
        private fun createSearchKeyboard(translation: Translation, data: String): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_like"])
                                        .setPayload(
                                            GSON.toJson(
                                                Payload()
                                                    .setCommand(Command.Like.value)
                                                    .setData(data)
                                            )
                                        )
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.POSITIVE),
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_next"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.Search.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                        ),
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_menu"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.Menu.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.POSITIVE),
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_fill_form"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.FillForm.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.PRIMARY)
                        )
                    )
                )
        }

        @JvmStatic
        private fun createLikedKeyboard(translation: Translation, data: String): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_view"])
                                        .setPayload(
                                            GSON.toJson(
                                                Payload()
                                                    .setCommand(Command.ViewLike.value)
                                                    .setData(data)
                                            )
                                        )
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.PRIMARY)
                        )
                    )
                )
                .setInline(true)
        }

        @JvmStatic
        private fun createRemoveKeyboard(translation: Translation, data: String): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_remove"])
                                        .setPayload(
                                            GSON.toJson(
                                                Payload()
                                                    .setCommand(Command.RemoveInterest.value)
                                                    .setData(data)
                                            )
                                        )
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.NEGATIVE)
                        )
                    )
                )
                .setInline(true)
        }

        @JvmStatic
        private fun createAdminKeyboard(user: User, translation: Translation): Keyboard {
            val buttons = arrayListOf<List<KeyboardButton>>()
            var row: ArrayList<KeyboardButton> = arrayListOf()
            if (user.level >= UserLevel.ADMIN) {
                row.add(
                    KeyboardButton()
                        .setAction(
                            KeyboardButtonAction()
                                .setLabel(translation.data["label_set_admin"])
                                .setPayload(GSON.toJson(Payload().setCommand(Command.SetAdmin.value)))
                                .setType(TemplateActionTypeNames.TEXT)
                        )
                )
                row.add(
                    KeyboardButton()
                        .setAction(
                            KeyboardButtonAction()
                                .setLabel(translation.data["label_remove_admin"])
                                .setPayload(GSON.toJson(Payload().setCommand(Command.RemoveAdmin.value)))
                                .setType(TemplateActionTypeNames.TEXT)
                        )
                )
                buttons.add(row)
                row = arrayListOf()
            }

            if (user.level >= UserLevel.OWNER) {
                row.add(
                    KeyboardButton()
                        .setAction(
                            KeyboardButtonAction()
                                .setLabel(translation.data["label_stop"])
                                .setPayload(GSON.toJson(Payload().setCommand(Command.Stop.value)))
                                .setType(TemplateActionTypeNames.TEXT)
                        )
                        .setColor(KeyboardButtonColor.NEGATIVE)
                )
            }
            row.add(
                KeyboardButton()
                    .setAction(
                        KeyboardButtonAction()
                            .setLabel(translation.data["label_menu"])
                            .setPayload(GSON.toJson(Payload().setCommand(Command.Menu.value)))
                            .setType(TemplateActionTypeNames.TEXT)
                    )
                    .setColor(KeyboardButtonColor.POSITIVE)
            )
            buttons.add(row)

            return Keyboard().setButtons(buttons)
        }

        @JvmStatic
        private fun createCancelKeyboard(translation: Translation, payload: Payload): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_menu"])
                                        .setPayload(GSON.toJson(payload))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.POSITIVE)
                        )
                    )
                )
        }
    }
}
