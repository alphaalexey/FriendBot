package com.alphaalexcompany.friendbot.`object`

enum class Command(val value: String) {
    Admin("admin"),
    FillForm("fill_form"),
    FormsChats("forms_chats"),
    FormsChatsCancel("forms_chats_cancel"),
    FormsUsers("forms_users"),
    FormsUsersCancel("forms_users_cancel"),
    Menu("menu"),
    RemoveAdmin("remove_admin"),
    RemoveAdminCancel("remove_admin_cancel"),
    SetAdmin("set_admin"),
    SetAdminCancel("set_admin_cancel"),
    SetAge("set_age"),
    SetAgeCancel("set_age_cancel"),
    Settings("settings"),
    Start("start"),
    Stop("stop"),
    Subscription("subscription")
}
