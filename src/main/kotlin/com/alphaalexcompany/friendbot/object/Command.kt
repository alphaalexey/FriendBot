package com.alphaalexcompany.friendbot.`object`

enum class Command(val value: String) {
    Admin("admin"),
    FillForm("fill_form"),
    FillFormCancel("fill_form_cancel"),
    FormsChats("forms_chats"),
    FormsChatsCancel("forms_chats_cancel"),
    FormsUsers("forms_users"),
    Like("like"),
    Menu("menu"),
    RemoveAdmin("remove_admin"),
    RemoveAdminCancel("remove_admin_cancel"),
    RemoveInterest("remove_interest"),
    Search("search"),
    SetAdmin("set_admin"),
    SetAdminCancel("set_admin_cancel"),
    SetAge("set_age"),
    Start("start"),
    Stop("stop"),
    ViewLike("view_like")
}
