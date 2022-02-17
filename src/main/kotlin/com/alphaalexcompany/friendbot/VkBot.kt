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
    private val users = database.getCollection<User>()
    private val chats = database.getCollection<Chat>()
    private val translations = database.getCollection<Translation>()

    private fun work(user: User, translation: Translation, message: MessageNew, userId: Int, payload: String) {
        if (message.message.text.lowercase() == translation.data["message_stuck"]) {
            message.message.text = ""
            work(user, translation, message, userId, Command.Start.value)
        } else {
            when (payload) {
                Command.Start.value -> {
                    client.messages().send(actor)
                        .keyboard(createStartKeyboard(translation))
                        .message(translation.data["message_start"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.Menu.value -> {
                    if (user.age != 0) {
                        client.messages().send(actor)
                            .keyboard(createMenuKeyboard(user, translation))
                            .message(translation.data["message_menu"])
                            .peerId(userId)
                            .randomId(Random.nextInt())
                            .execute()
                    } else {
                        work(user, translation, message, userId, Command.SetAge.value)
                    }
                }
                Command.FillForm.value -> {
                    //TODO("Add something")
                    client.messages().send(actor)
                        .message(translation.data["message_fill_form"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.FormsUsers.value -> {
                    //TODO("Add something")
                    work(user, translation, message, userId, Command.FillForm.value)
                    /*user.command = InputCommand.SearchUser
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createCancelKeyboard(translation, Command.FormsUsersCancel.value))
                        .message(translation.data["message_forms_choose"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                    client.messages().send(actor)
                        .message("AMOGUS")
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()*/
                }
                Command.FormsChats.value -> {
                    user.command = InputCommand.SearchChat
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createCancelKeyboard(translation, Command.FormsChatsCancel.value))
                        .message(translation.data["message_forms_choose"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                    client.messages().send(actor)
                        .message(Theme.values().joinToString("\n") {
                            "%d. %s".format(it.ordinal + 1, translation.data["chat_type_" + it.value])
                        })
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.FormsChatsCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, userId, Command.Menu.value)
                }
                Command.Settings.value -> {
                    client.messages().send(actor)
                        .keyboard(createSettingsKeyboard(translation))
                        .message(translation.data["message_settings"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.SetAge.value -> {
                    user.command = InputCommand.SetAge
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(
                            if (user.age != 0) {
                                createCancelKeyboard(translation, Command.SetAgeCancel.value)
                            } else {
                                createEmptyKeyboard()
                            }
                        )
                        .message(translation.data["message_set_age"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.SetAgeCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, userId, Command.Settings.value)
                }
                Command.Subscription.value -> {
                    client.messages().send(actor)
                        .message(translation.data["message_subscription"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.Admin.value -> {
                    client.messages().send(actor)
                        .keyboard(createAdminKeyboard(user, translation))
                        .message(translation.data["message_admin"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.RemoveAdmin.value -> {
                    user.command = InputCommand.RemoveAdmin
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createCancelKeyboard(translation, Command.RemoveAdminCancel.value))
                        .message(translation.data["message_remove_admin"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.SetAdmin.value -> {
                    user.command = InputCommand.SetAdmin
                    users.updateOne(user)
                    client.messages().send(actor)
                        .keyboard(createCancelKeyboard(translation, Command.SetAdminCancel.value))
                        .message(translation.data["message_set_admin"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                }
                Command.RemoveAdminCancel.value, Command.SetAdminCancel.value -> {
                    user.command = null
                    users.updateOne(user)
                    work(user, translation, message, userId, Command.Admin.value)
                }
                Command.Stop.value -> {
                    client.messages().send(actor)
                        .keyboard(createMenuKeyboard(user, translation))
                        .message(translation.data["message_stop"])
                        .peerId(userId)
                        .randomId(Random.nextInt())
                        .execute()
                    exitProcess(0)
                }
                else -> {
                    when (user.command) {
                        InputCommand.RemoveAdmin -> {
                            try {
                                val adminId: Int = message.message.text.toInt()
                                val admin: User = users.findOne(User::id eq adminId)!!
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
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_remove_admin_forbidden"])
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                                user.command = null
                                users.updateOne(user)
                            } catch (_: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_remove_admin_failed"])
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SetAdmin -> {
                            try {
                                val adminId: Int = message.message.text.toInt()
                                val admin: User = users.findOne(User::id eq adminId)!!
                                if (admin.level < UserLevel.ADMIN && user.level > UserLevel.ADMIN) {
                                    admin.level = UserLevel.ADMIN
                                    users.updateOne(admin)
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_set_admin_success"])
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .keyboard(createAdminKeyboard(user, translation))
                                        .message(translation.data["message_set_admin_forbidden"])
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                                user.command = null
                                users.updateOne(user)
                            } catch (_: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_admin_failed"])
                                    .peerId(userId)
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
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                } else {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_age_incorrect"])
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } catch (e: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_age_failed"])
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        InputCommand.SearchChat -> {
                            try {
                                val index: Int = message.message.text.toInt() - 1
                                val themes = Theme.values()
                                if (index in 0..themes.size) {
                                    val chat = chats.find(Chat::theme eq themes[index]).toList().map { group ->
                                        client.messages().getConversationsById(actor, group.id)
                                            .execute().items.first()
                                    }.filter { chat -> chat.chatSettings.membersCount < 20 }.randomOrNull()
                                    if (chat != null) {
                                        client.messages().send(actor)
                                            .keyboard(createMenuKeyboard(user, translation))
                                            .message(translation.data["message_set_chat_success"])
                                            .peerId(userId)
                                            .randomId(Random.nextInt())
                                            .execute()
                                        client.messages().send(actor)
                                            .message(
                                                client.messages().getInviteLink(actor, chat.peer.id).execute().link
                                            )
                                            .peerId(userId)
                                            .randomId(Random.nextInt())
                                            .execute()
                                    } else {
                                        client.messages().send(actor)
                                            .keyboard(createMenuKeyboard(user, translation))
                                            .message(translation.data["message_set_chat_no_left"])
                                            .peerId(userId)
                                            .randomId(Random.nextInt())
                                            .execute()
                                    }
                                    user.command = null
                                    users.updateOne(user)
                                } else {
                                    client.messages().send(actor)
                                        .message(translation.data["message_set_chat_incorrect"])
                                        .peerId(userId)
                                        .randomId(Random.nextInt())
                                        .execute()
                                }
                            } catch (e: Exception) {
                                client.messages().send(actor)
                                    .message(translation.data["message_set_chat_failed"])
                                    .peerId(userId)
                                    .randomId(Random.nextInt())
                                    .execute()
                            }
                        }
                        else -> {
                            client.messages().send(actor)
                                .message(translation.data["message_unknown"])
                                .peerId(userId)
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

            users.findOne(User::id eq userId)?.let { botUser ->
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
            } ?: users.insertOne(User(userId))
        }
    }

    override fun messageDeny(groupId: Int?, message: MessageDeny?) {
        super.messageDeny(groupId, message)

        message?.userId?.let { userId ->
            val user = client.users().get(actor).userIds(userId.toString()).execute().first()
            LOG.info("{} {}(id{}) запретил доступ.", user.firstName, user.lastName, userId)

            users.updateOne(User::id eq userId, setValue(User::stopped, true))
        }
    }

    override fun messageNew(groupId: Int?, message: MessageNew?) {
        super.messageNew(groupId, message)

        message?.message?.peerId?.let { peerId ->
            if (peerId in 0..1999999999) {
                val user = client.users().get(actor).userIds(peerId.toString()).execute().first()
                LOG.info("{} {}(id{}) сказал: {}", user.firstName, user.lastName, peerId, message.message?.text)

                var isNewUser = false

                var botUser = users.findOne(User::id eq peerId)
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
                        peerId,
                        if (isNewUser) {
                            Command.Start.value
                        } else {
                            GSON.fromJson(
                                message.message.payload,
                                Payload::class.java
                            )?.command ?: ""
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
        private fun createStartKeyboard(translation: Translation): Keyboard {
            return Keyboard().setButtons(
                listOf(
                    listOf(
                        KeyboardButton()
                            .setAction(
                                KeyboardButtonAction()
                                    .setLabel(translation.data["label_next"])
                                    .setPayload(GSON.toJson(Payload().setCommand(Command.Menu.value)))
                                    .setType(TemplateActionTypeNames.TEXT)
                            )
                            .setColor(KeyboardButtonColor.POSITIVE)
                    )
                )
            )
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
                            .setLabel(translation.data["label_settings"])
                            .setPayload(GSON.toJson(Payload().setCommand(Command.Settings.value)))
                            .setType(TemplateActionTypeNames.TEXT)
                    )
                    .setColor(KeyboardButtonColor.PRIMARY)
            )
            buttons.add(row)

            return Keyboard().setButtons(buttons)
        }

        @JvmStatic
        private fun createSettingsKeyboard(translation: Translation): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_set_age"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.SetAge.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                ),
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_fill_form"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.FillForm.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                        ),
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_subscription"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.Subscription.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.PRIMARY)
                        ),
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_menu"])
                                        .setPayload(GSON.toJson(Payload().setCommand(Command.Menu.value)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.POSITIVE)
                        )
                    )
                )
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
        private fun createCancelKeyboard(translation: Translation, payload: String): Keyboard {
            return Keyboard()
                .setButtons(
                    listOf(
                        listOf(
                            KeyboardButton()
                                .setAction(
                                    KeyboardButtonAction()
                                        .setLabel(translation.data["label_cancel"])
                                        .setPayload(GSON.toJson(Payload().setCommand(payload)))
                                        .setType(TemplateActionTypeNames.TEXT)
                                )
                                .setColor(KeyboardButtonColor.NEGATIVE)
                        )
                    )
                )
        }
    }
}
